package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Who sees what. A layout holds the standard form ({@code tabs}) and, optionally, variants for some roles
 * ({@code variants}, in priority order: the first one whose {@code roles} the user has wins). Any tab, block or field may
 * carry {@code roles} (visible only for these roles) and {@code roRoles} (read only for these roles); without them it is
 * visible and editable by everyone, as before. {@link #resolve} turns a layout into the one a given user sees: the
 * chosen variant's tabs, what the user may not see removed, "read only for my role" turned into {@code ro}. This only
 * arranges the form: EBX permissions still decide what a user may read or change.
 *
 * <p>
 * Pure JSON work, so that it can be tested without a server.
 */
public final class Audience
{
	/** The standard form's variant id. */
	public static final String STANDARD = "std";

	/** Whether the current user holds one of the given role names ({@code administrator} for the built-in one). */
	public interface Roles
	{
		boolean any(JsonNode pRoleNames);
	}

	private Audience()
	{
	}

	/** The variant this user gets: the first whose roles match, else null (the standard form). */
	public static JsonNode variant(final JsonNode pLayout, final Roles pRoles)
	{
		for (final JsonNode v : pLayout.path("variants"))
		{
			if (v.path("roles").size() > 0 && pRoles.any(v.get("roles")) && v.path("tabs").size() > 0)
			{
				return v;
			}
		}
		return null;
	}

	public static String variantId(final JsonNode pVariant)
	{
		return pVariant == null ? Audience.STANDARD : pVariant.path("id").asText("v");
	}

	/**
	 * The layout as this user sees it. {@code pSeesAll}: role restrictions are not applied (administrators, unless the
	 * layout says otherwise); the variant choice still is. The result carries {@code variant} (the chosen id) and every
	 * tab an {@code id}.
	 */
	public static ObjectNode resolve(final JsonNode pLayout, final Roles pRoles, final boolean pSeesAll)
	{
		final ObjectNode out = LayoutStore.json().createObjectNode();
		pLayout.fields().forEachRemaining(e -> {
			if (!"tabs".equals(e.getKey()) && !"variants".equals(e.getKey()))
			{
				out.set(e.getKey(), e.getValue());
			}
		});
		final JsonNode variant = Audience.variant(pLayout, pRoles);
		final JsonNode tabs = variant == null ? pLayout.path("tabs") : variant.path("tabs");
		out.put("variant", Audience.variantId(variant));
		final ArrayNode outTabs = out.putArray("tabs");
		for (int i = 0; i < tabs.size(); i++)
		{
			final JsonNode t = tabs.get(i);
			if (!pSeesAll && !Audience.visible(t, pRoles))
			{
				continue;
			}
			final ObjectNode tab = ((ObjectNode) t).deepCopy();
			if (!tab.hasNonNull("id"))
			{
				tab.put("id", "t" + i);
			}
			final boolean tabRo = !pSeesAll && Audience.readOnly(t, pRoles);
			final ArrayNode rows = tab.putArray("rows");
			for (final JsonNode r : t.path("rows"))
			{
				final ObjectNode row = ((ObjectNode) r).deepCopy();
				final ArrayNode cols = row.putArray("blocks");
				for (final JsonNode col : r.path("blocks"))
				{
					final ArrayNode outCol = cols.addArray();
					for (final JsonNode b : col)
					{
						if (!pSeesAll && !Audience.visible(b, pRoles))
						{
							continue;
						}
						final ObjectNode block = ((ObjectNode) b).deepCopy();
						if (tabRo || !pSeesAll && Audience.readOnly(b, pRoles))
						{
							block.put("ro", true);
						}
						if (b.has("fields") && !pSeesAll)
						{
							final ArrayNode fields = block.putArray("fields");
							for (final JsonNode f : b.path("fields"))
							{
								if (!Audience.visible(f, pRoles))
								{
									continue;
								}
								final ObjectNode field = ((ObjectNode) f).deepCopy();
								if (Audience.readOnly(f, pRoles))
								{
									field.put("ro", true);
								}
								fields.add(field);
							}
							if (fields.size() == 0 && b.path("fields").size() > 0)
							{
								continue; // every field of the panel is hidden for this user: so is the panel
							}
						}
						outCol.add(block);
					}
				}
				Audience.tidy(row);
				if (row.path("blocks").size() > 0)
				{
					rows.add(row);
				}
			}
			if (rows.size() > 0 || t.path("rows").size() == 0)
			{
				outTabs.add(tab);
			}
		}
		if (outTabs.size() == 0 && tabs.size() > 0)
		{
			// nothing left for this user: an empty first tab, so that the form says so instead of falling back on EBX's
			final ObjectNode empty = outTabs.addObject();
			empty.put("id", "t0");
			empty.set("label", tabs.get(0).get("label"));
			empty.putArray("rows");
		}
		return out;
	}

	/** A row whose columns lost their blocks: an empty column is dropped, an empty row has no blocks left. */
	static void tidy(final ObjectNode pRow)
	{
		final ArrayNode cols = (ArrayNode) pRow.path("blocks");
		final List<JsonNode> kept = new ArrayList<JsonNode>();
		for (final JsonNode col : cols)
		{
			if (col.size() > 0)
			{
				kept.add(col);
			}
		}
		if (kept.size() == cols.size())
		{
			return;
		}
		cols.removeAll();
		for (final JsonNode col : kept)
		{
			cols.add(col);
		}
		if (kept.size() < 2)
		{
			pRow.put("columns", 1);
			pRow.remove("ratio");
		}
	}

	static boolean visible(final JsonNode pItem, final Roles pRoles)
	{
		final JsonNode roles = pItem.get("roles");
		return roles == null || !roles.isArray() || roles.size() == 0 || pRoles.any(roles);
	}

	static boolean readOnly(final JsonNode pItem, final Roles pRoles)
	{
		final JsonNode roles = pItem.get("roRoles");
		return roles != null && roles.isArray() && roles.size() > 0 && pRoles.any(roles);
	}

	/** Tabs and blocks of a resolved layout, for the personalization screen: tab id → [label, block ids…]. */
	public static Map<String, List<JsonNode>> blocksByTab(final JsonNode pResolved)
	{
		final Map<String, List<JsonNode>> out = new LinkedHashMap<String, List<JsonNode>>();
		for (final JsonNode t : pResolved.path("tabs"))
		{
			final List<JsonNode> blocks = new ArrayList<JsonNode>();
			for (final JsonNode r : t.path("rows"))
			{
				for (final JsonNode col : r.path("blocks"))
				{
					for (final JsonNode b : col)
					{
						blocks.add(b);
					}
				}
			}
			out.put(t.path("id").asText(), blocks);
		}
		return out;
	}
}
