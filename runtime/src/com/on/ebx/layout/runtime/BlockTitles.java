package com.on.ebx.layout.runtime;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.orchestranetworks.schema.SchemaNode;

/** The title a block shows: its own, else one derived from what it shows (an association's label, "Change history"…). */
public final class BlockTitles
{
	private BlockTitles()
	{
	}

	public static String of(final JsonNode pBlock, final Locale pLocale, final Locale pDefault, final SchemaNode pRoot)
	{
		// a hero's "title" is the path of the field shown as its title, not a text
		final String t = "hero".equals(pBlock.path("type").asText()) ? "" : LText.pick(pBlock.get("title"), pLocale, pDefault);
		if (!t.isEmpty())
		{
			return t;
		}
		final String type = pBlock.path("type").asText();
		if ("grid".equals(type) || "timeline".equals(type) || "contacts".equals(type) || "documents".equals(type) || "chart".equals(type) || "pivot".equals(type)
			|| "aggregates".equals(type) || "heatmap".equals(type) || "gantt".equals(type) || "compare".equals(type) || "coverage".equals(type) || ("stateTimeline".equals(type) && !pBlock.path("a").asText("").isEmpty()) || ("gallery".equals(type) && !pBlock.path("a").asText("").isEmpty()))
		{
			return BlockTitles.label(pRoot, pLocale, pBlock.path("a").asText());
		}
		if ("history".equals(type))
		{
			return RuntimeText.get("historyTitle", pLocale);
		}
		if ("workflowTasks".equals(type) || "sourceValues".equals(type) || "translations".equals(type) || "inheritance".equals(type))
		{
			return RuntimeText.get(type + "Title", pLocale);
		}
		if ("valueHistory".equals(type))
		{
			return RuntimeText.get("vhTitle", pLocale);
		}
		if ("asOfTree".equals(type) || "stateTimeline".equals(type) || "delta".equals(type))
		{
			return RuntimeText.get(type + "Title", pLocale);
		}
		if ("sitesMap".equals(type))
		{
			return RuntimeText.get("smTitle", pLocale);
		}
		if ("orgChart".equals(type))
		{
			return RuntimeText.get("orgTitle", pLocale);
		}
		if ("recordInfo".equals(type))
		{
			return RuntimeText.get("recordInfoTitle", pLocale);
		}
		if ("changes".equals(type))
		{
			return RuntimeText.get("changesTitle", pLocale);
		}
		if ("usedBy".equals(type))
		{
			return RuntimeText.get("usedByTitle", pLocale);
		}
		if ("linked".equals(type))
		{
			return BlockTitles.label(pRoot, pLocale, pBlock.path("fk").asText());
		}
		if ("stepper".equals(type))
		{
			return BlockTitles.label(pRoot, pLocale, pBlock.path("field").asText());
		}
		return "";
	}

	static String label(final SchemaNode pRoot, final Locale pLocale, final String pStoredPath)
	{
		final com.orchestranetworks.schema.Path p = LayoutPaths.relative(pStoredPath);
		final SchemaNode n = p == null || pRoot == null ? null : pRoot.getNode(p);
		return n == null ? (pStoredPath == null ? "" : pStoredPath) : n.getLabel(pLocale);
	}
}
