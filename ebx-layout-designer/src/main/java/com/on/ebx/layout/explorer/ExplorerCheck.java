package com.on.ebx.layout.explorer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;

/**
 * What an explorer's JSON refers to that this table does not have, and what EBX could not evaluate (1.21.0, for the MCP
 * endpoint). The page itself never refuses a stored explorer: it drops, with a line in {@code kernel.log}, a facet, flag,
 * tile or card part it cannot serve. This check says so before the explorer is saved, element by element.
 * <p>
 * Errors: a path the table (or the linked table) does not define, a computed value or facet that does not exist, a
 * duplicate id, a condition EBX cannot evaluate (the engine would drop it: a media sub-field, a value computed in Java).
 * Warnings: a workflow not published, an unknown widget or key
 * value the page ignores.
 */
public final class ExplorerCheck
{
	/** One finding: {@code where} names the element ({@code facet country}, {@code card.lines[2]}…). */
	public static final class Issue
	{
		public final boolean error;
		public final String where;
		public final String text;

		Issue(final boolean pError, final String pWhere, final String pText)
		{
			this.error = pError;
			this.where = pWhere;
			this.text = pText;
		}
	}

	static final Set<String> DERIVED_TYPES = new HashSet<String>(Arrays.asList("count", "lookup", "sameValue", "firstOf", "case", "test", "suspects", "provided", "stateAt"));
	static final Set<String> FACET_KINDS = new HashSet<String>(Arrays.asList("field", "flags", "ranges", "tree", "favorites"));
	static final Set<String> FACET_WIDGETS = new HashSet<String>(Arrays.asList("checks", "chips", "select"));
	static final Set<String> TONES = new HashSet<String>(Arrays.asList("ok", "warn", "bad", "info", "accent", "muted"));
	static final Set<String> PART_WIDGETS = new HashSet<String>(Arrays.asList("text", "bold", "mono", "copy", "tag", "link", "pill", "dot", "country", "num",
		"money", "bar", "ring", "level", "stars", "alert", "date", "relative", "flags"));
	static final Set<String> CARD_LAYOUTS = new HashSet<String>(Arrays.asList("vertical", "horizontal", "compact"));
	static final Set<String> OPERATORS = new HashSet<String>(Arrays.asList("in", "notIn", "filled", "empty", "gt", "lt", "ge", "le", "eq", "ne", "days"));

	private final Adaptation dataset;
	private final AdaptationTable table;
	private final JsonNode root;
	private final Model model;
	private final Set<String> published;
	private final List<Issue> out = new ArrayList<Issue>();

	private ExplorerCheck(final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pRoot, final Locale pLocale, final Set<String> pPublished)
	{
		this.dataset = pDataset;
		this.table = pTable;
		this.root = pRoot;
		this.model = new Model(pDataset, pTable, pRoot, pLocale);
		this.published = pPublished;
	}

	/**
	 * @param pPublished names of the published workflows, null when they cannot be read (workflows are then not checked)
	 */
	public static List<Issue> check(final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pRoot, final Locale pLocale, final Set<String> pPublished)
	{
		if (pRoot == null || !pRoot.isObject())
		{
			final List<Issue> out = new ArrayList<Issue>();
			out.add(new Issue(true, "", "The explorer must be a JSON object."));
			return out;
		}
		final ExplorerCheck c = new ExplorerCheck(pDataset, pTable, pRoot, pLocale, pPublished);
		c.run();
		return c.out;
	}

	private void error(final String pWhere, final String pText)
	{
		this.out.add(new Issue(true, pWhere, pText));
	}

	private void warning(final String pWhere, final String pText)
	{
		this.out.add(new Issue(false, pWhere, pText));
	}

	private void run()
	{
		if (!"ebx-layout-designer/explorer".equals(this.root.path("format").asText("")))
		{
			this.warning("format", "\"format\" should be \"ebx-layout-designer/explorer\".");
		}
		final JsonNode derived = this.root.path("derived");
		if (derived.isObject())
		{
			derived.fields().forEachRemaining(e -> this.derived(e.getKey(), e.getValue()));
		}
		final JsonNode variants = this.root.path("variants");
		if (!variants.isArray() || variants.size() == 0)
		{
			this.error("variants", "At least one variant is needed: \"variants\": [{\"id\": \"std\", \"layout\": {…}}].");
			return;
		}
		final Set<String> variantIds = new HashSet<String>();
		for (int i = 0; i < variants.size(); i++)
		{
			final JsonNode v = variants.get(i);
			final String id = v.path("id").asText("");
			final String where = "variant " + (id.isEmpty() ? "#" + i : id);
			if (!id.isEmpty() && !variantIds.add(id))
			{
				this.error(where, "Duplicate variant id.");
			}
			if (!v.path("layout").isObject())
			{
				this.error(where, "A variant needs a \"layout\" object.");
				continue;
			}
			this.layout(where, v.get("layout"), variants.size() > 1);
		}
	}

	// ----- layout of a variant ---------------------------------------------------------------------------------------

	private void layout(final String pVariant, final JsonNode pLayout, final boolean pSeveral)
	{
		final String pre = pSeveral ? pVariant + ", " : "";
		for (final JsonNode s : pLayout.path("search").path("fields"))
		{
			this.source(pre + "search", s.asText(""), false);
		}
		final Set<String> facetIds = new HashSet<String>();
		final Set<String> flagFacets = new HashSet<String>();
		final Set<String> flagIds = new HashSet<String>();
		for (final JsonNode f : pLayout.path("facets"))
		{
			final String id = f.path("id").asText("");
			final String where = pre + "facet " + (id.isEmpty() ? "?" : id);
			if (id.isEmpty())
			{
				this.error(where, "A facet needs an \"id\".");
			}
			else if (!facetIds.add(id))
			{
				this.error(where, "Duplicate facet id.");
			}
			final String kind = f.path("kind").asText("field");
			if (!ExplorerCheck.FACET_KINDS.contains(kind))
			{
				this.error(where, "Unknown kind \"" + kind + "\" (field, flags, ranges, tree, favorites).");
				continue;
			}
			if (f.has("widget") && !ExplorerCheck.FACET_WIDGETS.contains(f.path("widget").asText()))
			{
				this.warning(where, "Unknown widget \"" + f.path("widget").asText() + "\" (checks, chips, select).");
			}
			if ("flags".equals(kind))
			{
				flagFacets.add(id);
				if (f.path("flags").size() == 0)
				{
					this.error(where, "A flags facet needs \"flags\": [{\"id\", \"label\", \"tone\", \"when\"}].");
				}
				for (final JsonNode fl : f.path("flags"))
				{
					final String fid = fl.path("id").asText("");
					final String fw = where + ", flag " + (fid.isEmpty() ? "?" : fid);
					if (fid.isEmpty())
					{
						this.error(fw, "A flag needs an \"id\".");
					}
					else if (!flagIds.add(fid))
					{
						this.error(fw, "Duplicate flag id (tiles name flags by id).");
					}
					this.tone(fw, fl);
					this.condition(fw, fl.get("when"), true);
				}
				continue;
			}
			if ("favorites".equals(kind))
			{
				continue;
			}
			final String src = Spec.source(f);
			if (src == null)
			{
				this.error(where, "A " + kind + " facet needs a \"field\" (a path, or derived:<name>).");
				continue;
			}
			this.source(where, src, true);
			if ("ranges".equals(kind) && f.path("ranges").size() == 0)
			{
				this.error(where, "A ranges facet needs \"ranges\": [{\"code\", \"label\", \"min\", \"max\"}].");
			}
			if ("tree".equals(kind))
			{
				this.treeParent(where, src, f.path("parent").asText(""));
			}
		}
		final Set<String> tileIds = new HashSet<String>();
		for (final JsonNode t : pLayout.path("tiles"))
		{
			final String id = t.path("id").asText("");
			final String where = pre + "tile " + (id.isEmpty() ? "?" : id);
			if (id.isEmpty())
			{
				this.error(where, "A tile needs an \"id\".");
			}
			else if (!tileIds.add(id))
			{
				this.error(where, "Duplicate tile id.");
			}
			this.tone(where, t);
			if (t.hasNonNull("flag"))
			{
				if (!flagIds.contains(t.get("flag").asText()))
				{
					this.error(where, "No flag \"" + t.get("flag").asText() + "\" in the flags facets of this variant.");
				}
			}
			else if (t.path("set").isObject())
			{
				t.get("set").fieldNames().forEachRemaining(k -> {
					if (!facetIds.contains(k))
					{
						this.error(where, "\"set\" names facet \"" + k + "\", which this variant does not have.");
					}
				});
			}
			else if (t.has("when"))
			{
				this.condition(where, t.get("when"), true);
			}
			else
			{
				this.warning(where, "A tile without \"when\", \"flag\" or \"set\" counts every record.");
			}
		}
		int i = 0;
		for (final JsonNode s : pLayout.path("sorts"))
		{
			final String where = pre + "sorts[" + i++ + "]";
			final String src = Spec.source(s);
			if (src == null)
			{
				this.error(where, "A sort needs a \"field\" or a \"derived\".");
			}
			else
			{
				this.source(where, src, true);
			}
		}
		if (pLayout.path("group").isObject())
		{
			final String src = Spec.source(pLayout.get("group"));
			if (src == null)
			{
				this.error(pre + "group", "\"group\" needs a \"field\" or a \"derived\".");
			}
			else
			{
				this.source(pre + "group", src, true);
			}
		}
		if (pLayout.path("asOf").isObject())
		{
			final JsonNode a = pLayout.get("asOf");
			this.field(pre + "asOf.from", a.path("from").asText(""));
			if (!a.path("to").asText("").isEmpty())
			{
				this.field(pre + "asOf.to", a.path("to").asText(""));
			}
			for (final String k : new String[] { "from", "to" })
			{
				final Model.Src s = this.model.src(a.path(k).asText(""));
				if (s != null && !"date".equals(s.type) && !"datetime".equals(s.type))
				{
					this.error(pre + "asOf." + k, "\"" + k + "\" must be a date field.");
				}
			}
		}
		this.header(pre + "header", pLayout.path("header"));
		this.card(pre + "card", pLayout.path("card"), flagFacets);
	}

	private void header(final String pWhere, final JsonNode pHeader)
	{
		int i = 0;
		for (final JsonNode l : pHeader.path("links"))
		{
			final String where = pWhere + ".links[" + i++ + "]";
			final String url = l.path("url").asText("").trim();
			if (!url.isEmpty())
			{
				if (!(url.startsWith("/") && !url.startsWith("//") || url.startsWith("https://")))
				{
					this.error(where, "A link address must be of this server (/…) or https://…; the page drops it otherwise.");
				}
				continue;
			}
			final String table = l.path("table").asText("").trim();
			if (!table.isEmpty() && this.table(table) == null)
			{
				this.error(where, "No table \"" + table + "\" in this dataset.");
			}
		}
	}

	private void card(final String pWhere, final JsonNode pCard, final Set<String> pFlagFacets)
	{
		if (!pCard.isObject())
		{
			return;
		}
		if (pCard.has("layout") && !ExplorerCheck.CARD_LAYOUTS.contains(pCard.path("layout").asText()))
		{
			this.warning(pWhere + ".layout", "Unknown card layout (vertical, horizontal, compact).");
		}
		for (final JsonNode l : pCard.path("layouts"))
		{
			if (!ExplorerCheck.CARD_LAYOUTS.contains(l.asText()))
			{
				this.warning(pWhere + ".layouts", "Unknown card layout \"" + l.asText() + "\".");
			}
		}
		final JsonNode title = pCard.get("title");
		if (title != null && title.isTextual())
		{
			this.field(pWhere + ".title", title.asText());
		}
		else if (title != null && title.isObject())
		{
			this.source(pWhere + ".title", Spec.source(title), false);
		}
		for (final String one : new String[] { "image", "pill", "gauge" })
		{
			final JsonNode n = pCard.path(one);
			if (n.isObject())
			{
				final String src = Spec.source(n);
				if (src == null)
				{
					this.error(pWhere + "." + one, "\"" + one + "\" needs a \"field\" or a \"derived\".");
				}
				else
				{
					this.source(pWhere + "." + one, src, false);
				}
			}
		}
		for (final String list : new String[] { "overline", "subtitle", "lines", "tags", "kpis" })
		{
			int i = 0;
			for (final JsonNode p : pCard.path(list))
			{
				final String where = pWhere + "." + list + "[" + i++ + "]";
				if (p.hasNonNull("flags"))
				{
					if (!pFlagFacets.contains(p.get("flags").asText()))
					{
						this.error(where, "No flags facet \"" + p.get("flags").asText() + "\" in this variant.");
					}
					continue;
				}
				final String src = Spec.source(p);
				if (src == null)
				{
					this.error(where, "A card part needs a \"field\", a \"derived\" or a \"flags\".");
					continue;
				}
				this.source(where, src, false);
				if (p.has("widget") && !ExplorerCheck.PART_WIDGETS.contains(p.path("widget").asText()))
				{
					this.warning(where, "Unknown widget \"" + p.path("widget").asText() + "\": shown as text.");
				}
			}
		}
		int i = 0;
		for (final JsonNode a : pCard.path("actions"))
		{
			this.action(pWhere + ".actions[" + i++ + "]", a);
		}
	}

	private void action(final String pWhere, final JsonNode pAction)
	{
		if (pAction.has("cond"))
		{
			this.condition(pWhere, pAction.get("cond"), false);
		}
		if (pAction.hasNonNull("workflow"))
		{
			final String pub = pAction.get("workflow").asText();
			if (this.published != null && !this.published.contains(pub))
			{
				this.warning(pWhere, "Workflow publication \"" + pub + "\" is not published on this instance: the button will not show.");
			}
			if (!pAction.hasNonNull("var"))
			{
				this.error(pWhere, "A workflow action needs \"var\": the data context variable that receives the record's XPath.");
			}
			return;
		}
		if (pAction.hasNonNull("service"))
		{
			return;
		}
		final String action = pAction.path("action").asText("");
		switch (action)
		{
		case "open":
		case "favorite":
			return;
		case "linked":
			this.linked(pWhere, pAction);
			if (pAction.hasNonNull("count") && !this.root.path("derived").has(pAction.get("count").asText()))
			{
				this.error(pWhere, "\"count\" names computed value \"" + pAction.get("count").asText() + "\", which \"derived\" does not define.");
			}
			return;
		default:
			this.error(pWhere, "Unknown action: use {\"action\": \"open\" | \"favorite\" | \"linked\"}, {\"workflow\", \"var\"} or {\"service\"}.");
		}
	}

	// ----- computed values -------------------------------------------------------------------------------------------

	private void derived(final String pName, final JsonNode pDef)
	{
		final String pWhere = "derived " + pName;
		final String type = pDef.path("type").asText("");
		if (!ExplorerCheck.DERIVED_TYPES.contains(type))
		{
			this.error(pWhere, "Unknown type \"" + type + "\" (count, lookup, sameValue, firstOf, case, test, suspects, provided, stateAt).");
			return;
		}
		switch (type)
		{
		case "count":
		case "lookup":
		{
			final AdaptationTable linked = this.linked(pWhere, pDef);
			if (linked != null)
			{
				final ExplorerCheck on = this.on(linked);
				if ("lookup".equals(type))
				{
					on.field(pWhere + ".field", pDef.path("field").asText(""));
					if (pDef.has("prefer"))
					{
						on.condition(pWhere + ".prefer", pDef.get("prefer"), false);
					}
				}
				if (pDef.has("where"))
				{
					on.condition(pWhere + ".where", pDef.get("where"), false);
				}
				this.out.addAll(on.out);
			}
			break;
		}
		case "sameValue":
			this.field(pWhere + ".field", pDef.path("field").asText(""));
			break;
		case "firstOf":
		{
			int i = 0;
			for (final JsonNode it : pDef.path("items"))
			{
				final String w = pWhere + ".items[" + i++ + "]";
				this.source(w, Spec.source(it), false);
				if (it.has("when"))
				{
					this.condition(w, it.get("when"), false);
				}
			}
			break;
		}
		case "case":
		{
			int i = 0;
			for (final JsonNode c : pDef.path("cases"))
			{
				this.condition(pWhere + ".cases[" + i++ + "]", c.get("when"), false);
			}
			break;
		}
		case "test":
			this.condition(pWhere, pDef.get("when"), false);
			break;
		case "stateAt":
		{
			this.field(pWhere + ".link", pDef.path("link").asText(""));
			AdaptationTable states = null;
			try
			{
				states = this.dataset.getTable(com.orchestranetworks.schema.Path.parse(pDef.path("states").asText("")));
			}
			catch (final RuntimeException ex)
			{
				states = null;
			}
			if (states == null)
			{
				this.error(pWhere + ".states", "\"states\" must be a table of the dataset (/root/…).");
			}
			else
			{
				final ExplorerCheck on = this.on(states);
				for (final String k : new String[] { "identity", "from", "field" })
				{
					on.field(pWhere + "." + k, pDef.path(k).asText(""));
				}
				if (!pDef.path("to").asText("").isEmpty())
				{
					on.field(pWhere + ".to", pDef.path("to").asText(""));
				}
				this.out.addAll(on.out);
			}
			break;
		}
		case "suspects":
			if (this.model.mdsTable == null)
			{
				this.warning(pWhere, "No Match and Merge metadataset for this table: \"suspects\" is always empty.");
			}
			break;
		default:
			break;
		}
		if (!"provided".equals(type) && !"suspects".equals(type) && !"stateAt".equals(type) && this.model.src(Spec.DERIVED + pName) == null)
		{
			this.error(pWhere, "EBX cannot compute this value as written.");
		}
	}

	/** The linked table of a count, lookup or linked action ({@code association}, or {@code table} + {@code fk}), null when it does not resolve. */
	private AdaptationTable linked(final String pWhere, final JsonNode pDef)
	{
		if (pDef.hasNonNull("association"))
		{
			final SchemaNode n = this.node(pDef.get("association").asText());
			if (n == null || !n.isAssociationNode())
			{
				this.error(pWhere, "\"" + pDef.get("association").asText() + "\" is not an association of this table.");
				return null;
			}
			try
			{
				return n.getAssociationLink().getTargetTable(this.dataset);
			}
			catch (final RuntimeException ex)
			{
				this.warning(pWhere, "The association's table is in another dataset: its fields are not checked.");
				return null;
			}
		}
		final String tablePath = pDef.path("table").asText("");
		if (tablePath.isEmpty())
		{
			this.error(pWhere, "Name the linked records: \"association\", or \"table\" + \"fk\".");
			return null;
		}
		final AdaptationTable t = this.table(tablePath);
		if (t == null)
		{
			this.error(pWhere, "No table \"" + tablePath + "\" in this dataset.");
			return null;
		}
		final SchemaNode fk = ExplorerCheck.node(t.getTableOccurrenceRootNode(), pDef.path("fk").asText(""));
		if (fk == null || fk.getFacetOnTableReference() == null)
		{
			this.error(pWhere, "\"fk\" must be a foreign key field of " + tablePath + " pointing to this table.");
			return null;
		}
		return t;
	}

	private ExplorerCheck on(final AdaptationTable pTable)
	{
		return new ExplorerCheck(this.dataset, pTable, this.root, this.model.locale, this.published);
	}

	// ----- conditions, sources and paths ------------------------------------------------------------------------------

	/**
	 * @param pEngine true for a condition the page filters on (flag, tile): EBX must be able to evaluate it, or the engine
	 *            drops the element
	 */
	private void condition(final String pWhere, final JsonNode pCond, final boolean pEngine)
	{
		if (pCond == null || pCond.isNull())
		{
			return;
		}
		final int before = this.out.size();
		this.conditionShape(pWhere, pCond);
		if (pEngine && this.out.size() == before)
		{
			final Model.Pred p;
			try
			{
				p = this.model.pred(pCond);
			}
			catch (final RuntimeException ex)
			{
				this.error(pWhere, "EBX cannot evaluate this condition: " + ex.getMessage());
				return;
			}
			if (p == null)
			{
				this.error(pWhere, "EBX cannot evaluate this condition as one query, and the page would drop it. Usual causes: an \"any\" mixing a "
					+ "Match and Merge state with a counted or looked-up value (EBX would evaluate that OR record by record: write two flags), a sub-field "
					+ "of a media field (test the media field itself, /Logo rather than /Logo/attachment), a \"provided\" value (computed in "
					+ "Java), or a field EBX cannot filter on. Use another field, or split the condition into two flags.");
			}
		}
	}

	private void conditionShape(final String pWhere, final JsonNode pCond)
	{
		if (!pCond.isObject())
		{
			this.error(pWhere, "A condition is an object: {\"field\": \"/p\", \"in\": [...]}, {\"all\": [...]}, {\"any\": [...]}, {\"not\": {...}}.");
			return;
		}
		if (pCond.size() == 0)
		{
			return;
		}
		if (pCond.has("all") || pCond.has("any"))
		{
			for (final JsonNode c : pCond.path(pCond.has("all") ? "all" : "any"))
			{
				this.conditionShape(pWhere, c);
			}
			return;
		}
		if (pCond.has("not"))
		{
			this.conditionShape(pWhere, pCond.get("not"));
			return;
		}
		final String src = pCond.hasNonNull("derived") ? Spec.DERIVED + pCond.get("derived").asText() : pCond.path("field").asText("");
		if (src.isEmpty())
		{
			this.error(pWhere, "A condition needs \"field\" or \"derived\".");
			return;
		}
		this.source(pWhere, src, false);
		boolean op = false;
		for (final String o : ExplorerCheck.OPERATORS)
		{
			op |= pCond.has(o);
		}
		if (!op)
		{
			this.error(pWhere, "A condition needs an operator: in, notIn, filled, empty, gt, lt, ge, le, eq, ne, days.");
		}
	}

	/**
	 * A source (a path, or {@code derived:<name>}) of this table.
	 *
	 * @param pEngine true when EBX must filter, count or sort on it (facet, sort, group)
	 */
	private void source(final String pWhere, final String pSource, final boolean pEngine)
	{
		if (pSource == null || pSource.isEmpty())
		{
			this.error(pWhere, "Empty field.");
			return;
		}
		if (pSource.startsWith(Spec.DERIVED))
		{
			final String name = pSource.substring(Spec.DERIVED.length());
			if (!this.root.path("derived").has(name))
			{
				this.error(pWhere, "Computed value \"" + name + "\" is not defined in \"derived\".");
			}
			else if (pEngine && "provided".equals(this.root.path("derived").path(name).path("type").asText()))
			{
				this.error(pWhere, "A \"provided\" value (computed in Java) shows on cards only: EBX cannot filter, count or sort on it.");
			}
			return;
		}
		if (!this.field(pWhere, pSource))
		{
			return;
		}
		if (pEngine)
		{
			final Model.Src s = this.model.src(pSource);
			if (s == null || s.sql == null && s.xpath == null)
			{
				this.error(pWhere, "EBX cannot filter, count or sort on " + pSource + " (a group, an association or a list of values?).");
			}
		}
	}

	/** True when the path is a field of the table; reports it otherwise. */
	private boolean field(final String pWhere, final String pPath)
	{
		if (pPath == null || !pPath.startsWith("/"))
		{
			this.error(pWhere, "\"" + pPath + "\" is not a path: write it from the record, with a leading slash (/Address/City).");
			return false;
		}
		final SchemaNode n = this.node(pPath);
		if (n == null)
		{
			this.error(pWhere, "No field " + pPath + " in " + this.table.getTablePath().format() + ".");
			return false;
		}
		return true;
	}

	private void tone(final String pWhere, final JsonNode pElement)
	{
		if (pElement.has("tone") && !pElement.path("tone").asText("").isEmpty() && !ExplorerCheck.TONES.contains(pElement.path("tone").asText()))
		{
			this.warning(pWhere, "Unknown tone \"" + pElement.path("tone").asText() + "\" (ok, warn, bad, info, accent, muted).");
		}
	}

	private void treeParent(final String pWhere, final String pSource, final String pParent)
	{
		if (pParent.isEmpty())
		{
			this.error(pWhere, "A tree facet needs \"parent\": the path of the parent in the table its field points to.");
			return;
		}
		final SchemaNode n = pSource.startsWith(Spec.DERIVED) ? null : this.node(pSource);
		if (n == null)
		{
			return;
		}
		if (n.getFacetOnTableReference() == null)
		{
			this.error(pWhere, "A tree facet's field must be a foreign key.");
			return;
		}
		try
		{
			final SchemaNode target = n.getFacetOnTableReference().getTableNode().getTableOccurrenceRootNode();
			if (ExplorerCheck.node(target, pParent) == null)
			{
				this.error(pWhere, "No field " + pParent + " in the table " + pSource + " points to.");
			}
		}
		catch (final RuntimeException ex)
		{
			// a target in another dataset: not checked
		}
	}

	private SchemaNode node(final String pPath)
	{
		return ExplorerCheck.node(this.table.getTableOccurrenceRootNode(), pPath);
	}

	static SchemaNode node(final SchemaNode pRoot, final String pStored)
	{
		try
		{
			final Path p = LayoutPaths.relative(pStored);
			return p == null ? null : pRoot.getNode(p);
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private AdaptationTable table(final String pPath)
	{
		try
		{
			return this.dataset.getTable(Path.parse(pPath));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** Every path this explorer reads, for a quick summary (the sources EBX reads, all variants). */
	public static Set<String> sources(final JsonNode pRoot)
	{
		return new LinkedHashSet<String>(Spec.indexSources(pRoot));
	}
}
