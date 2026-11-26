package com.on.ebx.layout.explorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.Request;
import com.onwbp.adaptation.RequestResult;
import com.onwbp.adaptation.RequestSortCriteria;
import com.orchestranetworks.query.QueryBuilder;
import com.orchestranetworks.query.QueryResult;
import com.orchestranetworks.query.Tuple;
import com.orchestranetworks.service.LoggingCategory;

/**
 * The explorer's answers, all asked to EBX with the user's session (EBX applies the record permissions): counts and
 * pages through a {@link Request} with an XPath filter when every condition has one (EBX's indexed search), else
 * through EBX SQL ({@code Adaptation.createQueryBuilder}); facet counts per value when a list of values is short, by an
 * SQL {@code GROUP BY} otherwise. Nothing is kept but the tile counts (a minute, until a table read changes).
 */
public final class Engine
{
	/** Lists of values counted one value at a time up to this size; beyond, one GROUP BY. */
	private static final int SHORT_LIST = 30;
	/** Up to this many matching records, a long list of values is counted by reading the records (EBX's index). */
	private static final int READ_LIMIT = 5000;
	/** When set (an administrator's {@code explain}), every statement sent to EBX and its time, for this thread. */
	private static final ThreadLocal<List<String[]>> TRACE = new ThreadLocal<List<String[]>>();
	/** For this thread's call: the cache key prefix (user and tables' state), and whether SQL is preferred (admin experiment). */
	private static final ThreadLocal<String> CACHE_KEY = new ThreadLocal<String>();
	private static final ThreadLocal<Boolean> PREFER_SQL = new ThreadLocal<Boolean>();
	/**
	 * EBX's answers kept a minute, per user and per state of the tables read (any change in them gives new keys): the same
	 * counts come back at each click (a facet counted under the same other choices).
	 */
	private static final Map<String, Object[]> ANSWERS = new ConcurrentHashMap<String, Object[]>();
	private static final long ANSWERS_MS = 60_000L;
	private static final Map<String, java.util.concurrent.CompletableFuture<Object>> INFLIGHT = new ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<Object>>();
	private static final java.util.regex.Pattern ALIAS = java.util.regex.Pattern.compile("\\b([cmsux])\\d+\\b");

	/**
	 * One call's context, carried to the pool's threads: how long answers stay valid, the time of the oldest answer used
	 * (shown on the page), and whether a newer request of the same page made this one useless.
	 */
	static final class Call
	{
		long ttl = Engine.ANSWERS_MS;
		final java.util.concurrent.atomic.AtomicLong oldest = new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);
		java.util.function.BooleanSupplier cancelled = () -> false;
		/** On-demand mode: the data changed after the answers kept were computed. */
		boolean changed;
		String mode = "onDemand";
	}

	private static final ThreadLocal<Call> CALL = new ThreadLocal<Call>();

	/** A request dropped because the same page asked a newer one (the user kept typing). */
	public static final class Cancelled extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		Cancelled()
		{
			super("superseded by a newer request");
		}
	}

	private static void checkCancelled()
	{
		final Call c = Engine.CALL.get();
		if (c != null && c.cancelled.getAsBoolean())
		{
			throw new Cancelled();
		}
	}

	/**
	 * Per explorer, dataset and sharing scope, in on-demand mode: when the answers kept were started (their "epoch") and
	 * the state of the tables then. A refresh, or the maximum age, starts a new epoch.
	 */
	private static final Map<String, long[]> EPOCHS = new ConcurrentHashMap<String, long[]>();

	/** An administrator's experiment: ask EBX SQL even where an XPath request could answer. */
	public static void preferSql(final boolean pOn)
	{
		if (pOn)
		{
			Engine.PREFER_SQL.set(Boolean.TRUE);
		}
		else
		{
			Engine.PREFER_SQL.remove();
		}
	}

	/**
	 * Opens the answer cache of one call (the page's query, the cards). Automatic mode: answers valid a minute and until a
	 * table read changes. On-demand mode (the default): answers valid until someone refreshes or until the explorer's
	 * maximum age ({@code freshness.maxAgeMinutes}, 60), whatever changes in the data meanwhile; the page says when they
	 * were computed and whether the data changed since.
	 */
	private static void begin(final View pView, final boolean pRefresh, final java.util.function.BooleanSupplier pCancelled, final boolean pAuto)
	{
		final Call call = new Call();
		if (pCancelled != null)
		{
			call.cancelled = pCancelled;
		}
		final com.fasterxml.jackson.databind.JsonNode f = pView.spec.root.path("freshness");
		call.mode = pAuto || "auto".equals(f.path("mode").asText("")) ? "auto" : "onDemand";
		final String base = Engine.scope(pView);
		final long token = Engine.token(pView.model);
		String state;
		if ("auto".equals(call.mode))
		{
			state = "t" + token;
		}
		else
		{
			final long maxAge = Math.max(1, f.path("maxAgeMinutes").asInt(60)) * 60_000L;
			final String key = base + pView.spec.root.toString().hashCode();
			final long now = System.currentTimeMillis();
			long[] epoch = Engine.EPOCHS.get(key);
			if (epoch == null || pRefresh || now - epoch[0] >= maxAge)
			{
				epoch = new long[] { now, token };
				Engine.EPOCHS.put(key, epoch);
				if (Engine.EPOCHS.size() > 5000)
				{
					Engine.EPOCHS.entrySet().removeIf(e -> now - e.getValue()[0] >= maxAge);
				}
			}
			call.ttl = maxAge;
			call.changed = epoch[1] != token;
			state = "e" + epoch[0];
		}
		Engine.CALL.set(call);
		Engine.CACHE_KEY.set(base + state + "|" + Boolean.TRUE.equals(Engine.PREFER_SQL.get()) + "|");
	}

	/** This call's context (freshness, oldest answer), read by the page before {@link #end}. */
	static Call call()
	{
		return Engine.CALL.get();
	}

	/** Per user and table state: does this user see every record of every table the explorer reads? */
	private static final Map<String, Object[]> SEES_ALL = new ConcurrentHashMap<String, Object[]>();

	/**
	 * Whose answers a user may share. A user who sees every record of every table the explorer reads (EBX counts them
	 * with the user's session and without, and finds the same number) gets the same answer as any other such user for the
	 * same statement: they share one cache, so a hundred users opening the page cost one computation per minute. Anyone
	 * else keeps answers of their own. The key also holds the dataset and the state of the tables read.
	 */
	static String scope(final View pView)
	{
		final Model m = pView.model;
		final long token = Engine.token(m);
		final String where = m.dataset.getHome().getKey().getName() + "|" + m.dataset.getAdaptationName().getStringName() + "|" + m.table.getTablePath().format() + "|";
		final String user = pView.session.getUserReference().getUserId();
		// who sees everything is asked again a minute later or as soon as the data changes: rights are never kept longer
		final String key = user + "|" + where + token;
		final Object[] known = Engine.SEES_ALL.get(key);
		final long now = System.currentTimeMillis();
		boolean all;
		if (known != null && now - ((Long) known[1]).longValue() < Engine.ANSWERS_MS)
		{
			all = ((Boolean) known[0]).booleanValue();
		}
		else
		{
			all = true;
			for (final AdaptationTable t : IndexBuilder.readTables(m.dataset, m.table, m.root))
			{
				all &= Engine.size(t, pView.session) == Engine.size(t, null);
				if (!all)
				{
					break;
				}
			}
			Engine.SEES_ALL.put(key, new Object[] { Boolean.valueOf(all), Long.valueOf(now) });
			if (Engine.SEES_ALL.size() > 5000)
			{
				Engine.SEES_ALL.entrySet().removeIf(e -> now - ((Long) e.getValue()[1]).longValue() > Engine.ANSWERS_MS);
			}
			Engine.traced("sees all records", user + ": " + all, now);
		}
		return (all ? "*all*" : user) + "|" + where;
	}

	/** Records of a table, as a user sees them (null: all of them). */
	private static int size(final AdaptationTable pTable, final com.orchestranetworks.service.Session pSession)
	{
		final Request r = pTable.createRequest();
		if (pSession != null)
		{
			r.setSession(pSession);
		}
		final RequestResult rr = r.execute();
		try
		{
			return rr.getSize();
		}
		finally
		{
			rr.close();
		}
	}

	private static void end()
	{
		Engine.CACHE_KEY.remove();
		Engine.CALL.remove();
	}

	@SuppressWarnings("unchecked")
	private static <T> T cached(final String pKind, final String pStatement, final java.util.function.Supplier<T> pCompute)
	{
		final String prefix = Engine.CACHE_KEY.get();
		if (prefix == null || Boolean.TRUE.equals(Engine.NO_CACHE.get()))
		{
			return pCompute.get();
		}
		// two compilations of the same condition differ only by their sub-query aliases (m10, c3…): one key for both
		final String key = prefix + pKind + "|" + Engine.ALIAS.matcher(pStatement).replaceAll("$1");
		final Object[] known = Engine.ANSWERS.get(key);
		final long now = System.currentTimeMillis();
		final Call call = Engine.CALL.get();
		final long ttl = call == null ? Engine.ANSWERS_MS : call.ttl;
		if (known != null && now - ((Long) known[1]).longValue() < ttl)
		{
			Engine.traced(pKind + " (kept)", pStatement, now);
			if (call != null)
			{
				call.oldest.accumulateAndGet(((Long) known[1]).longValue(), Math::min);
			}
			return (T) known[0];
		}
		Engine.checkCancelled();
		if (call != null)
		{
			call.oldest.accumulateAndGet(now, Math::min);
		}
		// one computation per question: users asking it at the same moment wait for the first one's answer
		final java.util.concurrent.CompletableFuture<Object> mine = new java.util.concurrent.CompletableFuture<Object>();
		final java.util.concurrent.CompletableFuture<Object> running = Engine.INFLIGHT.putIfAbsent(key, mine);
		if (running != null)
		{
			final T v = (T) running.join();
			Engine.traced(pKind + " (waited)", pStatement, now);
			return v;
		}
		final T v;
		try
		{
			v = pCompute.get();
			Engine.ANSWERS.put(key, new Object[] { v, Long.valueOf(now) });
			mine.complete(v);
		}
		catch (final RuntimeException ex)
		{
			mine.completeExceptionally(ex);
			throw ex;
		}
		finally
		{
			Engine.INFLIGHT.remove(key, mine);
		}
		if (Engine.ANSWERS.size() > 20_000)
		{
			Engine.ANSWERS.entrySet().removeIf(e -> now - ((Long) e.getValue()[1]).longValue() > 60 * 60_000L);
			if (Engine.ANSWERS.size() > 20_000)
			{
				Engine.ANSWERS.clear();
			}
		}
		return v;
	}

	private Engine()
	{
	}

	/**
	 * The explorer's own pool: the statements of one page are independent (counts, the page of keys, each facet), so they
	 * are sent to EBX at the same time. A work-stealing pool, so a facet that waits for its own counts helps run them; its
	 * size is the ceiling for the whole server. The user's session is shared by the threads, as Insight's profiling does
	 * ({@code ProfilingExecution.executeIndicatorUsingIndex}); not Java's common pool, whose threads do not see the
	 * webapp's classes.
	 */
	private static final java.util.concurrent.ForkJoinPool POOL = new java.util.concurrent.ForkJoinPool(Math.max(2, Math.min(8,
		Runtime.getRuntime().availableProcessors())), pool -> {
			final java.util.concurrent.ForkJoinWorkerThread t = java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
			t.setName("layout-designer-explorer-" + t.getPoolIndex());
			t.setDaemon(true);
			t.setContextClassLoader(Engine.class.getClassLoader());
			return t;
		}, null, false);

	/** Runs the tasks at the same time on the explorer's pool (the caller's context carried over), in order of the list. */
	static <T> List<T> all(final List<java.util.concurrent.Callable<T>> pTasks)
	{
		final List<T> out = new ArrayList<T>();
		if (pTasks.size() <= 1 || Boolean.TRUE.equals(Engine.SEQUENTIAL.get()))
		{
			for (final java.util.concurrent.Callable<T> c : pTasks)
			{
				out.add(Engine.call(c));
			}
			return out;
		}
		final String key = Engine.CACHE_KEY.get();
		final List<String[]> trace = Engine.TRACE.get();
		final Boolean sql = Engine.PREFER_SQL.get();
		final Boolean noCache = Engine.NO_CACHE.get();
		final Call call = Engine.CALL.get();
		final List<java.util.concurrent.ForkJoinTask<T>> forks = new ArrayList<java.util.concurrent.ForkJoinTask<T>>();
		for (final java.util.concurrent.Callable<T> c : pTasks)
		{
			forks.add(java.util.concurrent.ForkJoinTask.adapt(() -> {
				final String k0 = Engine.CACHE_KEY.get();
				final List<String[]> t0 = Engine.TRACE.get();
				final Boolean s0 = Engine.PREFER_SQL.get();
				final Boolean n0 = Engine.NO_CACHE.get();
				final Call c0 = Engine.CALL.get();
				Engine.set(Engine.CALL, call);
				Engine.set(Engine.NO_CACHE, noCache);
				Engine.set(Engine.CACHE_KEY, key);
				Engine.set(Engine.TRACE, trace);
				Engine.set(Engine.PREFER_SQL, sql);
				try
				{
					return c.call();
				}
				finally
				{
					Engine.set(Engine.CACHE_KEY, k0);
					Engine.set(Engine.TRACE, t0);
					Engine.set(Engine.PREFER_SQL, s0);
					Engine.set(Engine.NO_CACHE, n0);
					Engine.set(Engine.CALL, c0);
				}
			}));
		}
		if (Thread.currentThread() instanceof java.util.concurrent.ForkJoinWorkerThread
			&& ((java.util.concurrent.ForkJoinWorkerThread) Thread.currentThread()).getPool() == Engine.POOL)
		{
			// inside a task of the pool: fork and join, the waiting thread runs queued tasks meanwhile
			java.util.concurrent.ForkJoinTask.invokeAll(forks);
		}
		else
		{
			for (final java.util.concurrent.ForkJoinTask<T> f : forks)
			{
				Engine.POOL.execute(f);
			}
		}
		for (final java.util.concurrent.ForkJoinTask<T> f : forks)
		{
			try
			{
				out.add(f.join());
			}
			catch (final RuntimeException ex)
			{
				throw ex;
			}
		}
		return out;
	}

	private static <T> T call(final java.util.concurrent.Callable<T> pTask)
	{
		try
		{
			return pTask.call();
		}
		catch (final RuntimeException ex)
		{
			throw ex;
		}
		catch (final Exception ex)
		{
			throw new IllegalStateException(ex.getMessage(), ex);
		}
	}

	private static <T> void set(final ThreadLocal<T> pLocal, final T pValue)
	{
		if (pValue == null)
		{
			pLocal.remove();
		}
		else
		{
			pLocal.set(pValue);
		}
	}

	/** A load test: every simulated user does the whole work (nothing answered from the minute's cache). */
	private static final ThreadLocal<Boolean> NO_CACHE = new ThreadLocal<Boolean>();

	public static void noCache(final boolean pOn)
	{
		Engine.set(Engine.NO_CACHE, pOn ? Boolean.TRUE : null);
	}

	/** An administrator's comparison: the same page with its statements one after another ({@code parallel=0}). */
	private static final ThreadLocal<Boolean> SEQUENTIAL = new ThreadLocal<Boolean>();

	public static void sequential(final boolean pOn)
	{
		Engine.set(Engine.SEQUENTIAL, pOn ? Boolean.TRUE : null);
	}

	/** Records the statements of the calls that follow on this thread; {@link #trace()} returns them and stops. */
	public static void startTrace()
	{
		Engine.TRACE.set(java.util.Collections.synchronizedList(new ArrayList<String[]>()));
	}

	public static List<String[]> trace()
	{
		final List<String[]> t = Engine.TRACE.get();
		Engine.TRACE.remove();
		return t == null ? new ArrayList<String[]>() : t;
	}

	private static void traced(final String pKind, final String pText, final long pStart)
	{
		final List<String[]> t = Engine.TRACE.get();
		if (t != null)
		{
			t.add(new String[] { pKind, pText, String.valueOf(System.currentTimeMillis() - pStart) });
		}
	}

	static void forget(final int pExplorer)
	{
		// a saved explorer has a new definition, hence new keys: nothing kept is reused for it
	}

	// ----- the page ---------------------------------------------------------------------------------------------------

	static Query.Result run(final View pView, final Query.Request pRequest, final int pOffset, final int pLimit)
	{
		Engine.begin(pView, pRequest.refresh, pRequest.cancelled, false);
		try
		{
			final Query.Result out = Engine.runKept(pView, pRequest, pOffset, pLimit);
			final Call call = Engine.CALL.get();
			final long oldest = call.oldest.get();
			out.computedAt = oldest == Long.MAX_VALUE ? System.currentTimeMillis() : oldest;
			out.changed = call.changed;
			out.mode = call.mode;
			return out;
		}
		finally
		{
			Engine.end();
		}
	}

	private static Query.Result runKept(final View pView, final Query.Request pRequest, final int pOffset, final int pLimit)
	{
		final long start = System.currentTimeMillis();
		final Model m = pView.model;
		final Query.Result out = new Query.Result();
		final String q = pRequest.text == null ? "" : pRequest.text.trim();
		final Model.Pred text = q.isEmpty() ? Model.Pred.TRUE : m.text(pView.search, q);
		final Spec.Tile tile = Engine.tile(pView, pRequest.tile);
		final Model.Pred tileP = tile == null ? Model.Pred.TRUE : Engine.tilePred(pView, tile);
		final int nf = pView.facets.size();
		final Model.Pred[] selected = new Model.Pred[nf];
		for (int f = 0; f < nf; f++)
		{
			final Spec.Facet facet = pView.facets.get(f);
			final Set<String> w = pRequest.selected.get(facet.id);
			selected[f] = w == null || w.isEmpty() ? Model.Pred.TRUE : Engine.selection(pView, facet, w, pRequest.favorites);
		}
		final List<Model.Pred> all = new ArrayList<Model.Pred>();
		all.add(text);
		all.add(tileP);
		Collections.addAll(all, selected);
		final Model.Pred where = Model.and(all);

		final boolean main = !"facets".equals(pRequest.part), withFacets = !"main".equals(pRequest.part);
		final Spec.Sort sort = Engine.direction(pView.sorts.isEmpty() ? null : pView.sorts.get(Math.max(0, Math.min(pView.sorts.size() - 1, Engine.sortIndex(pView, pRequest.sort)))),
			pRequest.dir);
		final Map<String, String> groupOf = new java.util.concurrent.ConcurrentHashMap<String, String>();
		final List<java.util.concurrent.Callable<Object>> tasks = new ArrayList<java.util.concurrent.Callable<Object>>();
		// the page first (total, records, tiles), the facets in the same call or in a second one ("two steps")
		tasks.add(() -> main ? Integer.valueOf(Engine.count(pView, where)) : null);
		tasks.add(() -> main ? Engine.page(pView, where, Engine.group(pView), sort, pOffset, pLimit, groupOf) : null);
		tasks.add(() -> main ? Integer.valueOf(Engine.count(pView, Model.Pred.TRUE)) : null);
		for (final Spec.Tile t : pView.tiles)
		{
			tasks.add(() -> main ? Integer.valueOf(Engine.count(pView, Engine.tilePred(pView, t))) : null);
		}
		final int first = tasks.size();
		for (int f = 0; f < nf; f++)
		{
			final Spec.Facet facet = pView.facets.get(f);
			if (!withFacets || pRequest.closed.contains(facet.id))
			{
				tasks.add(() -> null);
				continue;
			}
			final List<Model.Pred> others = new ArrayList<Model.Pred>();
			others.add(text);
			others.add(tileP);
			for (int g = 0; g < nf; g++)
			{
				if (g != f)
				{
					others.add(selected[g]);
				}
			}
			final Set<String> w = pRequest.selected.get(facet.id);
			final Integer shown = pRequest.shown.get(facet.id);
			tasks.add(() -> Engine.facet(pView, facet, Model.and(others), w == null ? new TreeSet<String>() : w,
				Math.max(1, Math.min(Query.MAX_VALUES, shown == null ? facet.top : shown.intValue())), pRequest.facetSearch.get(facet.id), pRequest.favorites));
		}
		final List<Object> done = Engine.all(tasks);
		if (main)
		{
			out.total = ((Integer) done.get(0)).intValue();
			@SuppressWarnings("unchecked")
			final List<String> keys = (List<String>) done.get(1);
			out.keys.addAll(keys);
			out.all = ((Integer) done.get(2)).intValue();
			for (int i = 0; i < pView.tiles.size(); i++)
			{
				out.tiles.put(pView.tiles.get(i).id, (Integer) done.get(3 + i));
			}
			if (Engine.group(pView) != null)
			{
				Engine.groups(pView, where, keys, groupOf, out);
			}
		}
		if (withFacets)
		{
			for (int f = 0; f < nf; f++)
			{
				Query.FacetPage page = (Query.FacetPage) done.get(first + f);
				if (page == null)
				{
					page = new Query.FacetPage();
					page.lazy = true;
				}
				out.facets.put(pView.facets.get(f).id, page);
			}
		}
		LoggingCategory.getKernel().debug("[layout-designer] explorer query on " + m.table.getTablePath().format() + ": " + out.total + "/" + out.all + " records, "
			+ (System.currentTimeMillis() - start) + " ms");
		return out;
	}

	/** The page asks for a sort by its index in the explorer's list of sorts; the user's list may have fewer. */
	private static int sortIndex(final View pView, final int pAsked)
	{
		if (pAsked < 0 || pAsked >= pView.spec.sorts.size())
		{
			return 0;
		}
		final int i = pView.sorts.indexOf(pView.spec.sorts.get(pAsked));
		return i < 0 ? 0 : i;
	}

	static Spec.Tile tile(final View pView, final String pId)
	{
		if (pId == null || pId.isEmpty())
		{
			return null;
		}
		for (final Spec.Tile t : pView.tiles)
		{
			if (t.id.equals(pId))
			{
				return t;
			}
		}
		return null;
	}

	/** The records a facet's selected values keep. */
	static Model.Pred selection(final View pView, final Spec.Facet pFacet, final Set<String> pValues, final Set<String> pFavorites)
	{
		final Model m = pView.model;
		final List<Model.Pred> any = new ArrayList<Model.Pred>();
		switch (pFacet.kind)
		{
		case "favorites":
			return pValues.contains("1") ? m.keys(pFavorites) : Model.Pred.TRUE;
		case "flags":
			for (final Spec.Flag fl : pFacet.flags)
			{
				if (pValues.contains(fl.id))
				{
					any.add(m.pred(fl.when));
				}
			}
			break;
		case "ranges":
			for (final Spec.Range r : pFacet.ranges)
			{
				if (pValues.contains(r.code))
				{
					any.add(m.range(m.src(pFacet.source), r.min, r.max));
				}
			}
			break;
		case "tree":
		{
			// a node keeps its descendants
			final Model.Tree tree = m.tree(pFacet.source, pFacet.parent);
			final Set<String> keys = new LinkedHashSet<String>(pValues);
			boolean grew = true;
			while (grew)
			{
				grew = false;
				for (final Map.Entry<String, String> e : tree.parent.entrySet())
				{
					if (keys.contains(e.getValue()) && keys.add(e.getKey()))
					{
						grew = true;
					}
				}
			}
			any.add(m.in(m.src(pFacet.source), keys));
			break;
		}
		default:
			any.add(m.in(m.src(pFacet.source), pValues));
			break;
		}
		any.removeIf(p -> p == null);
		return any.isEmpty() ? Model.Pred.FALSE : Model.or(any);
	}

	static Model.Pred tilePred(final View pView, final Spec.Tile pTile)
	{
		final Model m = pView.model;
		if (pTile.flag != null)
		{
			for (final Spec.Facet f : pView.facets)
			{
				for (final Spec.Flag fl : f.flags)
				{
					if (pTile.flag.equals(fl.id))
					{
						final Model.Pred p = m.pred(fl.when);
						return p == null ? Model.Pred.FALSE : p;
					}
				}
			}
			return Model.Pred.FALSE;
		}
		if (!pTile.set.isEmpty())
		{
			final List<Model.Pred> all = new ArrayList<Model.Pred>();
			for (final Map.Entry<String, Set<String>> e : pTile.set.entrySet())
			{
				final Spec.Facet f = pView.facet(e.getKey());
				if (f == null)
				{
					return Model.Pred.FALSE;
				}
				all.add(Engine.selection(pView, f, e.getValue(), Collections.<String> emptySet()));
			}
			return Model.and(all);
		}
		final Model.Pred p = m.pred(pTile.when);
		return p == null ? Model.Pred.FALSE : p;
	}

	/** Changes when a table the explorer reads changes. */
	private static long token(final Model pModel)
	{
		long token = 17;
		for (final AdaptationTable t : IndexBuilder.readTables(pModel.dataset, pModel.table, pModel.root))
		{
			final Date d = t.getLastModificationDate();
			token = token * 31 + (d == null ? 0 : d.getTime());
		}
		return token;
	}

	// ----- EBX calls --------------------------------------------------------------------------------------------------

	/** How many records hold: EBX's request with an XPath filter when there is one, else an SQL count. */
	static int count(final View pView, final Model.Pred pPred)
	{
		if (pPred == null || pPred == Model.Pred.FALSE)
		{
			return 0;
		}
		return Engine.cached("count", pPred.xpath + "|" + pPred.sql, () -> Integer.valueOf(Engine.countNow(pView, pPred))).intValue();
	}

	private static int countNow(final View pView, final Model.Pred pPred)
	{
		final Model m = pView.model;
		final boolean sqlFirst = Boolean.TRUE.equals(Engine.PREFER_SQL.get()) && pPred.sql != null;
		if (pPred.xpath != null && !sqlFirst)
		{
			final long start = System.currentTimeMillis();
			final Request r = m.table.createRequest();
			if (!pPred.xpath.isEmpty())
			{
				r.setXPathFilter(pPred.xpath);
			}
			r.setSession(pView.session);
			final RequestResult rr = r.execute();
			try
			{
				return rr.getSize();
			}
			finally
			{
				rr.close();
				Engine.traced("xpath count", pPred.xpath, start);
			}
		}
		if (pPred.sql == null)
		{
			return 0;
		}
		final List<Object[]> rows = Engine.select(pView, "SELECT COUNT(*) AS n FROM " + m.from + " t WHERE " + pPred.sql, 1);
		return rows.isEmpty() ? 0 : Engine.integer(rows.get(0)[0]);
	}

	/** Keys of the records {@code pOffset}..{@code pOffset + pLimit} in the sort's order (then the primary key). */
	static List<String> page(final View pView, final Model.Pred pWhere, final Spec.Sort pSort, final int pOffset, final int pLimit)
	{
		return Engine.page(pView, pWhere, null, pSort, pOffset, pLimit, null);
	}

	/** The explorer's groups, unless the user may not read the source they group by. */
	static Spec.Sort group(final View pView)
	{
		final Spec.Sort g = pView.spec.group;
		return g == null || pView.hidden.contains(g.source) ? null : g;
	}

	/** The sort in the direction the user chose ("asc", "desc"), or as the explorer defines it. */
	private static Spec.Sort direction(final Spec.Sort pSort, final String pDir)
	{
		if (pSort == null || pDir == null || !("asc".equals(pDir) || "desc".equals(pDir)) || pSort.desc == "desc".equals(pDir))
		{
			return pSort;
		}
		final Spec.Sort out = new Spec.Sort();
		out.source = pSort.source;
		out.label = pSort.label;
		out.desc = "desc".equals(pDir);
		return out;
	}

	/** The groups the page's records fall in, in the page's order, each with its count of matching records (one count each). */
	private static void groups(final View pView, final Model.Pred pWhere, final List<String> pKeys, final Map<String, String> pGroupOf, final Query.Result pOut)
	{
		final Model m = pView.model;
		final Model.Src g = m.src(Engine.group(pView).source);
		final List<String> values = new ArrayList<String>();
		for (final String k : pKeys)
		{
			final String v = pGroupOf.get(k);
			if (v != null && !values.contains(v))
			{
				values.add(v);
			}
		}
		final List<java.util.concurrent.Callable<Object>> tasks = new ArrayList<java.util.concurrent.Callable<Object>>();
		for (final String v : values)
		{
			tasks.add(() -> {
				final Model.Pred one = v.isEmpty() ? m.leaf(g, "empty", null) : m.in(g, Collections.singletonList(v));
				final List<Model.Pred> both = new ArrayList<Model.Pred>();
				both.add(pWhere);
				both.add(one == null ? Model.Pred.FALSE : one);
				return Integer.valueOf(Engine.count(pView, Model.and(both)));
			});
		}
		final List<Object> counts = Engine.all(tasks);
		for (int i = 0; i < values.size(); i++)
		{
			final String v = values.get(i);
			pOut.groups.add(new Query.Item(v, v.isEmpty() ? "" : m.label(g, v), ((Integer) counts.get(i)).intValue(), 0, false));
		}
		pOut.groupOf.putAll(pGroupOf);
	}

	/**
	 * One page of record keys, sorted by the group's source first (when there is one), then by the sort. Each record's
	 * group value goes into {@code pGroupOf} (key → value, "" when empty).
	 */
	static List<String> page(final View pView, final Model.Pred pWhere, final Spec.Sort pGroup, final Spec.Sort pSort, final int pOffset, final int pLimit,
		final Map<String, String> pGroupOf)
	{
		final List<String> out = new ArrayList<String>();
		if (pWhere == Model.Pred.FALSE)
		{
			return out;
		}
		final Model m = pView.model;
		final Model.Src s = pSort == null ? null : m.src(pSort.source);
		final Model.Src g = pGroup == null ? null : m.src(pGroup.source);
		final boolean onField = (s == null || s.node != null && !s.node.isLinkedNode() && !"list".equals(s.type))
			&& (g == null || g.node != null && !g.node.isLinkedNode() && !"list".equals(g.type));
		final boolean sqlFirst = Boolean.TRUE.equals(Engine.PREFER_SQL.get()) && pWhere.sql != null;
		if (pWhere.xpath != null && onField && !sqlFirst || pWhere.sql == null)
		{
			final long start = System.currentTimeMillis();
			final Request r = m.table.createRequest();
			if (pWhere.xpath != null && !pWhere.xpath.isEmpty())
			{
				r.setXPathFilter(pWhere.xpath);
			}
			r.setSession(pView.session);
			if ((s != null || g != null) && onField)
			{
				final RequestSortCriteria criteria = new RequestSortCriteria();
				if (g != null)
				{
					criteria.add(g.node.getPathInAdaptation(), !pGroup.desc);
				}
				if (s != null)
				{
					criteria.add(s.node.getPathInAdaptation(), !pSort.desc);
				}
				r.setSortCriteria(criteria);
			}
			final RequestResult rr = r.execute();
			try
			{
				int i = 0;
				for (Adaptation a; (a = rr.nextAdaptation()) != null && out.size() < pLimit; i++)
				{
					if (i >= pOffset)
					{
						final String k = a.getOccurrencePrimaryKey().format();
						out.add(k);
						if (g != null && pGroupOf != null)
						{
							final String v = Cond.key(a.get(g.node));
							pGroupOf.put(k, v == null ? "" : v);
						}
					}
				}
			}
			finally
			{
				rr.close();
				Engine.traced("xpath page", pWhere.xpath + (s != null && onField ? " sorted by " + s.node.getPathInAdaptation().format() : ""), start);
			}
			return out;
		}
		final boolean grouped = g != null && g.sql != null;
		final String order = (grouped ? g.sql + (pGroup.desc ? " DESC" : " ASC") + " NULLS LAST, " : "")
			+ (s == null || s.sql == null ? "" : s.sql + (pSort.desc ? " DESC" : " ASC") + " NULLS LAST, ");
		for (final Object[] row : Engine.select(pView, "SELECT t.\"$pk\" AS k" + (grouped ? ", " + g.sql + " AS g" : "") + " FROM " + m.from + " t WHERE " + pWhere.sql
			+ " ORDER BY " + order + "t.\"$pk\" OFFSET " + Math.max(0, pOffset) + " ROWS FETCH NEXT " + Math.max(1, pLimit) + " ROWS ONLY", pLimit))
		{
			final String k = String.valueOf(row[0]);
			out.add(k);
			if (grouped && pGroupOf != null)
			{
				final String v = Cond.key(row[1]);
				pGroupOf.put(k, v == null ? "" : v);
			}
		}
		return out;
	}

	/** An EBX SQL statement run with the user's session; the metadataset is known as {@code mds}. */
	static List<Object[]> select(final View pView, final String pSql, final int pMax)
	{
		return Engine.cached("sql", pMax + "|" + pSql, () -> Engine.selectNow(pView, pSql, pMax));
	}

	private static List<Object[]> selectNow(final View pView, final String pSql, final int pMax)
	{
		final Model m = pView.model;
		final List<Object[]> out = new ArrayList<Object[]>();
		final long start = System.currentTimeMillis();
		try
		{
			final QueryBuilder b = m.dataset.createQueryBuilder();
			if (m.mds != null && pSql.contains("mds."))
			{
				b.addDataset("mds", m.mds);
			}
			final com.orchestranetworks.query.Query<Tuple> query = b.build(pSql);
			query.setSession(pView.session);
			query.setLocale(m.locale);
			final QueryResult<Tuple> result = query.getResult();
			try
			{
				for (final Tuple t : result)
				{
					out.add(t.toArray());
					if (out.size() >= pMax)
					{
						break;
					}
				}
			}
			finally
			{
				result.close();
				Engine.traced("sql", pSql, start);
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] explorer SQL refused by EBX: " + ex.getMessage() + " -- " + pSql);
			throw ex;
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] explorer SQL refused by EBX: " + ex.getMessage() + " -- " + pSql);
			throw new IllegalStateException(ex.getMessage(), ex);
		}
		return out;
	}

	private static int integer(final Object pValue)
	{
		return pValue instanceof Number ? ((Number) pValue).intValue() : 0;
	}

	// ----- facets -----------------------------------------------------------------------------------------------------

	private static Query.FacetPage facet(final View pView, final Spec.Facet pFacet, final Model.Pred pOthers, final Set<String> pWanted, final int pShown,
		final String pSearch, final Set<String> pFavorites)
	{
		final Model m = pView.model;
		switch (pFacet.kind)
		{
		case "favorites":
		{
			final Query.FacetPage out = new Query.FacetPage();
			out.total = 1;
			final int n = pFavorites.isEmpty() ? 0 : Engine.count(pView, Model.and(java.util.Arrays.asList(pOthers, m.keys(pFavorites))));
			out.items.add(new Query.Item("1", "", n, 0, pWanted.contains("1")));
			return out;
		}
		case "flags":
		case "ranges":
		{
			final List<String> codes = new ArrayList<String>();
			final List<String> labels = new ArrayList<String>();
			final List<Model.Pred> preds = new ArrayList<Model.Pred>();
			for (final Spec.Flag fl : pFacet.flags)
			{
				codes.add(fl.id);
				labels.add(Query.text(fl.label, m.locale));
				preds.add(m.pred(fl.when));
			}
			for (final Spec.Range r : pFacet.ranges)
			{
				codes.add(r.code);
				labels.add(Query.text(r.label, m.locale));
				preds.add(m.range(m.src(pFacet.source), r.min, r.max));
			}
			final int[] n = Engine.counts(pView, pOthers, preds);
			final Query.FacetPage out = new Query.FacetPage();
			out.total = codes.size();
			for (int i = 0; i < codes.size(); i++)
			{
				out.items.add(new Query.Item(codes.get(i), labels.get(i), n[i], 0, pWanted.contains(codes.get(i))));
			}
			return out;
		}
		default:
			return Engine.valuesFacet(pView, pFacet, pOthers, pWanted, pShown, pSearch);
		}
	}

	/**
	 * One count per condition, all on top of {@code pOthers}: one EBX count each (XPath when it can, else SQL). One
	 * statement summing CASEs was tried: with sub-queries inside the CASEs EBX's planner builds a plan that runs for minutes.
	 */
	private static int[] counts(final View pView, final Model.Pred pOthers, final List<Model.Pred> pPreds)
	{
		final List<java.util.concurrent.Callable<Integer>> tasks = new ArrayList<java.util.concurrent.Callable<Integer>>();
		for (final Model.Pred p : pPreds)
		{
			tasks.add(() -> Integer.valueOf(p == null ? 0 : Engine.count(pView, Model.and(java.util.Arrays.asList(pOthers, p)))));
		}
		final List<Integer> n = Engine.all(tasks);
		final int[] out = new int[n.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = n.get(i).intValue();
		}
		return out;
	}

	/** A field's (or a computed value's) facet: counts per value, the selected values first, then the most frequent. */
	private static Query.FacetPage valuesFacet(final View pView, final Spec.Facet pFacet, final Model.Pred pOthers, final Set<String> pWanted, final int pShown,
		final String pSearch)
	{
		final Model m = pView.model;
		final Model.Src s = m.src(pFacet.source);
		final boolean tree = "tree".equals(pFacet.kind);
		final String q = pSearch == null || pSearch.trim().isEmpty() ? null : pSearch.trim();
		final Map<String, String> listed = Engine.shortList(pView, s);
		final Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
		int total = -1;
		boolean more = false;
		if (tree)
		{
			// every value of the target table, then each node also counts its descendants' records
			final Map<String, Integer> own = Engine.grouped(pView, s, pOthers, null, null, Integer.MAX_VALUE, new int[1]);
			final Model.Tree t = m.tree(pFacet.source, pFacet.parent);
			for (final Map.Entry<String, Integer> e : own.entrySet())
			{
				final Set<String> chain = new LinkedHashSet<String>();
				for (String c = e.getKey(); c != null && chain.size() < 12 && chain.add(c); c = t.parent.get(c))
				{
					counts.merge(c, e.getValue(), Integer::sum);
				}
			}
		}
		else if (listed != null)
		{
			final List<String> keys = new ArrayList<String>();
			final List<Model.Pred> preds = new ArrayList<Model.Pred>();
			for (final Map.Entry<String, String> e : listed.entrySet())
			{
				if (q == null || Model.norm(e.getValue()).contains(Model.norm(q)) || Model.norm(e.getKey()).contains(Model.norm(q)))
				{
					keys.add(e.getKey());
					preds.add(m.in(s, Collections.singleton(e.getKey())));
				}
			}
			// one XPath count per value: measured faster than one SQL GROUP BY under the same filter (route=sql, 2026-10-15)
			final int[] n = Engine.counts(pView, pOthers, preds);
			for (int i = 0; i < keys.size(); i++)
			{
				counts.put(keys.get(i), Integer.valueOf(n[i]));
			}
		}
		else if (s != null && s.node != null && pOthers.xpath != null && !pOthers.xpath.isEmpty() && Engine.count(pView, pOthers) <= Engine.READ_LIMIT)
		{
			// few records match: read them through EBX's index and count their values, rather than a SQL scan of the table
			final Map<String, Integer> all = Engine.readValues(pView, s, pOthers);
			final List<String> keys = new ArrayList<String>(all.keySet());
			keys.removeIf(k -> q != null && !Model.norm(k).contains(Model.norm(q)) && !Model.norm(m.label(s, k)).contains(Model.norm(q)));
			keys.sort((a, b) -> Integer.compare(all.get(b).intValue(), all.get(a).intValue()));
			total = keys.size();
			for (final String k : keys.subList(0, Math.min(keys.size(), pShown + 1)))
			{
				counts.put(k, all.get(k));
			}
			for (final String k : pWanted)
			{
				counts.put(k, all.getOrDefault(k, Integer.valueOf(0)));
			}
		}
		else
		{
			// the exact number of distinct values only without filter (asked once, then kept); under a filter, "more than N"
			final int[] distinct = new int[] { 0, pOthers.isTrue() ? 1 : 0 };
			counts.putAll(Engine.grouped(pView, s, pOthers, q, null, pShown + 1, distinct));
			total = distinct[0];
			more = total < 0;
			if (more)
			{
				total = counts.size();
			}
			// selected values outside the first ones keep their counts
			final Set<String> missing = new TreeSet<String>(pWanted);
			missing.removeAll(counts.keySet());
			if (!missing.isEmpty())
			{
				counts.putAll(Engine.grouped(pView, s, pOthers, null, missing, missing.size(), new int[1]));
			}
		}
		final Query.FacetPage page = Engine.facetPage(m, s, pFacet, counts, listed, tree ? m.tree(pFacet.source, pFacet.parent) : null, pWanted, pShown, q, total);
		page.more = more;
		return page;
	}

	/** Counts per value of a field over the records an XPath filter keeps, read with the user's session. */
	private static Map<String, Integer> readValues(final View pView, final Model.Src pSrc, final Model.Pred pWhere)
	{
		final long start = System.currentTimeMillis();
		final Map<String, Integer> out = new HashMap<String, Integer>();
		final Request r = pView.model.table.createRequest();
		r.setXPathFilter(pWhere.xpath);
		r.setSession(pView.session);
		final RequestResult rr = r.execute();
		try
		{
			for (Adaptation a; (a = rr.nextAdaptation()) != null;)
			{
				Object v;
				try
				{
					v = a.get(pSrc.node.getPathInAdaptation());
				}
				catch (final RuntimeException ex)
				{
					v = null;
				}
				for (final Object o : v instanceof java.util.Collection ? (java.util.Collection<?>) v : Collections.singletonList(v))
				{
					final String k = Cond.key(IndexBuilder.compact(o));
					if (k != null && !k.isEmpty())
					{
						out.merge(k, Integer.valueOf(1), Integer::sum);
					}
				}
			}
		}
		finally
		{
			rr.close();
			Engine.traced("xpath read", pWhere.xpath + " -> values of " + pSrc.source, start);
		}
		return out;
	}

	/** The values of a source when they are few and known in advance (static enumeration, boolean, small referenced table). */
	private static Map<String, String> shortList(final View pView, final Model.Src pSrc)
	{
		final Model pModel = pView.model;
		if (pSrc == null)
		{
			return null;
		}
		Map<String, String> l = pModel.enumeration(pSrc);
		if (l == null && "fk".equals(pSrc.type))
		{
			l = pModel.targetLabels(pSrc);
		}
		if (l == null && pSrc.def != null && "test".equals(pSrc.def.path("type").asText()))
		{
			l = new LinkedHashMap<String, String>();
			l.put("true", "true");
			l.put("false", "false");
		}
		if (l == null && pSrc.node != null && pSrc.sql != null && !"list".equals(pSrc.type))
		{
			// a plain field that holds few distinct values in this table (source system, language): asked once
			l = pModel.fewValues(pSrc, () -> {
				final Map<String, String> found = new LinkedHashMap<String, String>();
				final Map<String, Integer> g = Engine.grouped(pView, pSrc, Model.Pred.TRUE, null, null, Engine.SHORT_LIST + 1, new int[1]);
				if (g.size() <= Engine.SHORT_LIST)
				{
					for (final String k : g.keySet())
					{
						found.put(k, pModel.label(pSrc, k));
					}
				}
				return found;
			});
		}
		return l != null && !l.isEmpty() && l.size() <= Engine.SHORT_LIST ? l : null;
	}

	/**
	 * SQL counts per value: {@code SELECT v, COUNT(*) … GROUP BY v ORDER BY COUNT(*) DESC}, at most {@code pMax} values;
	 * {@code pOnly} restricts to these values; {@code pDistinct[0]} receives the number of distinct values.
	 */
	private static Map<String, Integer> grouped(final View pView, final Model.Src pSrc, final Model.Pred pOthers, final String pSearch, final Set<String> pOnly,
		final int pMax, final int[] pDistinct)
	{
		final Model m = pView.model;
		final Map<String, Integer> out = new LinkedHashMap<String, Integer>();
		if (pSrc == null || pOthers == Model.Pred.FALSE)
		{
			return out;
		}
		String value = pSrc.sql;
		String from = m.from + " t";
		if ("list".equals(pSrc.type) && pSrc.node != null && !pSrc.node.isLinkedNode() && pSrc.node.getFacetOnTableReference() == null && pSrc.def == null)
		{
			from += ", UNNEST(t." + Model.dotted(pSrc.node.getPathInAdaptation()) + ") AS u(v)";
			value = "u.v";
		}
		if (value == null || pOthers.sql == null)
		{
			LoggingCategory.getKernel().warn("[layout-designer] explorer: no SQL count for the values of " + pSrc.source);
			return out;
		}
		final StringBuilder where = new StringBuilder(pOthers.sql).append(" AND ").append(value).append(" IS NOT NULL");
		if (pOnly != null)
		{
			final Model.Pred in = m.in(pSrc, pOnly);
			if (in == null || in.sql == null)
			{
				return out;
			}
			where.append(" AND ").append(in.sql);
		}
		if (pSearch != null)
		{
			where.append(" AND (").append(Sql.like(value, "text".equals(pSrc.type) || "fk".equals(pSrc.type) || "u.v".equals(value), pSearch.toLowerCase(m.locale)));
			final Map<String, String> labels = "fk".equals(pSrc.type) ? m.targetLabels(pSrc) : m.enumeration(pSrc);
			if (labels != null)
			{
				final List<String> hit = new ArrayList<String>();
				for (final Map.Entry<String, String> e : labels.entrySet())
				{
					if (Model.norm(e.getValue()).contains(Model.norm(pSearch)) && hit.size() < 500)
					{
						hit.add(e.getKey());
					}
				}
				final Model.Pred in = hit.isEmpty() ? null : m.in(pSrc, hit);
				if (in != null && in.sql != null)
				{
					where.append(" OR ").append(in.sql);
				}
			}
			where.append(')');
		}
		final String limit = pMax == Integer.MAX_VALUE ? "" : " FETCH FIRST " + pMax + " ROWS ONLY";
		for (final Object[] row : Engine.select(pView, "SELECT " + value + " AS v, COUNT(*) AS n FROM " + from + " WHERE " + where + " GROUP BY " + value
			+ " ORDER BY COUNT(*) DESC, " + value + limit, pMax))
		{
			final String k = Cond.key(row[0]);
			if (k != null && !k.isEmpty())
			{
				out.put(k, Integer.valueOf(Engine.integer(row[1])));
			}
		}
		pDistinct[0] = out.size();
		if (pMax != Integer.MAX_VALUE && out.size() >= pMax && pDistinct.length > 1 && pDistinct[1] == 0)
		{
			// a long list under a filter: "more than the values shown", without an exact COUNT(DISTINCT) (seconds on a large table)
			pDistinct[0] = -1;
		}
		else if (pMax != Integer.MAX_VALUE && out.size() >= pMax)
		{
			final List<Object[]> n = Engine.select(pView, "SELECT COUNT(DISTINCT " + value + ") AS n FROM " + from + " WHERE " + where, 1);
			pDistinct[0] = n.isEmpty() ? out.size() : Engine.integer(n.get(0)[0]);
		}
		return out;
	}

	/** One page of a facet's values: the selected ones first, then the most frequent (or the model's order for a short list). */
	private static Query.FacetPage facetPage(final Model pModel, final Model.Src pSrc, final Spec.Facet pFacet, final Map<String, Integer> pCounts,
		final Map<String, String> pListed, final Model.Tree pTree, final Set<String> pWanted, final int pShown, final String pSearch, final int pTotal)
	{
		final Query.FacetPage out = new Query.FacetPage();
		final java.util.function.Function<String, String> label = k -> pTree != null && pTree.label.containsKey(k) ? pTree.label.get(k)
			: pListed != null && pListed.containsKey(k) ? pListed.get(k) : pModel.label(pSrc, k);
		final List<String> matching = new ArrayList<String>();
		for (final String k : pCounts.keySet())
		{
			if (pSearch == null || pListed == null && pTree == null || Model.norm(label.apply(k)).contains(Model.norm(pSearch))
				|| Model.norm(k).contains(Model.norm(pSearch)))
			{
				matching.add(k);
			}
		}
		out.total = pTotal >= 0 ? pTotal : matching.size();
		final boolean modelOrder = pListed != null && pListed.size() <= pFacet.top && pSearch == null;
		if (!modelOrder)
		{
			matching.sort((a, b) -> {
				final int c = Integer.compare(pCounts.get(b).intValue(), pCounts.get(a).intValue());
				return c != 0 ? c : label.apply(a).compareToIgnoreCase(label.apply(b));
			});
		}
		final List<String> page = new ArrayList<String>(pWanted);
		for (final String k : matching)
		{
			if (page.size() >= pShown + pWanted.size())
			{
				break;
			}
			if (!pWanted.contains(k) && (modelOrder || pCounts.get(k).intValue() > 0 || pListed != null))
			{
				page.add(k);
			}
		}
		if (pTree != null && pSearch == null)
		{
			// a tree shows its values under their parents: the page depth first
			final List<String> ordered = new ArrayList<String>();
			Engine.walk(pTree, null, page, ordered, 0);
			for (final String k : page)
			{
				if (!ordered.contains(k))
				{
					ordered.add(k);
				}
			}
			page.clear();
			page.addAll(ordered);
		}
		for (final String k : page)
		{
			final Integer n = pCounts.get(k);
			out.items.add(new Query.Item(k, label.apply(k), n == null ? 0 : n.intValue(), pTree == null ? 0 : Engine.depth(pTree, k), pWanted.contains(k)));
		}
		return out;
	}

	private static void walk(final Model.Tree pTree, final String pParent, final List<String> pKeep, final List<String> pOut, final int pDepth)
	{
		if (pDepth > 12)
		{
			return;
		}
		for (final String k : pTree.label.keySet())
		{
			final String p = pTree.parent.get(k);
			if ((pParent == null ? p == null || !pTree.label.containsKey(p) : pParent.equals(p)) && !pOut.contains(k))
			{
				if (pKeep.contains(k))
				{
					pOut.add(k);
				}
				Engine.walk(pTree, k, pKeep, pOut, pDepth + 1);
			}
		}
	}

	private static int depth(final Model.Tree pTree, final String pKey)
	{
		int d = 0;
		for (String p = pTree.parent.get(pKey); p != null && d < 12; p = pTree.parent.get(p))
		{
			d++;
		}
		return d;
	}

	/** Values of one source over the records this user sees (designer, long lists): most frequent first, one page. */
	static Query.FacetPage values(final View pView, final String pSource, final String pSearch, final int pOffset, final int pCount)
	{
		final Model m = pView.model;
		final Model.Src s = m.src(pSource);
		final Query.FacetPage out = new Query.FacetPage();
		if (s == null)
		{
			return out;
		}
		final String q = pSearch == null || pSearch.trim().isEmpty() ? null : pSearch.trim();
		final int n = Math.max(1, Math.min(Query.MAX_VALUES, pCount));
		final int[] distinct = new int[1];
		final Map<String, Integer> counts = Engine.grouped(pView, s, Model.Pred.TRUE, q, null, Math.max(0, pOffset) + n, distinct);
		out.total = distinct[0];
		int i = 0;
		for (final Map.Entry<String, Integer> e : counts.entrySet())
		{
			if (i++ >= pOffset)
			{
				out.items.add(new Query.Item(e.getKey(), m.label(s, e.getKey()), e.getValue().intValue(), 0, false));
			}
		}
		return out;
	}

	// ----- cards ------------------------------------------------------------------------------------------------------

	/** What the cards of a page show besides their fields, for these record keys. */
	public static final class Cards
	{
		/** Per record key: computed values (a number, a text, true/false; suspects: the keys of the other records). */
		public final Map<String, Map<String, Object>> derived = new HashMap<String, Map<String, Object>>();
		/** Per record key and flags facet: the flags raised. */
		public final Map<String, Map<String, List<String>>> flags = new HashMap<String, Map<String, List<String>>>();
	}

	/**
	 * The computed values and flags of the page's records. Each value read in other tables is one small EBX SQL statement
	 * grouped by the page's keys (a count, a value of a linked row, suspects, same-value counts); the values built from
	 * the record's own values ({@code test}, {@code case}, {@code firstOf}) and the flags are then evaluated on the records
	 * as the user reads them. One statement holding every nested value was tried first: EBX's planner refuses it
	 * ({@code CyclicMetadataException}) or takes many seconds.
	 */
	public static Cards cards(final View pView, final List<String> pKeys, final Set<String> pDerived, final Set<String> pFlagFacets)
	{
		// the cards are never older than the automatic mode allows, whatever the explorer's mode
		Engine.begin(pView, false, null, true);
		try
		{
			return Engine.cardsKept(pView, pKeys, pDerived, pFlagFacets);
		}
		finally
		{
			Engine.end();
		}
	}

	private static Cards cardsKept(final View pView, final List<String> pKeys, final Set<String> pDerived, final Set<String> pFlagFacets)
	{
		final Cards out = new Cards();
		final Model m = pView.model;
		final Map<String, Adaptation> records = new LinkedHashMap<String, Adaptation>();
		for (final String k : pKeys)
		{
			try
			{
				final Adaptation r = m.table.lookupAdaptationByPrimaryKey(com.onwbp.adaptation.PrimaryKey.parseString(k));
				if (r != null && !pView.session.getPermissions().getAdaptationAccessPermission(r).isHidden())
				{
					records.put(k, r);
				}
			}
			catch (final RuntimeException ex)
			{
				// a key no longer valid: no card
			}
		}
		if (records.isEmpty())
		{
			return out;
		}
		// every computed value the cards and their flags read, and those these values read
		final Set<String> needed = new LinkedHashSet<String>();
		final List<Spec.Flag> flags = new ArrayList<Spec.Flag>();
		final Map<Spec.Flag, String> flagFacet = new HashMap<Spec.Flag, String>();
		for (final String d : pDerived)
		{
			needed.add(d.substring(Spec.DERIVED.length()));
		}
		for (final String id : pFlagFacets)
		{
			final Spec.Facet f = pView.facet(id);
			if (f != null)
			{
				for (final Spec.Flag fl : f.flags)
				{
					flags.add(fl);
					flagFacet.put(fl, id);
					Cond.reads(fl.when, new HashSet<String>(), needed);
				}
			}
		}
		boolean grew = true;
		while (grew)
		{
			grew = false;
			for (final String d : new ArrayList<String>(needed))
			{
				final Set<String> more = new LinkedHashSet<String>();
				Spec.derivedReads(pView.spec.derived.get(d), new HashSet<String>(), more);
				grew |= needed.addAll(more);
			}
		}
		final Map<String, Map<String, Object>> values = new HashMap<String, Map<String, Object>>();
		for (final String k : records.keySet())
		{
			values.put(k, new HashMap<String, Object>());
		}
		// values read in other tables, one statement each for the whole page
		for (final String d : needed)
		{
			final String src = Spec.DERIVED + d;
			final com.fasterxml.jackson.databind.JsonNode def = pView.spec.derived.get(d);
			if (def == null || pView.hidden.contains(src))
			{
				continue;
			}
			switch (def.path("type").asText(""))
			{
			case "count":
			case "lookup":
				Engine.linkedValues(pView, d, def, records.keySet(), values);
				break;
			case "suspects":
				Engine.suspects(pView, d, records.keySet(), values);
				break;
			case "sameValue":
				Engine.sameValues(pView, d, def, records, values);
				break;
			case "provided":
			{
				final com.on.ebx.layout.runtime.ExplorerValues.Provider p = com.on.ebx.layout.runtime.ExplorerValues.get(def.path("name").asText(""));
				for (final Map.Entry<String, Adaptation> e : records.entrySet())
				{
					try
					{
						final Object v = p == null ? null : IndexBuilder.compact(p.value(e.getValue()));
						if (v != null)
						{
							values.get(e.getKey()).put(d, v);
						}
					}
					catch (final RuntimeException ex)
					{
						// a provider's failure leaves the value empty
					}
				}
				break;
			}
			default:
				break;
			}
		}
		// values built from the record's own values, in their declared order, then the flags
		for (final Map.Entry<String, Adaptation> e : records.entrySet())
		{
			final Map<String, Object> v = values.get(e.getKey());
			final Cond.Lookup lookup = Engine.lookup(pView, e.getValue(), v);
			for (final Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> d : pView.spec.derived.entrySet())
			{
				final String type = d.getValue().path("type").asText("");
				if (needed.contains(d.getKey()) && !pView.hidden.contains(Spec.DERIVED + d.getKey())
					&& ("test".equals(type) || "case".equals(type) || "firstOf".equals(type)))
				{
					final Object x = Engine.rowValue(pView, d.getValue(), lookup);
					if (x != null)
					{
						v.put(d.getKey(), x);
					}
				}
			}
			for (final Spec.Flag fl : flags)
			{
				if (Cond.test(fl.when, lookup))
				{
					out.flags.computeIfAbsent(e.getKey(), x -> new HashMap<String, List<String>>()).computeIfAbsent(flagFacet.get(fl), x -> new ArrayList<String>())
						.add(fl.id);
				}
			}
			final Map<String, Object> shown = new HashMap<String, Object>();
			for (final String d : pDerived)
			{
				final Object x = v.get(d.substring(Spec.DERIVED.length()));
				if (x != null)
				{
					shown.put(d, x);
				}
			}
			out.derived.put(e.getKey(), shown);
		}
		return out;
	}

	/** A record's values as the user reads them (a field hidden on this record reads empty) and its computed values. */
	private static Cond.Lookup lookup(final View pView, final Adaptation pRecord, final Map<String, Object> pDerived)
	{
		return new Cond.Lookup()
		{
			@Override
			public Object field(final String pPath)
			{
				final com.orchestranetworks.schema.SchemaNode n = pView.model.node(pPath);
				if (n == null || pView.hidden.contains(pPath) || pView.session.getPermissions().getNodeAccessPermission(n, pRecord).isHidden())
				{
					return null;
				}
				try
				{
					return IndexBuilder.compact(pRecord.get(n.getPathInAdaptation()));
				}
				catch (final RuntimeException ex)
				{
					return null;
				}
			}

			@Override
			public Object derived(final String pName)
			{
				return pView.hidden.contains(Spec.DERIVED + pName) ? null : pDerived.get(pName);
			}
		};
	}

	/** {@code count} or {@code lookup} for the page's keys: one statement on the linked table grouped by its foreign key. */
	private static void linkedValues(final View pView, final String pName, final com.fasterxml.jackson.databind.JsonNode pDef, final Set<String> pKeys,
		final Map<String, Map<String, Object>> pValues)
	{
		final Model m = pView.model;
		final boolean count = "count".equals(pDef.path("type").asText());
		final AdaptationTable child = IndexBuilder.linkedTable(m.dataset, m.table, pDef);
		final com.orchestranetworks.schema.Path fk = IndexBuilder.linkedKey(m.table, pDef, child);
		if (child == null || fk == null)
		{
			return;
		}
		final Model c = m.on(child);
		final String key = "FK_AS_STRING(t." + Model.dotted(fk) + ")";
		final Model.Pred keys = m.keysAs(key, pKeys);
		final Model.Pred where = c.pred(pDef.get(count ? "where" : "prefer"));
		if (where == null || where.sql == null)
		{
			return;
		}
		String sql;
		if (count)
		{
			sql = "SELECT " + key + " AS k, COUNT(*) AS n FROM " + c.from + " t WHERE " + keys.sql + (where.isTrue() ? "" : " AND " + where.sql) + " GROUP BY " + key;
		}
		else
		{
			final Model.Src f = c.src(pDef.path("field").asText(""));
			if (f == null || f.sql == null)
			{
				return;
			}
			sql = "SELECT " + key + " AS k, MIN(" + f.sql + ") AS v" + (where.isTrue() ? "" : ", MIN(CASE WHEN " + where.sql + " THEN " + f.sql + " END) AS p") + " FROM "
				+ c.from + " t WHERE " + keys.sql + " GROUP BY " + key;
		}
		for (final Object[] row : Engine.select(pView, sql, Integer.MAX_VALUE))
		{
			final Map<String, Object> v = pValues.get(String.valueOf(row[0]));
			if (v == null)
			{
				continue;
			}
			final Object x = count ? row[1] : row.length > 2 && row[2] != null ? row[2] : row[1];
			if (x != null)
			{
				v.put(pName, IndexBuilder.compact(x));
			}
		}
		if (count)
		{
			for (final Map<String, Object> v : pValues.values())
			{
				v.putIfAbsent(pName, Double.valueOf(0));
			}
		}
	}

	/** Suspects of the page's records: the keys of the other records, both directions. */
	private static void suspects(final View pView, final String pName, final Set<String> pKeys, final Map<String, Map<String, Object>> pValues)
	{
		final Model m = pView.model;
		if (m.mdsTable == null)
		{
			return;
		}
		final List<String[]> pairs = new ArrayList<String[]>();
		if (m.mdsTable.endsWith("Suspected") && !Boolean.TRUE.equals(Engine.PREFER_SQL.get()))
		{
			// the add-on's table through its own index: one XPath request for the page
			final long start = System.currentTimeMillis();
			final StringBuilder x = new StringBuilder();
			for (final String k : pKeys)
			{
				x.append(x.length() == 0 ? "" : " or ").append("./sourceId = ").append(XPath.lit(k)).append(" or ./targetId = ").append(XPath.lit(k));
			}
			final Request r = m.mds.getTable(com.orchestranetworks.schema.Path.parse(m.mdsTable)).createRequest();
			r.setXPathFilter(x.toString());
			r.setSession(pView.session);
			final RequestResult rr = r.execute();
			try
			{
				for (Adaptation a; (a = rr.nextAdaptation()) != null;)
				{
					pairs.add(new String[] { Cond.key(a.get(com.orchestranetworks.schema.Path.parse("./sourceId"))),
						Cond.key(a.get(com.orchestranetworks.schema.Path.parse("./targetId"))) });
				}
			}
			finally
			{
				rr.close();
				Engine.traced("xpath read", "suspects of the page (" + pKeys.size() + " records)", start);
			}
		}
		else
		{
			final StringBuilder in = new StringBuilder();
			for (final String k : pKeys)
			{
				in.append(in.length() == 0 ? "" : ", ").append(Sql.lit(k));
			}
			for (final Object[] row : Engine.select(pView, "SELECT s.sourceId AS a, s.targetId AS b FROM mds." + Model.quote(m.mdsTable) + " s WHERE " + m.suspectRow("s")
				+ " AND (s.sourceId IN (" + in + ") OR s.targetId IN (" + in + "))", Integer.MAX_VALUE))
			{
				pairs.add(new String[] { Cond.key(row[0]), Cond.key(row[1]) });
			}
		}
		for (final String[] p : pairs)
		{
			if (p[0] == null || p[1] == null || p[0].equals(p[1]))
			{
				continue;
			}
			Engine.addSuspect(pValues, pName, p[0], p[1]);
			Engine.addSuspect(pValues, pName, p[1], p[0]);
		}
		for (final Map<String, Object> v : pValues.values())
		{
			v.putIfAbsent(pName, new ArrayList<Object>());
		}
	}

	@SuppressWarnings("unchecked")
	private static void addSuspect(final Map<String, Map<String, Object>> pValues, final String pName, final String pOf, final String pOther)
	{
		final Map<String, Object> v = pValues.get(pOf);
		if (v != null)
		{
			final List<Object> l = (List<Object>) v.computeIfAbsent(pName, x -> new ArrayList<Object>());
			if (!l.contains(pOther))
			{
				l.add(pOther);
			}
		}
	}

	/** {@code sameValue} for the page's records: how many records hold each of their values. */
	private static void sameValues(final View pView, final String pName, final com.fasterxml.jackson.databind.JsonNode pDef, final Map<String, Adaptation> pRecords,
		final Map<String, Map<String, Object>> pValues)
	{
		final Model m = pView.model;
		final Model.Src f = m.src(pDef.path("field").asText(""));
		if (f == null || f.sql == null || f.node == null)
		{
			return;
		}
		final Map<String, String> valueOf = new HashMap<String, String>();
		for (final Map.Entry<String, Adaptation> e : pRecords.entrySet())
		{
			final String k = Cond.key(IndexBuilder.compact(e.getValue().get(f.node.getPathInAdaptation())));
			if (k != null && !k.isEmpty())
			{
				valueOf.put(e.getKey(), k);
			}
		}
		final Map<String, Integer> counts = new HashMap<String, Integer>();
		if (!valueOf.isEmpty())
		{
			final Model.Pred in = m.in(f, new HashSet<String>(valueOf.values()));
			for (final Object[] row : Engine.select(pView, "SELECT " + f.sql + " AS v, COUNT(*) AS n FROM " + m.from + " t WHERE " + in.sql + " GROUP BY " + f.sql,
				Integer.MAX_VALUE))
			{
				counts.put(Cond.key(row[0]), Integer.valueOf(Engine.integer(row[1])));
			}
		}
		for (final Map.Entry<String, Map<String, Object>> e : pValues.entrySet())
		{
			final String v = valueOf.get(e.getKey());
			e.getValue().put(pName, Double.valueOf(v == null ? 0 : counts.getOrDefault(v, Integer.valueOf(0)).intValue()));
		}
	}

	/** {@code test}, {@code case} and {@code firstOf} from a record's values (and the computed values declared before). */
	private static Object rowValue(final View pView, final com.fasterxml.jackson.databind.JsonNode pDef, final Cond.Lookup pLookup)
	{
		switch (pDef.path("type").asText(""))
		{
		case "test":
			return Boolean.valueOf(Cond.test(pDef.get("when"), pLookup));
		case "case":
			for (final com.fasterxml.jackson.databind.JsonNode c : pDef.path("cases"))
			{
				if (Cond.test(c.get("when"), pLookup))
				{
					return Engine.scalar(c.get("value"));
				}
			}
			return Engine.scalar(pDef.get("else"));
		default:
			for (final com.fasterxml.jackson.databind.JsonNode it : pDef.path("items"))
			{
				final String src = Spec.source(it);
				final Object v = src == null ? null : src.startsWith(Spec.DERIVED) ? pLookup.derived(src.substring(Spec.DERIVED.length())) : pLookup.field(src);
				if (!Cond.isEmpty(v) && Cond.test(it.get("when"), pLookup))
				{
					final String k = Cond.key(v instanceof java.util.Collection ? ((java.util.Collection<?>) v).iterator().next() : v);
					return it.path("prefix").asText("") + pView.model.label(pView.model.src(src), k);
				}
			}
			return null;
		}
	}

	private static Object scalar(final com.fasterxml.jackson.databind.JsonNode pValue)
	{
		if (pValue == null || pValue.isNull())
		{
			return null;
		}
		if (pValue.isNumber())
		{
			return Double.valueOf(pValue.asDouble());
		}
		if (pValue.isBoolean())
		{
			return Boolean.valueOf(pValue.asBoolean());
		}
		return pValue.asText();
	}
}
