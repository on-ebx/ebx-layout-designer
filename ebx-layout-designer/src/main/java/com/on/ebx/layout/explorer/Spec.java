package com.on.ebx.layout.explorer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One explorer as one user gets it: the variant chosen for their roles, without the facets, tiles, sorts, card parts and
 * actions their roles do not show ({@code docs/EXPLORER-FORMAT.md}). Pure JSON work; EBX permissions are applied after,
 * by {@link View}. A source is a field path ({@code /p}) or a computed value ({@code derived:name}).
 */
public final class Spec
{
	public static final String DERIVED = "derived:";

	public static final class Facet
	{
		public String id, kind, source, widget, parent;
		public JsonNode title;
		public boolean open = true;
		public int top = 10;
		public final List<Flag> flags = new ArrayList<Flag>();
		public final List<Range> ranges = new ArrayList<Range>();
	}

	public static final class Flag
	{
		public String id, tone;
		public JsonNode label, when;
	}

	public static final class Range
	{
		public String code;
		public JsonNode label;
		public Double min, max;
	}

	public static final class Tile
	{
		public String id, tone, flag;
		public JsonNode label, when;
		public final Map<String, Set<String>> set = new LinkedHashMap<String, Set<String>>();
	}

	public static final class Sort
	{
		public String source;
		public boolean desc;
		public JsonNode label;
	}

	/** The whole stored JSON, the variant used, and the variant's layout with what the user's roles hide removed. */
	public final JsonNode root;
	public final String variantId;
	public final ObjectNode layout;
	public final Map<String, JsonNode> derived = new LinkedHashMap<String, JsonNode>();
	public final List<Facet> facets = new ArrayList<Facet>();
	public final List<Tile> tiles = new ArrayList<Tile>();
	public final List<String> search = new ArrayList<String>();
	public final List<Sort> sorts = new ArrayList<Sort>();
	public final int pageSize;
	/** The records are shown in groups of this source's values, with a count per group (null: no groups). */
	public final Sort group;

	/**
	 * @param pRoles true when the user holds one of the role names of the given array (an empty array never reaches it)
	 * @param pSeesAll an administrator who sees every element whatever its roles
	 */
	public Spec(final JsonNode pRoot, final Predicate<JsonNode> pRoles, final boolean pSeesAll)
	{
		this.root = pRoot;
		final JsonNode variants = pRoot.path("variants");
		JsonNode chosen = variants.path(0);
		for (int i = 1; i < variants.size(); i++)
		{
			final JsonNode roles = variants.get(i).path("roles");
			if (roles.size() > 0 && pRoles.test(roles))
			{
				chosen = variants.get(i);
				break;
			}
		}
		this.variantId = chosen.path("id").asText("std");
		final ObjectNode layout = chosen.path("layout").isObject() ? ((ObjectNode) chosen.get("layout")).deepCopy() : com.on.ebx.layout.runtime.LayoutStore.json().createObjectNode();
		final Predicate<JsonNode> shown = el -> pSeesAll || Spec.forRoles(el, pRoles);
		Spec.keep(layout.path("facets"), shown);
		Spec.keep(layout.path("tiles"), shown);
		final JsonNode card = layout.path("card");
		for (final String list : new String[] { "overline", "subtitle", "lines", "tags", "kpis", "actions" })
		{
			Spec.keep(card.path(list), shown);
		}
		this.layout = layout;
		pRoot.path("derived").fields().forEachRemaining(e -> this.derived.put(e.getKey(), e.getValue()));

		for (final JsonNode f : layout.path("facets"))
		{
			final Facet x = new Facet();
			x.id = f.path("id").asText("");
			x.kind = f.path("kind").asText("field");
			x.source = Spec.source(f);
			x.widget = f.path("widget").asText("checks");
			x.title = f.get("title");
			x.open = f.path("open").asBoolean(true);
			x.top = Math.max(1, Math.min(50, f.path("top").asInt(10)));
			x.parent = f.path("parent").asText(null);
			for (final JsonNode fl : f.path("flags"))
			{
				final Flag g = new Flag();
				g.id = fl.path("id").asText("");
				g.tone = fl.path("tone").asText("warn");
				g.label = fl.get("label");
				g.when = fl.get("when");
				x.flags.add(g);
			}
			for (final JsonNode r : f.path("ranges"))
			{
				final Range g = new Range();
				g.code = r.path("code").asText("");
				g.label = r.get("label");
				g.min = r.hasNonNull("min") ? Double.valueOf(r.get("min").asDouble()) : null;
				g.max = r.hasNonNull("max") ? Double.valueOf(r.get("max").asDouble()) : null;
				x.ranges.add(g);
			}
			if (!x.id.isEmpty() && ("favorites".equals(x.kind) || "flags".equals(x.kind) || x.source != null))
			{
				this.facets.add(x);
			}
		}
		for (final JsonNode t : layout.path("tiles"))
		{
			final Tile x = new Tile();
			x.id = t.path("id").asText("");
			x.tone = t.path("tone").asText("");
			x.label = t.get("label");
			x.flag = t.path("flag").asText(null);
			x.when = t.get("when");
			t.path("set").fields().forEachRemaining(e -> {
				final Set<String> values = new TreeSet<String>();
				for (final JsonNode v : e.getValue())
				{
					values.add(v.asText());
				}
				x.set.put(e.getKey(), values);
			});
			if (!x.id.isEmpty())
			{
				this.tiles.add(x);
			}
		}
		for (final JsonNode s : layout.path("search").path("fields"))
		{
			this.search.add(s.asText());
		}
		for (final JsonNode s : layout.path("sorts"))
		{
			final Sort x = new Sort();
			x.source = Spec.source(s);
			x.desc = "desc".equals(s.path("dir").asText("asc"));
			x.label = s.get("label");
			if (x.source != null)
			{
				this.sorts.add(x);
			}
		}
		this.pageSize = Math.max(6, Math.min(96, layout.path("pageSize").asInt(48)));
		final JsonNode g = layout.path("group");
		final Sort group = new Sort();
		group.source = g.isObject() ? Spec.source(g) : null;
		group.desc = "desc".equals(g.path("dir").asText("asc"));
		group.label = g.get("label");
		this.group = group.source == null ? null : group;
	}

	static boolean forRoles(final JsonNode pElement, final Predicate<JsonNode> pRoles)
	{
		final JsonNode roles = pElement.get("roles");
		return roles == null || !roles.isArray() || roles.size() == 0 || pRoles.test(roles);
	}

	private static void keep(final JsonNode pArray, final Predicate<JsonNode> pShown)
	{
		if (!(pArray instanceof ArrayNode))
		{
			return;
		}
		final ArrayNode a = (ArrayNode) pArray;
		for (int i = a.size() - 1; i >= 0; i--)
		{
			if (!pShown.test(a.get(i)))
			{
				a.remove(i);
			}
		}
	}

	/** {@code field} or {@code derived} of an element, as a source; null when it has neither. */
	public static String source(final JsonNode pElement)
	{
		if (pElement.hasNonNull("derived"))
		{
			return Spec.DERIVED + pElement.get("derived").asText();
		}
		final String f = pElement.path("field").asText("");
		if (f.startsWith(Spec.DERIVED))
		{
			return f;
		}
		return f.isEmpty() ? null : f;
	}

	/** Every source the index must hold for this explorer, all variants included (so one index serves every user). */
	public static Set<String> indexSources(final JsonNode pRoot)
	{
		final Set<String> out = new LinkedHashSet<String>();
		final Set<String> fields = new LinkedHashSet<String>();
		final Set<String> derived = new LinkedHashSet<String>();
		for (final JsonNode v : pRoot.path("variants"))
		{
			final JsonNode l = v.path("layout");
			for (final JsonNode f : l.path("facets"))
			{
				Spec.add(Spec.source(f), fields, derived);
				for (final JsonNode fl : f.path("flags"))
				{
					Cond.reads(fl.get("when"), fields, derived);
				}
			}
			for (final JsonNode t : l.path("tiles"))
			{
				Cond.reads(t.get("when"), fields, derived);
			}
			for (final JsonNode s : l.path("search").path("fields"))
			{
				Spec.add(s.asText(), fields, derived);
			}
			for (final JsonNode s : l.path("sorts"))
			{
				Spec.add(Spec.source(s), fields, derived);
			}
			// a card reads its fields from the records of the page; its computed values come from the index
			final JsonNode card = l.path("card");
			for (final String list : new String[] { "overline", "subtitle", "lines", "tags", "kpis" })
			{
				for (final JsonNode p : card.path(list))
				{
					if (p.hasNonNull("derived"))
					{
						derived.add(p.get("derived").asText());
					}
				}
			}
			if (card.path("title").hasNonNull("derived"))
			{
				derived.add(card.path("title").get("derived").asText());
			}
			for (final String one : new String[] { "pill", "gauge" })
			{
				if (card.path(one).hasNonNull("derived"))
				{
					derived.add(card.path(one).get("derived").asText());
				}
			}
		}
		// computed values read fields, and may read other computed values
		final Set<String> seen = new LinkedHashSet<String>();
		boolean grew = true;
		while (grew)
		{
			grew = false;
			for (final String d : new ArrayList<String>(derived))
			{
				if (seen.add(d))
				{
					grew = true;
					Spec.derivedReads(pRoot.path("derived").path(d), fields, derived);
				}
			}
		}
		out.addAll(fields);
		for (final String d : pRoot.path("derived").isObject() ? Spec.names(pRoot.path("derived")) : new ArrayList<String>())
		{
			if (derived.contains(d))
			{
				out.add(Spec.DERIVED + d);
			}
		}
		return out;
	}

	private static List<String> names(final JsonNode pObject)
	{
		final List<String> out = new ArrayList<String>();
		pObject.fieldNames().forEachRemaining(out::add);
		return out;
	}

	private static void add(final String pSource, final Set<String> pFields, final Set<String> pDerived)
	{
		if (pSource == null || pSource.isEmpty())
		{
			return;
		}
		if (pSource.startsWith(Spec.DERIVED))
		{
			pDerived.add(pSource.substring(Spec.DERIVED.length()));
		}
		else
		{
			pFields.add(pSource);
		}
	}

	/** The fields of this table and the computed values a computed value reads (not those of a linked table). */
	public static void derivedReads(final JsonNode pDef, final Set<String> pFields, final Set<String> pDerived)
	{
		if (pDef == null || !pDef.isObject())
		{
			return;
		}
		switch (pDef.path("type").asText(""))
		{
		case "sameValue":
			Spec.add(pDef.path("field").asText(""), pFields, pDerived);
			break;
		case "firstOf":
			for (final JsonNode it : pDef.path("items"))
			{
				Spec.add(Spec.source(it), pFields, pDerived);
				Cond.reads(it.get("when"), pFields, pDerived);
			}
			break;
		case "case":
			for (final JsonNode c : pDef.path("cases"))
			{
				Cond.reads(c.get("when"), pFields, pDerived);
			}
			break;
		case "test":
			Cond.reads(pDef.get("when"), pFields, pDerived);
			break;
		default:
			break;
		}
	}
}
