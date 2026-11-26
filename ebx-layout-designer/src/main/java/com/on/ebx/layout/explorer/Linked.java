package com.on.ebx.layout.explorer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.orchestranetworks.schema.Path;

/** For the designer's preview: computed values of a few records, read the way the index reads them. */
public final class Linked
{
	private Linked()
	{
	}

	/** {@code count} of a computed value for the given record keys (one read of the linked table); null when it cannot be read. */
	public static Map<String, Integer> counts(final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pDef, final Set<String> pKeys)
	{
		final AdaptationTable child = IndexBuilder.linkedTable(pDataset, pTable, pDef);
		final Path fk = IndexBuilder.linkedKey(pTable, pDef, child);
		if (child == null || fk == null)
		{
			return null;
		}
		final Map<String, Integer> out = new HashMap<String, Integer>();
		IndexBuilder.each(child, c -> {
			Object v;
			try
			{
				v = c.get(fk);
			}
			catch (final RuntimeException ex)
			{
				v = null;
			}
			final String k = Cond.key(v);
			if (k != null && pKeys.contains(k) && Cond.test(pDef.get("where"), IndexBuilder.recordLookup(c, null)))
			{
				out.merge(k, Integer.valueOf(1), Integer::sum);
			}
		});
		return out;
	}

	/** True when the table has a Match and Merge metadataset next to it. */
	public static boolean hasSuspects(final Adaptation pDataset, final AdaptationTable pTable)
	{
		return IndexBuilder.metadataset(pDataset, pTable) != null;
	}

	/** Suspects of the given records (keys of the other records). */
	public static Map<String, List<String>> suspects(final Adaptation pDataset, final AdaptationTable pTable, final Set<String> pKeys)
	{
		final Map<String, List<String>> out = new HashMap<String, List<String>>();
		final Adaptation mds = IndexBuilder.metadataset(pDataset, pTable);
		if (mds == null)
		{
			return out;
		}
		for (final String t : new String[] { "/root/Suspected", "/root/MatchingResult" })
		{
			final Path p = Path.parse(t);
			if (mds.getSchemaNode().getNode(p) == null)
			{
				continue;
			}
			final boolean result = t.endsWith("MatchingResult");
			IndexBuilder.each(mds.getTable(p), r -> {
				if (result && !"1".equals(String.valueOf(r.get(Path.parse("./lastResult")))))
				{
					return;
				}
				final String a = Cond.key(r.get(Path.parse("./sourceId"))), b = Cond.key(r.get(Path.parse("./targetId")));
				if (a == null || b == null || a.equals(b))
				{
					return;
				}
				if (pKeys.contains(a) && !out.computeIfAbsent(a, k -> new ArrayList<String>()).contains(b))
				{
					out.get(a).add(b);
				}
				if (pKeys.contains(b) && !out.computeIfAbsent(b, k -> new ArrayList<String>()).contains(a))
				{
					out.get(b).add(a);
				}
			});
		}
		return out;
	}
}
