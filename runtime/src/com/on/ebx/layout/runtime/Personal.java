package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A user's own arrangement of a form, laid over what {@link Audience} gives them. It is a set of preferences, never a
 * copy of the layout, so that a change made later in the designer still reaches everyone:
 *
 * <pre>
 * {"tabs": ["t2", "t0"],         order of the tabs (tabs not listed keep their place)
 *  "defaultTab": "t2",           opened first
 *  "hiddenTabs": ["t5"],
 *  "hidden": ["b12"],            blocks not shown
 *  "collapsed": ["b7"],          blocks folded under their title
 *  "pinned": ["b3", "b9"]}       blocks moved, in this order, to a first tab "My essentials"
 * </pre>
 *
 * The layout allows it with {@code "personal": true}; a tab or block with {@code "locked": true} can be neither hidden
 * nor moved. While a record is created nothing is hidden or folded (every field may be needed). A preference naming a
 * tab or block that no longer exists is ignored.
 */
public final class Personal
{
	public static final String MINE = "_mine";

	private Personal()
	{
	}

	public static boolean allowed(final JsonNode pLayout)
	{
		return pLayout != null && pLayout.path("personal").asBoolean(false);
	}

	/** Applies the preferences to a resolved layout, in place. */
	public static void apply(final ObjectNode pResolved, final JsonNode pPrefs, final boolean pCreating)
	{
		if (!Personal.allowed(pResolved) || pPrefs == null || !pPrefs.isObject())
		{
			return;
		}
		final ArrayNode tabs = (ArrayNode) pResolved.path("tabs");
		final Set<String> hidden = pCreating ? new HashSet<String>() : Personal.set(pPrefs.get("hidden"));
		final Set<String> collapsed = pCreating ? new HashSet<String>() : Personal.set(pPrefs.get("collapsed"));
		final Set<String> hiddenTabs = pCreating ? new HashSet<String>() : Personal.set(pPrefs.get("hiddenTabs"));
		final List<String> pinOrder = Personal.list(pPrefs.get("pinned"));
		final Map<String, ObjectNode> pinned = new LinkedHashMap<String, ObjectNode>();
		for (final JsonNode t : tabs)
		{
			for (final JsonNode r : t.path("rows"))
			{
				for (final JsonNode col : r.path("blocks"))
				{
					final ArrayNode c = (ArrayNode) col;
					for (int k = c.size() - 1; k >= 0; k--)
					{
						final ObjectNode b = (ObjectNode) c.get(k);
						final String id = b.path("id").asText("");
						final boolean locked = b.path("locked").asBoolean(false);
						if (id.isEmpty())
						{
							continue;
						}
						if (!locked && pinOrder.contains(id))
						{
							pinned.put(id, b);
							c.remove(k);
						}
						else if (!locked && hidden.contains(id))
						{
							c.remove(k);
						}
						else if (collapsed.contains(id))
						{
							b.put("_collapsed", true);
						}
					}
				}
				Audience.tidy((ObjectNode) r);
			}
		}
		// tabs: drop the hidden and the emptied ones, then the user's order
		final List<ObjectNode> list = new ArrayList<ObjectNode>();
		for (final JsonNode t : tabs)
		{
			final ObjectNode tab = (ObjectNode) t;
			final ArrayNode rows = (ArrayNode) tab.path("rows");
			final boolean hadRows = rows.size() > 0;
			for (int i = rows.size() - 1; i >= 0; i--)
			{
				if (rows.get(i).path("blocks").size() == 0)
				{
					rows.remove(i);
				}
			}
			final boolean locked = tab.path("locked").asBoolean(false);
			if (!locked && hiddenTabs.contains(tab.path("id").asText()))
			{
				continue;
			}
			if (rows.size() == 0 && hadRows)
			{
				continue;
			}
			list.add(tab);
		}
		final List<ObjectNode> ordered = Personal.order(list, Personal.list(pPrefs.get("tabs")));
		final String first = pPrefs.path("defaultTab").asText("");
		boolean firstMoved = false;
		for (int i = 0; i < ordered.size(); i++)
		{
			if (ordered.get(i).path("id").asText().equals(first))
			{
				ordered.add(0, ordered.remove(i));
				firstMoved = true;
				break;
			}
		}
		if (!pinned.isEmpty())
		{
			final ObjectNode mine = LayoutStore.json().createObjectNode();
			mine.put("id", Personal.MINE);
			final ObjectNode label = mine.putObject("label");
			label.put("en-US", RuntimeText.get("myEssentials", java.util.Locale.US));
			label.put("fr-FR", RuntimeText.get("myEssentials", java.util.Locale.FRANCE));
			label.put("it-IT", RuntimeText.get("myEssentials", java.util.Locale.ITALY));
			final ObjectNode row = mine.putArray("rows").addObject();
			row.put("columns", 1);
			final ArrayNode col = row.putArray("blocks").addArray();
			for (final String id : pinOrder)
			{
				if (pinned.containsKey(id))
				{
					col.add(pinned.get(id));
				}
			}
			// "My essentials" opens first, unless the user chose another tab to open first
			ordered.add(firstMoved ? Math.min(1, ordered.size()) : 0, mine);
		}
		tabs.removeAll();
		for (final ObjectNode t : ordered)
		{
			tabs.add(t);
		}
		if (tabs.size() == 0)
		{
			final ObjectNode empty = tabs.addObject();
			empty.put("id", "t0");
			empty.putArray("rows");
		}
	}

	/** The listed tabs take, in the listed order, the places those same tabs had; the others keep theirs. */
	static List<ObjectNode> order(final List<ObjectNode> pTabs, final List<String> pOrder)
	{
		final List<ObjectNode> out = new ArrayList<ObjectNode>(pTabs);
		final List<Integer> slots = new ArrayList<Integer>();
		final List<ObjectNode> moved = new ArrayList<ObjectNode>();
		for (final String id : pOrder)
		{
			for (int i = 0; i < pTabs.size(); i++)
			{
				if (pTabs.get(i).path("id").asText().equals(id) && !moved.contains(pTabs.get(i)))
				{
					moved.add(pTabs.get(i));
					slots.add(Integer.valueOf(i));
				}
			}
		}
		java.util.Collections.sort(slots);
		for (int k = 0; k < slots.size(); k++)
		{
			out.set(slots.get(k).intValue(), moved.get(k));
		}
		return out;
	}

	private static Set<String> set(final JsonNode pArray)
	{
		return new HashSet<String>(Personal.list(pArray));
	}

	private static List<String> list(final JsonNode pArray)
	{
		final List<String> out = new ArrayList<String>();
		if (pArray != null && pArray.isArray())
		{
			for (final JsonNode x : pArray)
			{
				if (!x.asText("").isEmpty())
				{
					out.add(x.asText());
				}
			}
		}
		return out;
	}
}
