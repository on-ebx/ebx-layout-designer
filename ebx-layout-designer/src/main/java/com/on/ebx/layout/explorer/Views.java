package com.on.ebx.layout.explorer;

import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.Request;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Role;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.service.SessionPermissions;

/**
 * The caches and the EBX reads behind the explorers: one {@link Index} per (dataset, explorer, locale), rebuilt when a
 * table it reads changes; one {@link View} per user on it, kept {@link #VIEW_MS} and rebuilt when the index is. A view's
 * permissions come from EBX: the records through a request run with the user's session, the fields through
 * {@link SessionPermissions#getNodeAccessPermission}, at table level and per record (record-dependent rules on fields are
 * not applied by {@code Request.setSession}, its Javadoc says so).
 */
public final class Views
{
	static final long VIEW_MS = 60_000L;
	private static final long INDEX_MAX_MS = 15 * 60_000L;
	private static final Map<String, Index> INDEXES = new ConcurrentHashMap<String, Index>();
	private static final Map<String, View> VIEWS = new ConcurrentHashMap<String, View>();

	private Views()
	{
	}

	/** The index of an explorer on a dataset, in a locale (labels and search are in that locale). */
	public static Index index(final Adaptation pDataset, final AdaptationTable pTable, final int pExplorer, final JsonNode pRoot, final Locale pLocale)
	{
		final String key = pDataset.getHome().getKey().getName() + "|" + pDataset.getAdaptationName().getStringName() + "|" + pExplorer + "|"
			+ pLocale.toLanguageTag();
		final long token = Views.token(pDataset, pTable, pRoot);
		final Index known = Views.INDEXES.get(key);
		if (known != null && known.token == token && System.currentTimeMillis() - known.builtAt < Views.INDEX_MAX_MS)
		{
			return known;
		}
		synchronized (key.intern())
		{
			final Index again = Views.INDEXES.get(key);
			if (again != null && again.token == token && System.currentTimeMillis() - again.builtAt < Views.INDEX_MAX_MS)
			{
				return again;
			}
			final Index built = new IndexBuilder(pDataset, pTable, pRoot, pLocale).build(token);
			Views.INDEXES.put(key, built);
			return built;
		}
	}

	/** Changes when a table the index reads is modified, or when the explorer's JSON changes. */
	static long token(final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pRoot)
	{
		long token = pRoot.toString().hashCode();
		for (final AdaptationTable t : IndexBuilder.readTables(pDataset, pTable, pRoot))
		{
			final Date d = t.getLastModificationDate();
			token = token * 31 + (d == null ? 0 : d.getTime());
		}
		return token;
	}

	/** The user's view of the index, for the variant their roles choose. */
	public static View view(final Session pSession, final Adaptation pDataset, final AdaptationTable pTable, final int pExplorer, final JsonNode pRoot)
	{
		final Locale locale = pSession.getLocale();
		final Index index = Views.index(pDataset, pTable, pExplorer, pRoot, locale);
		final Spec spec = Views.spec(pRoot, pSession);
		final String key = pSession.getUserReference().getUserId() + "|" + pDataset.getHome().getKey().getName() + "|"
			+ pDataset.getAdaptationName().getStringName() + "|" + pExplorer + "|" + locale.toLanguageTag() + "|" + spec.variantId;
		final View known = Views.VIEWS.get(key);
		if (known != null && known.ix == index && System.currentTimeMillis() - known.builtAt < Views.VIEW_MS)
		{
			return known;
		}
		final View built = Views.build(pSession, pDataset, pTable, index, spec);
		Views.VIEWS.put(key, built);
		if (Views.VIEWS.size() > 2000)
		{
			Views.VIEWS.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().builtAt > Views.VIEW_MS);
		}
		return built;
	}

	public static Spec spec(final JsonNode pRoot, final Session pSession)
	{
		final boolean seesAll = pRoot.path("adminSeesAll").asBoolean(true) && pSession.isUserInRole(Profile.ADMINISTRATOR);
		return new Spec(pRoot, roles -> Views.inAnyRole(pSession, roles), seesAll);
	}

	/** Role names as the designer writes them ("Achats"; "administrator" for the built-in role). */
	public static boolean inAnyRole(final Session pSession, final JsonNode pRoles)
	{
		if (pSession == null || pRoles == null)
		{
			return false;
		}
		for (final JsonNode r : pRoles)
		{
			final String name = r.asText("").trim();
			if (name.isEmpty())
			{
				continue;
			}
			try
			{
				final Profile p = "administrator".equalsIgnoreCase(name) || "Badministrator".equals(name) ? Role.ADMINISTRATOR : Role.forSpecificRole(name);
				if (pSession.isUserInRole(p))
				{
					return true;
				}
			}
			catch (final RuntimeException ex)
			{
				// an unknown profile name
			}
		}
		return false;
	}

	/** True when the user may open this explorer: its openTo roles (none = everybody) and read access to the table. */
	public static boolean mayOpen(final Session pSession, final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pRoot)
	{
		final JsonNode open = pRoot.path("openTo");
		if (open.size() > 0 && !pSession.isUserInRole(Profile.ADMINISTRATOR) && !Views.inAnyRole(pSession, open))
		{
			return false;
		}
		final SessionPermissions p = pSession.getPermissions();
		return !p.getAdaptationAccessPermission(pDataset).isHidden() && !p.getNodeAccessPermission(pTable.getTableNode(), pDataset).isHidden();
	}

	private static View build(final Session pSession, final Adaptation pDataset, final AdaptationTable pTable, final Index pIndex, final Spec pSpec)
	{
		final long start = System.currentTimeMillis();
		final SessionPermissions perms = pSession.getPermissions();
		final SchemaNode root = pTable.getTableOccurrenceRootNode();
		// fields: hidden for the whole table
		final Map<String, SchemaNode> nodes = new HashMap<String, SchemaNode>();
		final Set<String> hiddenFields = new HashSet<String>();
		for (final String s : Views.fieldSources(pIndex, pSpec))
		{
			final SchemaNode n = Views.node(root, s);
			if (n == null)
			{
				continue;
			}
			if (perms.getNodeAccessPermission(n, pDataset).isHidden())
			{
				hiddenFields.add(s);
			}
			else
			{
				nodes.put(s, n);
			}
		}
		// the records the user may see; on each, the fields hidden by a record-dependent rule
		final BitSet visible = new BitSet(pIndex.size());
		final Map<Integer, Set<String>> rowHidden = new HashMap<Integer, Set<String>>();
		final Request request = pTable.createRequest();
		request.setSession(pSession);
		final RequestResult result = request.execute();
		try
		{
			for (Adaptation r; (r = result.nextAdaptation()) != null;)
			{
				final Integer row = pIndex.pos.get(r.getOccurrencePrimaryKey().format());
				if (row == null)
				{
					continue;
				}
				visible.set(row.intValue());
				for (final Map.Entry<String, SchemaNode> e : nodes.entrySet())
				{
					if (perms.getNodeAccessPermission(e.getValue(), r).isHidden())
					{
						rowHidden.computeIfAbsent(row, k -> new HashSet<String>()).add(e.getKey());
					}
				}
			}
		}
		finally
		{
			result.close();
		}
		// counts over linked rows: again over the rows the user may see when they do not see them all
		final Map<String, int[]> counts = new HashMap<String, int[]>();
		final Set<String> hiddenTables = new HashSet<String>();
		for (final Index.Children c : pIndex.children)
		{
			final AdaptationTable t = pDataset.getTable(Path.parse(c.table));
			if (perms.getNodeAccessPermission(t.getTableNode(), pDataset).isHidden())
			{
				hiddenTables.add(c.derived);
				continue;
			}
			final Set<String> seen = new HashSet<String>();
			final Request cr = t.createRequest();
			cr.setSession(pSession);
			final RequestResult rr = cr.execute();
			try
			{
				for (Adaptation r; (r = rr.nextAdaptation()) != null;)
				{
					seen.add(r.getOccurrencePrimaryKey().format());
				}
			}
			finally
			{
				rr.close();
			}
			if (seen.size() < c.keys.length)
			{
				final int[] n = new int[pIndex.size()];
				for (int i = 0; i < c.keys.length; i++)
				{
					if (c.counted[i] && seen.contains(c.keys[i]))
					{
						n[c.parent[i]]++;
					}
				}
				counts.put(c.derived, n);
			}
		}
		for (final Map.Entry<String, JsonNode> e : pSpec.derived.entrySet())
		{
			final AdaptationTable t = IndexBuilder.linkedTable(pDataset, pTable, e.getValue());
			if (t != null && "lookup".equals(e.getValue().path("type").asText()) && perms.getNodeAccessPermission(t.getTableNode(), pDataset).isHidden())
			{
				hiddenTables.add(e.getKey());
			}
		}
		final View view = new View(pIndex, pSpec, visible, hiddenFields, rowHidden, counts, hiddenTables);
		LoggingCategory.getKernel().debug("[layout-designer] explorer view of " + pSession.getUserReference().getUserId() + ": " + view.visibleCount + "/"
			+ pIndex.size() + " records, " + hiddenFields.size() + " hidden fields, " + rowHidden.size() + " records with hidden fields, "
			+ (System.currentTimeMillis() - start) + " ms");
		return view;
	}

	/** The field sources whose permission matters: those of the index and those the card reads. */
	private static Set<String> fieldSources(final Index pIndex, final Spec pSpec)
	{
		final Set<String> out = new LinkedHashSet<String>();
		for (final String s : pIndex.slots.keySet())
		{
			if (!s.startsWith(Spec.DERIVED))
			{
				out.add(s);
			}
		}
		return out;
	}

	static SchemaNode node(final SchemaNode pRoot, final String pPath)
	{
		try
		{
			return pPath == null || pPath.isEmpty() || pPath.startsWith(Spec.DERIVED) ? null : pRoot.getNode(LayoutPaths.relative(pPath));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** Drops every cached index and view of an explorer (after a save). */
	public static void forget(final int pExplorer)
	{
		final String mark = "|" + pExplorer + "|";
		Views.INDEXES.keySet().removeIf(k -> k.contains(mark));
		Views.VIEWS.keySet().removeIf(k -> k.contains(mark));
	}
}
