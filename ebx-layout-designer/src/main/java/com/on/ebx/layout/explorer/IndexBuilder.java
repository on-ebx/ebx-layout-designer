package com.on.ebx.layout.explorer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.on.ebx.layout.runtime.ExplorerValues;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationName;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.ValueContext;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.schema.info.AssociationLink;
import com.orchestranetworks.schema.info.AssociationLinkByTableRefInverse;
import com.orchestranetworks.service.LoggingCategory;

/**
 * Builds an {@link Index}: one streaming read of the table, one of each linked table a computed value reads (grouped
 * by the foreign key, never one request per record), one of each tree facet's target table. EBX Java API only.
 */
final class IndexBuilder
{
	private final Adaptation dataset;
	private final AdaptationTable table;
	private final JsonNode root;
	private final Locale locale;
	private final SchemaNode rootNode;

	private final List<String> sources = new ArrayList<String>();
	private final Map<String, Integer> slots = new LinkedHashMap<String, Integer>();
	private final Map<String, SchemaNode> nodes = new HashMap<String, SchemaNode>();
	private final Map<String, Map<String, String>> labels = new HashMap<String, Map<String, String>>();
	private final Set<String> modelOrder = new HashSet<String>();
	private final Set<String> searched = new HashSet<String>();
	private final List<Index.Children> children = new ArrayList<Index.Children>();
	/** A record's value context, for the labels of enumeration values no record holds. */
	private ValueContext anyContext;

	IndexBuilder(final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pRoot, final Locale pLocale)
	{
		this.dataset = pDataset;
		this.table = pTable;
		this.root = pRoot;
		this.locale = pLocale;
		this.rootNode = pTable.getTableOccurrenceRootNode();
	}

	/** The tables an index reads, for its change token: the table, the linked tables of its computed values, the tree targets. */
	static List<AdaptationTable> readTables(final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pRoot)
	{
		final List<AdaptationTable> out = new ArrayList<AdaptationTable>();
		out.add(pTable);
		pRoot.path("derived").fields().forEachRemaining(e -> {
			final AdaptationTable t = IndexBuilder.linkedTable(pDataset, pTable, e.getValue());
			if (t != null)
			{
				out.add(t);
			}
		});
		final Adaptation mds = IndexBuilder.metadataset(pDataset, pTable);
		if (mds != null)
		{
			for (final String t : new String[] { "/root/Suspected", "/root/MatchingResult" })
			{
				if (mds.getSchemaNode().getNode(Path.parse(t)) != null)
				{
					out.add(mds.getTable(Path.parse(t)));
				}
			}
		}
		for (final JsonNode v : pRoot.path("variants"))
		{
			for (final JsonNode f : v.path("layout").path("facets"))
			{
				if ("tree".equals(f.path("kind").asText()))
				{
					final AdaptationTable t = IndexBuilder.treeTable(pDataset, pTable, f.path("field").asText(""));
					if (t != null)
					{
						out.add(t);
					}
				}
			}
		}
		return out;
	}

	Index build(final long pToken)
	{
		final long start = System.currentTimeMillis();
		for (final String s : Spec.indexSources(this.root))
		{
			if (s.startsWith(Spec.DERIVED))
			{
				if (this.root.path("derived").has(s.substring(Spec.DERIVED.length())))
				{
					this.slot(s);
				}
				continue;
			}
			final SchemaNode n = this.node(s);
			if (n != null && n.isTerminalValue() && !n.isAssociationNode())
			{
				this.nodes.put(s, n);
				this.slot(s);
			}
		}
		for (final JsonNode v : this.root.path("variants"))
		{
			for (final JsonNode s : v.path("layout").path("search").path("fields"))
			{
				this.searched.add(s.asText());
			}
		}
		final Map<String, JsonNode> provided = new LinkedHashMap<String, JsonNode>();
		this.root.path("derived").fields().forEachRemaining(e -> {
			if ("provided".equals(e.getValue().path("type").asText()) && this.slots.containsKey(Spec.DERIVED + e.getKey()))
			{
				provided.put(e.getKey(), e.getValue());
			}
		});

		final List<String> keys = new ArrayList<String>();
		final List<String> recordLabels = new ArrayList<String>();
		final List<Object[]> rows = new ArrayList<Object[]>();
		final int width = this.slots.size();
		IndexBuilder.each(this.table, r -> {
			final Object[] row = new Object[width];
			ValueContext vc = null;
			for (final Map.Entry<String, SchemaNode> e : this.nodes.entrySet())
			{
				Object v;
				try
				{
					v = r.get(e.getValue().getPathInAdaptation());
				}
				catch (final RuntimeException ex)
				{
					v = null;
				}
				v = IndexBuilder.compact(v);
				row[this.slots.get(e.getKey()).intValue()] = v;
				if (v != null && IndexBuilder.labelled(e.getValue()))
				{
					final Map<String, String> m = this.labels.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<String, String>());
					for (final Object o : v instanceof Collection ? (Collection<?>) v : java.util.Collections.singletonList(v))
					{
						final String k = Cond.key(o);
						if (k != null && !m.containsKey(k))
						{
							if (vc == null)
							{
								vc = r.createValueContext();
							}
							m.put(k, IndexBuilder.display(e.getValue(), o, vc, this.locale));
						}
					}
				}
			}
			for (final Map.Entry<String, JsonNode> p : provided.entrySet())
			{
				final ExplorerValues.Provider provider = ExplorerValues.get(p.getValue().path("name").asText(""));
				Object v = null;
				if (provider != null)
				{
					try
					{
						v = IndexBuilder.compact(provider.value(r));
					}
					catch (final RuntimeException ex)
					{
						// a provider's failure leaves the value empty
					}
				}
				row[this.slots.get(Spec.DERIVED + p.getKey()).intValue()] = v;
			}
			if (this.anyContext == null)
			{
				this.anyContext = vc != null ? vc : r.createValueContext();
			}
			keys.add(r.getOccurrencePrimaryKey().format());
			recordLabels.add(r.getLabel(this.locale));
			rows.add(row);
		});
		final Object[][] vals = rows.toArray(new Object[rows.size()][]);
		final String[] keyArray = keys.toArray(new String[keys.size()]);
		final Map<String, Integer> pos = new HashMap<String, Integer>();
		for (int i = 0; i < keyArray.length; i++)
		{
			pos.put(keyArray[i], Integer.valueOf(i));
		}
		this.orderEnumerations();

		// computed values: those reading other tables first, then those reading this row, in their declared order
		for (final Map.Entry<String, JsonNode> e : IndexBuilder.entries(this.root.path("derived")))
		{
			final Integer slot = this.slots.get(Spec.DERIVED + e.getKey());
			if (slot == null)
			{
				continue;
			}
			final String type = e.getValue().path("type").asText("");
			if ("count".equals(type) || "lookup".equals(type))
			{
				this.linked(e.getKey(), e.getValue(), vals, pos, slot.intValue());
			}
			else if ("suspects".equals(type))
			{
				this.suspects(vals, pos, slot.intValue());
			}
			else if ("sameValue".equals(type))
			{
				final Integer f = this.slots.get(e.getValue().path("field").asText(""));
				final Map<String, Integer> counts = new HashMap<String, Integer>();
				if (f != null)
				{
					for (final Object[] row : vals)
					{
						final String k = Cond.key(row[f.intValue()]);
						if (k != null && !k.isEmpty())
						{
							counts.merge(k, Integer.valueOf(1), Integer::sum);
						}
					}
				}
				for (final Object[] row : vals)
				{
					final String k = f == null ? null : Cond.key(row[f.intValue()]);
					row[slot.intValue()] = Integer.valueOf(k == null || k.isEmpty() ? 0 : counts.get(k).intValue());
				}
			}
		}
		for (final Map.Entry<String, JsonNode> e : IndexBuilder.entries(this.root.path("derived")))
		{
			final Integer slot = this.slots.get(Spec.DERIVED + e.getKey());
			final String type = e.getValue().path("type").asText("");
			if (slot == null || !("firstOf".equals(type) || "case".equals(type) || "test".equals(type)))
			{
				continue;
			}
			for (final Object[] row : vals)
			{
				row[slot.intValue()] = this.rowValue(e.getValue(), row);
			}
		}

		final Map<String, Index.Tree> trees = this.trees();
		final String[][] text = new String[vals.length][];
		for (int i = 0; i < vals.length; i++)
		{
			text[i] = new String[width];
			for (final Map.Entry<String, Integer> s : this.slots.entrySet())
			{
				if (this.searched.contains(s.getKey()))
				{
					text[i][s.getValue().intValue()] = Index.searchText(null, s.getKey(), vals[i][s.getValue().intValue()], this.labels);
				}
			}
		}
		final long ms = System.currentTimeMillis() - start;
		LoggingCategory.getKernel().info("[layout-designer] explorer index of " + this.table.getTablePath().format() + " in "
			+ this.dataset.getAdaptationName().getStringName() + ": " + vals.length + " records, " + width + " values each, " + ms + " ms");
		return new Index(keyArray, recordLabels.toArray(new String[recordLabels.size()]), vals, this.slots, this.labels, this.modelOrder, trees,
			this.children, text, pToken, ms);
	}

	private void slot(final String pSource)
	{
		if (!this.slots.containsKey(pSource))
		{
			this.slots.put(pSource, Integer.valueOf(this.slots.size()));
			this.sources.add(pSource);
		}
	}

	private SchemaNode node(final String pPath)
	{
		try
		{
			return pPath == null || pPath.isEmpty() ? null : this.rootNode.getNode(LayoutPaths.relative(pPath));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** Lists, enumerations, foreign keys and booleans carry labels; a free text or a number is its own label. */
	static boolean labelled(final SchemaNode pNode)
	{
		return pNode.getFacetOnTableReference() != null || pNode.getFacetEnumeration() != null || pNode.getMaxOccurs() > 1
			|| Boolean.class.equals(pNode.getOccurrencesJavaClass());
	}

	static String display(final SchemaNode pNode, final Object pValue, final ValueContext pContext, final Locale pLocale)
	{
		try
		{
			final String s = pNode.displayOccurrence(pValue, true, pContext, pLocale);
			return s == null ? Cond.key(pValue) : s;
		}
		catch (final RuntimeException ex)
		{
			return Cond.key(pValue);
		}
	}

	/** What the index keeps of a value: numbers as Double, dates as Date, lists as lists of these, the rest as text. */
	static Object compact(final Object pValue)
	{
		if (pValue == null)
		{
			return null;
		}
		if (pValue instanceof BigDecimal || pValue instanceof Number)
		{
			return Double.valueOf(((Number) pValue).doubleValue());
		}
		if (pValue instanceof Boolean || pValue instanceof Date)
		{
			return pValue;
		}
		if (pValue instanceof Collection)
		{
			final List<Object> l = new ArrayList<Object>();
			for (final Object o : (Collection<?>) pValue)
			{
				final Object c = IndexBuilder.compact(o);
				if (c != null)
				{
					l.add(c);
				}
			}
			return l;
		}
		final String s = String.valueOf(pValue);
		return s.isEmpty() ? null : s.intern();
	}

	/** A static enumeration keeps the model's order (values never met included, so a short list shows them at zero). */
	private void orderEnumerations()
	{
		for (final Map.Entry<String, SchemaNode> e : this.nodes.entrySet())
		{
			final SchemaNode n = e.getValue();
			try
			{
				if (n.getFacetEnumeration() == null || n.getFacetOnTableReference() != null || !n.getFacetEnumeration().isEnumerationStatic())
				{
					continue;
				}
				final List<?> values = n.getFacetEnumeration().getValues();
				if (values == null || values.size() > 50)
				{
					continue;
				}
				final Map<String, String> met = this.labels.getOrDefault(e.getKey(), new LinkedHashMap<String, String>());
				final Map<String, String> ordered = new LinkedHashMap<String, String>();
				for (final Object v : values)
				{
					final String k = Cond.key(v);
					ordered.put(k, met.containsKey(k) ? met.get(k) : IndexBuilder.display(n, v, this.anyContext, this.locale));
				}
				for (final Map.Entry<String, String> m : met.entrySet())
				{
					ordered.putIfAbsent(m.getKey(), m.getValue());
				}
				this.labels.put(e.getKey(), ordered);
				this.modelOrder.add(e.getKey());
			}
			catch (final RuntimeException ex)
			{
				// an enumeration that cannot be listed: values in the order met
			}
		}
		for (final Map.Entry<String, SchemaNode> e : this.nodes.entrySet())
		{
			if (Boolean.class.equals(e.getValue().getOccurrencesJavaClass()) && !this.modelOrder.contains(e.getKey()))
			{
				final Map<String, String> met = this.labels.getOrDefault(e.getKey(), new LinkedHashMap<String, String>());
				final Map<String, String> ordered = new LinkedHashMap<String, String>();
				for (final Boolean b : new Boolean[] { Boolean.TRUE, Boolean.FALSE })
				{
					ordered.put(b.toString(), met.containsKey(b.toString()) ? met.get(b.toString()) : IndexBuilder.display(e.getValue(), b, this.anyContext, this.locale));
				}
				this.labels.put(e.getKey(), ordered);
				this.modelOrder.add(e.getKey());
			}
		}
	}

	/** {@code count} and {@code lookup}: one read of the linked table, grouped by the foreign key pointing to the record. */
	private void linked(final String pName, final JsonNode pDef, final Object[][] pVals, final Map<String, Integer> pPos, final int pSlot)
	{
		final boolean count = "count".equals(pDef.path("type").asText());
		final AdaptationTable child = IndexBuilder.linkedTable(this.dataset, this.table, pDef);
		final Path fk = IndexBuilder.linkedKey(this.table, pDef, child);
		if (child == null || fk == null)
		{
			LoggingCategory.getKernel().warn("[layout-designer] explorer value " + pName + ": linked table or foreign key not found");
			return;
		}
		final JsonNode where = pDef.get(count ? "where" : "prefer");
		final SchemaNode childRoot = child.getTableOccurrenceRootNode();
		final Path field = count ? null : LayoutPaths.relative(pDef.path("field").asText(""));
		final List<String> childKeys = new ArrayList<String>();
		final List<Integer> parents = new ArrayList<Integer>();
		final List<Boolean> counted = new ArrayList<Boolean>();
		final int[] n = new int[pVals.length];
		final boolean[] preferred = new boolean[pVals.length];
		IndexBuilder.each(child, c -> {
			final Integer row = pPos.get(Cond.key(IndexBuilder.safeGet(c, fk)));
			if (row == null)
			{
				return;
			}
			final boolean ok = Cond.test(where, IndexBuilder.recordLookup(c, childRoot));
			if (count)
			{
				childKeys.add(c.getOccurrencePrimaryKey().format());
				parents.add(row);
				counted.add(Boolean.valueOf(ok));
				if (ok)
				{
					n[row.intValue()]++;
				}
			}
			else if (field != null && (pVals[row.intValue()][pSlot] == null || ok && !preferred[row.intValue()]))
			{
				final Object v = IndexBuilder.compact(IndexBuilder.safeGet(c, field));
				if (v != null)
				{
					pVals[row.intValue()][pSlot] = v;
					preferred[row.intValue()] = ok && where != null;
				}
			}
		});
		if (count)
		{
			for (int i = 0; i < pVals.length; i++)
			{
				pVals[i][pSlot] = Double.valueOf(n[i]);
			}
			final int[] parent = new int[parents.size()];
			final boolean[] c = new boolean[counted.size()];
			for (int i = 0; i < parent.length; i++)
			{
				parent[i] = parents.get(i).intValue();
				c[i] = counted.get(i).booleanValue();
			}
			this.children.add(new Index.Children(pName, child.getTablePath().format(), childKeys.toArray(new String[childKeys.size()]), parent, c));
		}
	}

	static AdaptationTable linkedTable(final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pDef)
	{
		final String type = pDef.path("type").asText("");
		if (!"count".equals(type) && !"lookup".equals(type))
		{
			return null;
		}
		try
		{
			if (pDef.hasNonNull("association"))
			{
				final SchemaNode a = pTable.getTableOccurrenceRootNode().getNode(LayoutPaths.relative(pDef.get("association").asText()));
				final AssociationLink link = a == null || !a.isAssociationNode() ? null : a.getAssociationLink();
				return link instanceof AssociationLinkByTableRefInverse ? link.getTargetTable(pDataset) : null;
			}
			if (pDef.hasNonNull("table"))
			{
				return pDataset.getTable(Path.parse(pDef.get("table").asText()));
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] explorer: linked table of " + pDef + " not found: " + ex.getMessage());
		}
		return null;
	}

	static Path linkedKey(final AdaptationTable pTable, final JsonNode pDef, final AdaptationTable pChild)
	{
		try
		{
			if (pDef.hasNonNull("association"))
			{
				final SchemaNode a = pTable.getTableOccurrenceRootNode().getNode(LayoutPaths.relative(pDef.get("association").asText()));
				final AssociationLink link = a == null ? null : a.getAssociationLink();
				if (link instanceof AssociationLinkByTableRefInverse)
				{
					// an absolute path in the schema (/root/Child/fk): keep the part inside the linked table's record
					String p = ((AssociationLinkByTableRefInverse) link).getFieldToSourcePath().format();
					final String table = pChild == null ? null : pChild.getTablePath().format();
					if (table != null && p.startsWith(table + "/"))
					{
						p = p.substring(table.length());
					}
					return LayoutPaths.relative(p.startsWith("./") ? p : p.startsWith("/") ? p : "/" + p);
				}
				return null;
			}
			return pDef.hasNonNull("fk") ? LayoutPaths.relative(pDef.get("fk").asText()) : null;
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private static Object safeGet(final Adaptation pRecord, final Path pPath)
	{
		try
		{
			return pRecord.get(pPath);
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** Values of a record for a condition: its fields, by path. */
	static Cond.Lookup recordLookup(final Adaptation pRecord, final SchemaNode pRoot)
	{
		return new Cond.Lookup()
		{
			@Override
			public Object field(final String pPath)
			{
				try
				{
					return IndexBuilder.compact(pRecord.get(LayoutPaths.relative(pPath)));
				}
				catch (final RuntimeException ex)
				{
					return null;
				}
			}

			@Override
			public Object derived(final String pName)
			{
				return null;
			}
		};
	}

	/** Suspect pairs of the Match and Merge metadataset, both directions (6.3 table Suspected, older MatchingResult with lastResult = 1). */
	private void suspects(final Object[][] pVals, final Map<String, Integer> pPos, final int pSlot)
	{
		final Adaptation mds = IndexBuilder.metadataset(this.dataset, this.table);
		final Map<Integer, Set<String>> pairs = new HashMap<Integer, Set<String>>();
		if (mds != null)
		{
			for (final String t : new String[] { "/root/Suspected", "/root/MatchingResult" })
			{
				final Path p = Path.parse(t);
				if (mds.getSchemaNode().getNode(p) == null)
				{
					continue;
				}
				final boolean result = t.endsWith("MatchingResult");
				IndexBuilder.each(mds.getTable(p), r -> {
					if (result && !"1".equals(String.valueOf(IndexBuilder.safeGet(r, Path.parse("./lastResult")))))
					{
						return;
					}
					final String a = Cond.key(IndexBuilder.safeGet(r, Path.parse("./sourceId")));
					final String b = Cond.key(IndexBuilder.safeGet(r, Path.parse("./targetId")));
					if (a == null || b == null || a.equals(b))
					{
						return;
					}
					final Integer ra = pPos.get(a), rb = pPos.get(b);
					if (ra != null)
					{
						pairs.computeIfAbsent(ra, k -> new TreeSet<String>()).add(b);
					}
					if (rb != null)
					{
						pairs.computeIfAbsent(rb, k -> new TreeSet<String>()).add(a);
					}
				});
			}
		}
		for (int i = 0; i < pVals.length; i++)
		{
			final Set<String> s = pairs.get(Integer.valueOf(i));
			pVals[i][pSlot] = s == null ? new ArrayList<Object>() : new ArrayList<Object>(s);
		}
	}

	static Adaptation metadataset(final Adaptation pDataset, final AdaptationTable pTable)
	{
		try
		{
			final String table = pTable.getTablePath().getLastStep().format();
			return pDataset.getHome().findAdaptationOrNull(AdaptationName.forName(pDataset.getAdaptationName().getStringName() + "_" + table + "_MDS"));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** {@code firstOf}, {@code case} and {@code test}, from the row's values (computed values declared before included). */
	private Object rowValue(final JsonNode pDef, final Object[] pRow)
	{
		final Cond.Lookup l = new Cond.Lookup()
		{
			@Override
			public Object field(final String pPath)
			{
				final Integer s = IndexBuilder.this.slots.get(pPath);
				return s == null ? null : pRow[s.intValue()];
			}

			@Override
			public Object derived(final String pName)
			{
				final Integer s = IndexBuilder.this.slots.get(Spec.DERIVED + pName);
				return s == null ? null : pRow[s.intValue()];
			}
		};
		switch (pDef.path("type").asText(""))
		{
		case "test":
			return Boolean.valueOf(Cond.test(pDef.get("when"), l));
		case "case":
			for (final JsonNode c : pDef.path("cases"))
			{
				if (Cond.test(c.get("when"), l))
				{
					return IndexBuilder.scalar(c.get("value"));
				}
			}
			return IndexBuilder.scalar(pDef.get("else"));
		default:
			for (final JsonNode it : pDef.path("items"))
			{
				final String src = Spec.source(it);
				final Object v = src == null ? null : src.startsWith(Spec.DERIVED) ? l.derived(src.substring(Spec.DERIVED.length())) : l.field(src);
				if (!Cond.isEmpty(v) && Cond.test(it.get("when"), l))
				{
					final String k = Cond.key(v instanceof Collection ? ((Collection<?>) v).iterator().next() : v);
					final Map<String, String> m = src == null ? null : this.labels.get(src);
					final String shown = m != null && m.containsKey(k) ? m.get(k) : k;
					return it.path("prefix").asText("") + shown;
				}
			}
			return null;
		}
	}

	private static Object scalar(final JsonNode pValue)
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

	private Map<String, Index.Tree> trees()
	{
		final Map<String, Index.Tree> out = new HashMap<String, Index.Tree>();
		for (final JsonNode v : this.root.path("variants"))
		{
			for (final JsonNode f : v.path("layout").path("facets"))
			{
				final String field = f.path("field").asText("");
				if (!"tree".equals(f.path("kind").asText()) || out.containsKey(field))
				{
					continue;
				}
				final AdaptationTable t = IndexBuilder.treeTable(this.dataset, this.table, field);
				final Path parent = LayoutPaths.relative(f.path("parent").asText(""));
				if (t == null || parent == null)
				{
					continue;
				}
				final Index.Tree tree = new Index.Tree();
				IndexBuilder.each(t, r -> {
					final String k = r.getOccurrencePrimaryKey().format();
					tree.label.put(k, r.getLabel(this.locale));
					final String p = Cond.key(IndexBuilder.safeGet(r, parent));
					if (p != null && !p.isEmpty())
					{
						tree.parent.put(k, p);
					}
				});
				out.put(field, tree);
			}
		}
		return out;
	}

	static AdaptationTable treeTable(final Adaptation pDataset, final AdaptationTable pTable, final String pField)
	{
		try
		{
			final SchemaNode n = pTable.getTableOccurrenceRootNode().getNode(LayoutPaths.relative(pField));
			if (n == null || n.getFacetOnTableReference() == null)
			{
				return null;
			}
			final Path target = n.getFacetOnTableReference().getTablePath();
			return n.getFacetOnTableReference().getContainerReference() == null ? pDataset.getTable(target) : null;
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private static List<Map.Entry<String, JsonNode>> entries(final JsonNode pObject)
	{
		final List<Map.Entry<String, JsonNode>> out = new ArrayList<Map.Entry<String, JsonNode>>();
		pObject.fields().forEachRemaining(out::add);
		return out;
	}

	/** Streams a table without keeping its records. */
	static void each(final AdaptationTable pTable, final Consumer<Adaptation> pAction)
	{
		final RequestResult result = pTable.createRequest().execute();
		try
		{
			for (Adaptation r; (r = result.nextAdaptation()) != null;)
			{
				pAction.accept(r);
			}
		}
		finally
		{
			result.close();
		}
	}
}
