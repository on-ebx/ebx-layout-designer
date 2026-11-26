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

/** Shared reads of the explorers' model: linked tables, the Match and Merge metadataset, labels, records' values. */
final class IndexBuilder
{

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
