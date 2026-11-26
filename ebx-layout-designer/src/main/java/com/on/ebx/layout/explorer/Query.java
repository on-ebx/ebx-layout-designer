package com.on.ebx.layout.explorer;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One request of the explorer page on a {@link View}: the text, the selected facet values, the tile, the sort. Answers
 * the matching rows in order, each facet's counts (a row counts for a facet when it passes every OTHER facet), each
 * tile's count, and for each facet one page of values: never a whole list of values. Pure.
 */
public final class Query
{
	public static final int MAX_VALUES = 50;

	/** One value of a facet: key, label, count, depth (tree), selected. */
	public static final class Item
	{
		public final String v, l;
		public final int n, d;
		public final boolean sel;

		Item(final String pV, final String pL, final int pN, final int pD, final boolean pSel)
		{
			this.v = pV;
			this.l = pL;
			this.n = pN;
			this.d = pD;
			this.sel = pSel;
		}
	}

	public static final class FacetPage
	{
		/** Distinct values matching the facet's search (all values when none), among the rows counted. */
		public int total;
		public final List<Item> items = new ArrayList<Item>();
	}

	public static final class Result
	{
		public int all;
		public final List<Integer> rows = new ArrayList<Integer>();
		public final Map<String, Integer> tiles = new LinkedHashMap<String, Integer>();
		public final Map<String, FacetPage> facets = new LinkedHashMap<String, FacetPage>();
	}

	/** What the page asks: per facet, how many values to show and the text typed in the facet's own search. */
	public static final class Request
	{
		public String text;
		public final Map<String, Set<String>> selected = new HashMap<String, Set<String>>();
		public String tile;
		public int sort;
		public final Map<String, Integer> shown = new HashMap<String, Integer>();
		public final Map<String, String> facetSearch = new HashMap<String, String>();
		public Set<String> favorites = new TreeSet<String>();
	}

	private Query()
	{
	}

	/** {@code "st=ACTIF|BLOQUE;flag=RIB"}: facet id, then its values; unknown ids are ignored by the query. */
	public static Map<String, Set<String>> parseSelection(final String pText)
	{
		final Map<String, Set<String>> out = new HashMap<String, Set<String>>();
		if (pText == null)
		{
			return out;
		}
		for (final String part : pText.split(";"))
		{
			final int eq = part.indexOf('=');
			if (eq <= 0)
			{
				continue;
			}
			final Set<String> values = new TreeSet<String>();
			for (final String v : part.substring(eq + 1).split("\\|"))
			{
				if (!v.isEmpty())
				{
					values.add(Query.unescape(v));
				}
			}
			if (!values.isEmpty())
			{
				out.put(part.substring(0, eq), values);
			}
		}
		return out;
	}

	/** Values may hold ";", "|" or "=": the page writes them as %3B, %7C, %3D, and % as %25. */
	static String unescape(final String pValue)
	{
		return pValue.replace("%3B", ";").replace("%7C", "|").replace("%3D", "=").replace("%25", "%");
	}

	public static Result run(final View pView, final Request pRequest, final Locale pLocale)
	{
		final Result out = new Result();
		out.all = pView.visibleCount;
		final List<Spec.Facet> facets = pView.facets;
		final int nf = facets.size();
		final List<Map<String, Integer>> counts = new ArrayList<Map<String, Integer>>();
		final boolean[] active = new boolean[nf];
		final List<Set<String>> wanted = new ArrayList<Set<String>>();
		for (int f = 0; f < nf; f++)
		{
			counts.add(new HashMap<String, Integer>());
			final Set<String> w = pRequest.selected.get(facets.get(f).id);
			wanted.add(w);
			active[f] = w != null && !w.isEmpty();
		}
		final String q = pRequest.text == null || pRequest.text.trim().isEmpty() ? null : Index.norm(pRequest.text.trim());
		final Spec.Tile tile = Query.tile(pView, pRequest.tile);
		final Map<String, Integer> facetIndex = new HashMap<String, Integer>();
		for (int f = 0; f < nf; f++)
		{
			facetIndex.put(facets.get(f).id, Integer.valueOf(f));
		}
		for (final Spec.Tile t : pView.tiles)
		{
			out.tiles.put(t.id, Integer.valueOf(0));
		}

		for (int r = pView.visible.nextSetBit(0); r >= 0; r = pView.visible.nextSetBit(r + 1))
		{
			for (final Spec.Tile t : pView.tiles)
			{
				if (Query.tileHolds(pView, t, r, facetIndex))
				{
					out.tiles.merge(t.id, Integer.valueOf(1), Integer::sum);
				}
			}
			if (q != null && !Query.textHolds(pView, r, q))
			{
				continue;
			}
			if (tile != null && !Query.tileHolds(pView, tile, r, facetIndex))
			{
				continue;
			}
			int failures = 0;
			int failed = -1;
			for (int f = 0; f < nf && failures < 2; f++)
			{
				if (active[f] && !Query.facetHolds(pView, f, r, wanted.get(f), pRequest.favorites))
				{
					failures++;
					failed = f;
				}
			}
			if (failures > 1)
			{
				continue;
			}
			if (failures == 0)
			{
				out.rows.add(Integer.valueOf(r));
			}
			for (int f = 0; f < nf; f++)
			{
				if (failures == 0 || failed == f)
				{
					final Map<String, Integer> c = counts.get(f);
					if ("favorites".equals(facets.get(f).kind))
					{
						if (pRequest.favorites.contains(pView.ix.keys[r]))
						{
							c.merge("1", Integer.valueOf(1), Integer::sum);
						}
						continue;
					}
					for (final String v : pView.facetValues(f, r))
					{
						c.merge(v, Integer.valueOf(1), Integer::sum);
					}
				}
			}
		}
		Query.sort(pView, out.rows, pRequest.sort, pLocale);
		for (int f = 0; f < nf; f++)
		{
			final Spec.Facet facet = facets.get(f);
			final Integer shown = pRequest.shown.get(facet.id);
			out.facets.put(facet.id, Query.page(pView, facet, counts.get(f), wanted.get(f), shown == null ? facet.top : shown.intValue(),
				pRequest.facetSearch.get(facet.id), pLocale));
		}
		return out;
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

	private static boolean textHolds(final View pView, final int pRow, final String pText)
	{
		for (final String s : pView.search)
		{
			final Integer slot = pView.ix.slots.get(s);
			if (slot == null || pView.hiddenOn(pRow, s))
			{
				continue;
			}
			final String t = pView.ix.text[pRow][slot.intValue()];
			if (t != null && t.contains(pText))
			{
				return true;
			}
		}
		return pView.search.isEmpty() && Index.norm(pView.ix.labels[pRow]).contains(pText);
	}

	private static boolean facetHolds(final View pView, final int pFacet, final int pRow, final Set<String> pWanted, final Set<String> pFavorites)
	{
		if ("favorites".equals(pView.facets.get(pFacet).kind))
		{
			return pFavorites.contains(pView.ix.keys[pRow]);
		}
		for (final String v : pView.facetValues(pFacet, pRow))
		{
			if (pWanted.contains(v))
			{
				return true;
			}
		}
		return false;
	}

	static boolean tileHolds(final View pView, final Spec.Tile pTile, final int pRow, final Map<String, Integer> pFacetIndex)
	{
		if (pTile.flag != null)
		{
			for (int f = 0; f < pView.facets.size(); f++)
			{
				if ("flags".equals(pView.facets.get(f).kind))
				{
					for (final String v : pView.facetValues(f, pRow))
					{
						if (pTile.flag.equals(v))
						{
							return true;
						}
					}
				}
			}
			return false;
		}
		if (!pTile.set.isEmpty())
		{
			for (final Map.Entry<String, Set<String>> e : pTile.set.entrySet())
			{
				final Integer f = pFacetIndex.get(e.getKey());
				if (f == null)
				{
					return false;
				}
				boolean hit = false;
				for (final String v : pView.facetValues(f.intValue(), pRow))
				{
					hit |= e.getValue().contains(v);
				}
				if (!hit)
				{
					return false;
				}
			}
			return true;
		}
		return Cond.test(pTile.when, pView.lookup(pRow));
	}

	private static void sort(final View pView, final List<Integer> pRows, final int pSort, final Locale pLocale)
	{
		final Collator collator = Collator.getInstance(pLocale == null ? Locale.ENGLISH : pLocale);
		collator.setStrength(Collator.SECONDARY);
		final Spec.Sort sort = pView.sorts.isEmpty() ? null : pView.sorts.get(Math.max(0, Math.min(pView.sorts.size() - 1, pSort)));
		final Comparator<Integer> byLabel = (a, b) -> collator.compare(String.valueOf(pView.ix.labels[a.intValue()]), String.valueOf(pView.ix.labels[b.intValue()]));
		if (sort == null)
		{
			pRows.sort(byLabel);
			return;
		}
		final Comparator<Integer> byValue = (a, b) -> {
			final Object x = Query.sortable(pView.value(a.intValue(), sort.source)), y = Query.sortable(pView.value(b.intValue(), sort.source));
			if (x == null || y == null)
			{
				// empty values last, whatever the direction
				return x == null && y == null ? 0 : x == null ? 1 : -1;
			}
			int c;
			if (x instanceof Double && y instanceof Double)
			{
				c = ((Double) x).compareTo((Double) y);
			}
			else if (x instanceof Date && y instanceof Date)
			{
				c = ((Date) x).compareTo((Date) y);
			}
			else
			{
				final String lx = pView.ix.label(sort.source, Cond.key(x)), ly = pView.ix.label(sort.source, Cond.key(y));
				c = collator.compare(lx, ly);
			}
			return sort.desc ? -c : c;
		};
		pRows.sort(byValue.thenComparing(byLabel));
	}

	private static Object sortable(final Object pValue)
	{
		if (pValue instanceof Collection)
		{
			return ((Collection<?>) pValue).size() == 0 ? null : Double.valueOf(((Collection<?>) pValue).size());
		}
		return pValue;
	}

	/** One page of a facet's values: the selected ones first, then the most frequent (or the model's order for a short list). */
	static FacetPage page(final View pView, final Spec.Facet pFacet, final Map<String, Integer> pCounts, final Set<String> pWanted, final int pShown,
		final String pSearch, final Locale pLocale)
	{
		final FacetPage out = new FacetPage();
		final Set<String> sel = pWanted == null ? new TreeSet<String>() : pWanted;
		final String q = pSearch == null || pSearch.trim().isEmpty() ? null : Index.norm(pSearch.trim());
		final int shown = Math.max(1, Math.min(Query.MAX_VALUES, pShown));
		if ("favorites".equals(pFacet.kind))
		{
			out.total = 1;
			out.items.add(new Item("1", "", pCounts.getOrDefault("1", Integer.valueOf(0)).intValue(), 0, sel.contains("1")));
			return out;
		}
		if ("flags".equals(pFacet.kind) || "ranges".equals(pFacet.kind))
		{
			final List<String[]> all = new ArrayList<String[]>();
			for (final Spec.Flag fl : pFacet.flags)
			{
				all.add(new String[] { fl.id, Query.text(fl.label, pLocale) });
			}
			for (final Spec.Range r : pFacet.ranges)
			{
				all.add(new String[] { r.code, Query.text(r.label, pLocale) });
			}
			for (final String[] v : all)
			{
				out.items.add(new Item(v[0], v[1], pCounts.getOrDefault(v[0], Integer.valueOf(0)).intValue(), 0, sel.contains(v[0])));
			}
			out.total = all.size();
			return out;
		}
		final Index.Tree tree = "tree".equals(pFacet.kind) ? pView.ix.trees.get(pFacet.source) : null;
		final java.util.function.Function<String, String> label = k -> tree != null && tree.label.containsKey(k) ? tree.label.get(k) : pView.ix.label(pFacet.source, k);
		// the values to choose from: those counted, plus a short model enumeration's values at zero
		final Map<String, Integer> pool = new LinkedHashMap<String, Integer>();
		final Map<String, String> modelValues = pView.ix.modelOrder.contains(pFacet.source) ? pView.ix.valueLabels.get(pFacet.source) : null;
		if (modelValues != null && tree == null)
		{
			for (final String k : modelValues.keySet())
			{
				pool.put(k, pCounts.getOrDefault(k, Integer.valueOf(0)));
			}
		}
		for (final Map.Entry<String, Integer> e : pCounts.entrySet())
		{
			pool.putIfAbsent(e.getKey(), e.getValue());
		}
		final List<String> matching = new ArrayList<String>();
		for (final String k : pool.keySet())
		{
			if (q == null || Index.norm(label.apply(k)).contains(q) || Index.norm(k).contains(q))
			{
				matching.add(k);
			}
		}
		out.total = matching.size();
		final boolean modelOrder = modelValues != null && pool.size() <= pFacet.top && q == null;
		if (!modelOrder)
		{
			matching.sort((a, b) -> {
				final int c = Integer.compare(pool.get(b).intValue(), pool.get(a).intValue());
				return c != 0 ? c : label.apply(a).compareToIgnoreCase(label.apply(b));
			});
		}
		final List<String> page = new ArrayList<String>();
		for (final String k : sel)
		{
			page.add(k);
		}
		for (final String k : matching)
		{
			if (page.size() >= shown + sel.size())
			{
				break;
			}
			if (!sel.contains(k))
			{
				page.add(k);
			}
		}
		if (tree != null && q == null)
		{
			// a tree shows its values under their parents: order the page depth first
			final List<String> ordered = new ArrayList<String>();
			Query.walk(tree, null, page, ordered, 0);
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
			out.items.add(new Item(k, label.apply(k), pCounts.getOrDefault(k, Integer.valueOf(0)).intValue(), tree == null ? 0 : Query.depth(tree, k),
				sel.contains(k)));
		}
		return out;
	}

	private static void walk(final Index.Tree pTree, final String pParent, final List<String> pKeep, final List<String> pOut, final int pDepth)
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
				Query.walk(pTree, k, pKeep, pOut, pDepth + 1);
			}
		}
	}

	private static int depth(final Index.Tree pTree, final String pKey)
	{
		int d = 0;
		for (String p = pTree.parent.get(pKey); p != null && d < 12; p = pTree.parent.get(p))
		{
			d++;
		}
		return d;
	}

	/** A configured text in the user's locale (else the closest one). */
	static String text(final com.fasterxml.jackson.databind.JsonNode pText, final Locale pLocale)
	{
		return pText == null ? "" : com.on.ebx.layout.runtime.LText.pick(pText, pLocale == null ? Locale.US : pLocale, Locale.US);
	}

	/** Values of one source (a field or a computed value) over the rows this user sees, for the designer and long lists. */
	public static FacetPage values(final View pView, final String pSource, final String pSearch, final int pOffset, final int pCount)
	{
		final Map<String, Integer> counts = new HashMap<String, Integer>();
		for (int r = pView.visible.nextSetBit(0); r >= 0; r = pView.visible.nextSetBit(r + 1))
		{
			final Object v = pView.value(r, pSource);
			for (final Object o : v instanceof Collection ? (Collection<?>) v : java.util.Collections.singletonList(v))
			{
				final String k = Cond.key(o);
				if (k != null && !k.isEmpty())
				{
					counts.merge(k, Integer.valueOf(1), Integer::sum);
				}
			}
		}
		final String q = pSearch == null || pSearch.trim().isEmpty() ? null : Index.norm(pSearch.trim());
		final List<String> keys = new ArrayList<String>();
		for (final String k : counts.keySet())
		{
			if (q == null || Index.norm(pView.ix.label(pSource, k)).contains(q) || Index.norm(k).contains(q))
			{
				keys.add(k);
			}
		}
		keys.sort((a, b) -> {
			final int c = Integer.compare(counts.get(b).intValue(), counts.get(a).intValue());
			return c != 0 ? c : a.compareTo(b);
		});
		final FacetPage out = new FacetPage();
		out.total = keys.size();
		for (final String k : keys.subList(Math.min(pOffset, keys.size()), Math.min(pOffset + Math.max(1, Math.min(Query.MAX_VALUES, pCount)), keys.size())))
		{
			out.items.add(new Item(k, pView.ix.label(pSource, k), counts.get(k).intValue(), 0, false));
		}
		return out;
	}
}
