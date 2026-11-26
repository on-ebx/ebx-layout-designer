package com.on.ebx.layout.designer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.on.ebx.layout.runtime.LayoutStore;

/**
 * The layout a table starts from when nobody has drawn one: a first tab with one panel per group of the model (two
 * columns), its loose fields in a first panel, and one tab per association. Every text is the model's own label.
 */
final class DefaultLayout
{
	private DefaultLayout()
	{
	}

	static JsonNode build(final ArrayNode pFields, final ArrayNode pGroups, final ArrayNode pAssocs, final List<Locale> pLocales)
	{
		final ObjectNode layout = LayoutStore.json().createObjectNode();
		layout.put("format", "ebx-form-layout/1");
		final ArrayNode tabs = layout.putArray("tabs");
		final ObjectNode general = tabs.addObject();
		final ObjectNode label = general.putObject("label");
		for (final Locale l : pLocales)
		{
			label.put(l.toLanguageTag(), "fr".equals(l.getLanguage()) ? "Général" : "it".equals(l.getLanguage()) ? "Generale" : "General");
		}
		general.putArray("count");
		final ArrayNode rows = general.putArray("rows");

		// one panel per group, fields in model order
		final Map<String, ObjectNode> panels = new LinkedHashMap<String, ObjectNode>();
		int id = 0;
		final ObjectNode loose = DefaultLayout.panel(LayoutStore.json().createObjectNode(), "b" + (++id), label);
		for (final JsonNode f : pFields)
		{
			final String g = f.path("g").asText("");
			ObjectNode panel = g.isEmpty() ? loose : panels.get(g);
			if (panel == null)
			{
				JsonNode groupLabel = null;
				for (final JsonNode gr : pGroups)
				{
					if (gr.path("p").asText().equals(g))
					{
						groupLabel = gr.get("lab");
					}
				}
				panel = DefaultLayout.panel(LayoutStore.json().createObjectNode(), "b" + (++id), groupLabel);
				panels.put(g, panel);
			}
			panel.withArray("fields").addObject().put("p", f.path("p").asText());
		}
		final List<ObjectNode> all = new ArrayList<ObjectNode>();
		if (loose.withArray("fields").size() > 0)
		{
			all.add(loose);
		}
		all.addAll(panels.values());
		final ObjectNode row = rows.addObject();
		row.put("columns", all.size() > 1 ? 2 : 1);
		row.put("ratio", "11");
		final ArrayNode cols = row.putArray("blocks");
		final ArrayNode left = cols.addArray();
		final ArrayNode right = all.size() > 1 ? cols.addArray() : null;
		for (int i = 0; i < all.size(); i++)
		{
			(right != null && i % 2 == 1 ? right : left).add(all.get(i));
		}

		// one tab per association
		for (final JsonNode a : pAssocs)
		{
			final ObjectNode tab = tabs.addObject();
			tab.set("label", a.get("lab").deepCopy());
			tab.putArray("count").add(a.path("p").asText());
			final ObjectNode r = tab.putArray("rows").addObject();
			r.put("columns", 1);
			final ObjectNode grid = r.putArray("blocks").addArray().addObject();
			grid.put("id", "b" + (++id));
			grid.put("type", "grid");
			grid.put("a", a.path("p").asText());
			grid.put("style", "native");
		}
		return layout;
	}

	private static ObjectNode panel(final ObjectNode pTarget, final String pId, final JsonNode pTitle)
	{
		pTarget.put("id", pId);
		pTarget.put("type", "panel");
		if (pTitle != null)
		{
			pTarget.set("title", pTitle.deepCopy());
		}
		pTarget.putArray("fields");
		return pTarget;
	}
}
