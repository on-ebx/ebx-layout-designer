package com.on.ebx.layout.runtime;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.RequestResult;
import com.onwbp.adaptation.XPathExpressionHelper;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.ui.UIHttpManagerComponent;
import com.orchestranetworks.ui.form.UIFormPaneWriter;

/**
 * Blocks for effective-dated models (1.24.0), where an entity (a fund, a contract, an organisation) is a stable identity
 * and its values live in successive states, each from an effective date to an end date: {@code asOfTree} draws the
 * hierarchy as it stands at a chosen date, {@code stateTimeline} the states of one entity on a time scale, and
 * {@code delta} a before/after report kept as JSON in a text field.
 */
final class EffectiveBlocks
{
	/** States read per level (one scan of the states table per level); a hierarchy at a date is a screen, not an extraction. */
	private static final int CAP = 2000;

	private EffectiveBlocks()
	{
	}

	private static String esc(final String s)
	{
		return LayoutPane.esc(s);
	}

	private static String t(final String pKey, final RenderContext rc)
	{
		return RuntimeText.get(pKey, rc.locale);
	}

	private static String note(final String pText)
	{
		return "<div class=\"ldf-more\">" + EffectiveBlocks.esc(pText) + "</div>";
	}

	/**
	 * 1.26.0: the attributes of a link that opens a record in EBX's own modal pop-up (a sub-session of this form, the
	 * same mechanism as the table views' preview button): the user stays on the form and comes back to it on close.
	 * Without a writer, or when the pop-up cannot be built, a plain link in a new tab.
	 */
	private static String open(final RenderContext rc, final UIFormPaneWriter w, final Adaptation r)
	{
		final String url = MoreBlocks.recordUrl(rc, r);
		String js = null;
		if (w != null)
		{
			try
			{
				final UIHttpManagerComponent wc = rc.context.createWebComponentForSubSession();
				wc.selectInstanceOrOccurrence(r);
				js = w.buildButtonPreview(wc).getJavaScriptCommand();
			}
			catch (final RuntimeException ex)
			{
				js = null;
			}
		}
		return js == null ? "href=\"" + EffectiveBlocks.esc(url) + "\" target=\"_blank\" rel=\"noopener\""
			: "href=\"" + EffectiveBlocks.esc(url) + "\" onclick=\"" + EffectiveBlocks.esc(js) + ";return false;\"";
	}

	private static String iso(final LocalDate d)
	{
		return d == null ? "" : d.toString();
	}

	private static String pct(final double f)
	{
		return String.format(java.util.Locale.ROOT, "%.3f%%", 100 * Math.max(0, Math.min(1, f)));
	}

	/** {@code ./fund = '79712'}: the predicate that finds the states whose foreign key holds this record's key. */
	private static String keyPredicate(final String pStoredPath, final Adaptation pTarget)
	{
		final Path p = LayoutPaths.relative(pStoredPath);
		return p.format() + " = " + XPathExpressionHelper.encodeLiteralStringWithDelimiters(pTarget.getOccurrencePrimaryKey().format());
	}

	/** The rows of a table matching a predicate, the ones the user may see, at most CAP. */
	private static List<Adaptation> select(final AdaptationTable pTable, final String pPredicate)
	{
		final List<Adaptation> out = new ArrayList<Adaptation>();
		try
		{
			final RequestResult rr = pTable.createRequestResult(pPredicate);
			try
			{
				for (Adaptation r = rr.nextAdaptation(); r != null && out.size() < EffectiveBlocks.CAP; r = rr.nextAdaptation())
				{
					if (Access.canSee(r))
					{
						out.add(r);
					}
				}
			}
			finally
			{
				rr.close();
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] states of " + pTable.getTablePath().format() + " not read (" + pPredicate + "): " + ex.getMessage());
		}
		return out;
	}

	private static Adaptation linked(final Adaptation pRow, final SchemaNode pFk)
	{
		try
		{
			final Adaptation r = pFk.getFacetOnTableReference().getLinkedRecord(pRow);
			return r != null && Access.canSee(r) ? r : null;
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private static SchemaNode fkNode(final SchemaNode pRoot, final String pStoredPath)
	{
		final Path p = LayoutPaths.relative(pStoredPath);
		final SchemaNode n = p == null ? null : pRoot.getNode(p);
		return n == null || n.getFacetOnTableReference() == null ? null : n;
	}

	private static String safeLabel(final Adaptation r, final java.util.Locale pLocale)
	{
		try
		{
			return r.getLabel(pLocale);
		}
		catch (final RuntimeException ex)
		{
			return r.getOccurrencePrimaryKey().format();
		}
	}

	// ---------------------------------------------------------------------------------------------------- asOfTree

	/** One state of an entity: its validity, what it shows. */
	private static final class State
	{
		final LocalDate from;
		final LocalDate to;
		final String label;
		final String chip;
		/** The attributes of the link that opens the state. */
		final String link;

		State(final LocalDate pFrom, final LocalDate pTo, final String pLabel, final String pChip, final String pLink)
		{
			this.from = pFrom;
			this.to = pTo;
			this.label = pLabel;
			this.chip = pChip;
			this.link = pLink;
		}
	}

	/** An entity of the tree with every state found for it; which one applies is decided in the browser from the date. */
	private static final class Node
	{
		final Adaptation identity;
		final String label;
		/** The attributes of the link that opens the entity. */
		final String link;
		final List<State> states = new ArrayList<State>();
		final List<Node> children = new ArrayList<Node>();

		Node(final Adaptation pIdentity, final String pLabel, final String pLink)
		{
			this.identity = pIdentity;
			this.label = pLabel;
			this.link = pLink;
		}
	}

	private static State state(final RenderContext rc, final UIFormPaneWriter w, final Adaptation r, final JsonNode cfg)
	{
		final String label = cfg.path("label").asText("");
		final String chip = cfg.path("chip").asText("");
		final String end = cfg.path("endDate").asText("");
		final String shown = label.isEmpty() ? "" : Values.display(r, label, rc.locale);
		return new State(Values.date(Values.raw(r, cfg.path("effectiveDate").asText(""))), end.isEmpty() ? null : Values.date(Values.raw(r, end)),
			shown.isEmpty() ? EffectiveBlocks.safeLabel(r, rc.locale) : shown, chip.isEmpty() ? "" : Values.display(r, chip, rc.locale), EffectiveBlocks.open(rc, w, r));
	}

	/**
	 * {@code levels[]}: one entry per level under the root, each a table of states ({@code states}) whose rows name
	 * their parent entity ({@code parent}, a foreign key) and their own entity ({@code identity}, a foreign key),
	 * with {@code effectiveDate}, {@code endDate}, {@code label}, {@code chip}. {@code root}: a foreign key of the record
	 * to the root entity (empty: the record itself). {@code selfStates} + {@code selfIdentity}…: the root's own states.
	 * {@code date}: a date field of the record giving the default date (empty: today).
	 */
	static String asOfTree(final RenderContext rc, final JsonNode b)
	{
		return EffectiveBlocks.asOfTree(rc, b, null);
	}

	static String asOfTree(final RenderContext rc, final JsonNode b, final UIFormPaneWriter w)
	{
		final JsonNode levels = b.path("levels");
		if (!levels.isArray() || levels.size() == 0)
		{
			return EffectiveBlocks.note(EffectiveBlocks.t("setupBlock", rc));
		}
		for (final JsonNode l : levels)
		{
			if (l.path("states").asText("").isEmpty() || l.path("parent").asText("").isEmpty() || l.path("identity").asText("").isEmpty()
				|| l.path("effectiveDate").asText("").isEmpty())
			{
				return EffectiveBlocks.note(EffectiveBlocks.t("setupBlock", rc));
			}
		}
		if (rc.creating || rc.record == null)
		{
			return EffectiveBlocks.note(EffectiveBlocks.t("afterCreate", rc));
		}
		final String rootFk = b.path("root").asText("");
		final Adaptation root = rootFk.isEmpty() ? rc.record : MoreBlocks.linked(rc, rootFk);
		if (root == null)
		{
			return EffectiveBlocks.note(EffectiveBlocks.t("noLinked", rc));
		}
		final Adaptation dataset = root.getContainer();
		final String dateField = b.path("date").asText("");
		LocalDate def = dateField.isEmpty() ? null : Values.date(rc.raw(dateField));
		if (def == null)
		{
			def = LocalDate.now();
		}
		final Node top = new Node(root, EffectiveBlocks.safeLabel(root, rc.locale), EffectiveBlocks.open(rc, w, root));
		// the root's own states, when configured
		final String selfStates = b.path("selfStates").asText("");
		if (!selfStates.isEmpty() && !b.path("selfIdentity").asText("").isEmpty() && !b.path("selfEffectiveDate").asText("").isEmpty())
		{
			final AdaptationTable st = EffectiveBlocks.table(dataset, selfStates);
			if (st != null)
			{
				final JsonNode cfg = LayoutStore.json().createObjectNode().put("effectiveDate", b.path("selfEffectiveDate").asText("")).put("endDate", b.path("selfEndDate").asText(""))
					.put("label", b.path("selfLabel").asText("")).put("chip", b.path("selfChip").asText(""));
				final SchemaNode selfFk = EffectiveBlocks.fkNode(st.getTableOccurrenceRootNode(), b.path("selfIdentity").asText(""));
				final SchemaNode selfDate = st.getTableOccurrenceRootNode().getNode(LayoutPaths.relative(b.path("selfEffectiveDate").asText("")));
				for (final Adaptation r : EffectiveBlocks.select(st, EffectiveBlocks.keyPredicate(b.path("selfIdentity").asText(""), root)))
				{
					if (selfFk != null && selfDate != null && Access.canRead(selfFk, r) && Access.canRead(selfDate, r))
					{
						top.states.add(EffectiveBlocks.state(rc, w, r, cfg));
					}
				}
			}
		}
		final List<String> levelLabels = new ArrayList<String>();
		List<Node> parents = new ArrayList<Node>();
		parents.add(top);
		final TreeSet<String> bounds = new TreeSet<String>();
		for (final State s : top.states)
		{
			EffectiveBlocks.bound(bounds, s);
		}
		int depth = 0;
		boolean truncated = false;
		for (final JsonNode l : levels)
		{
			if (++depth > 8 || parents.isEmpty())
			{
				break;
			}
			final AdaptationTable table = EffectiveBlocks.table(dataset, l.path("states").asText(""));
			if (table == null)
			{
				return EffectiveBlocks.note(RuntimeText.get("asOfNoTable", rc.locale, l.path("states").asText("")));
			}
			final SchemaNode tRoot = table.getTableOccurrenceRootNode();
			final SchemaNode identityFk = EffectiveBlocks.fkNode(tRoot, l.path("identity").asText(""));
			if (EffectiveBlocks.fkNode(tRoot, l.path("parent").asText("")) == null || identityFk == null)
			{
				return EffectiveBlocks.note(EffectiveBlocks.t("setupBlock", rc));
			}
			levelLabels.add(EffectiveBlocks.levelName(rc, l, identityFk, dataset));
			final SchemaNode parentFk = EffectiveBlocks.fkNode(tRoot, l.path("parent").asText(""));
			final SchemaNode dateNode = tRoot.getNode(LayoutPaths.relative(l.path("effectiveDate").asText("")));
			final List<Node> next = new ArrayList<Node>();
			// one scan of the states table per level (its parent key is not indexed): an OR of the parents' keys, by chunks
			final Map<String, Node> parentByKey = new LinkedHashMap<String, Node>();
			for (final Node p : parents)
			{
				parentByKey.put(p.identity.getOccurrencePrimaryKey().format(), p);
			}
			final List<String> keys = new ArrayList<String>(parentByKey.keySet());
			final Map<String, Node> byIdentity = new LinkedHashMap<String, Node>();
			int read = 0;
			for (int i = 0; i < keys.size() && read < EffectiveBlocks.CAP; i += 100)
			{
				final StringBuilder pred = new StringBuilder();
				for (final String k : keys.subList(i, Math.min(keys.size(), i + 100)))
				{
					pred.append(pred.length() == 0 ? "" : " or ").append(LayoutPaths.relative(l.path("parent").asText("")).format()).append(" = ")
						.append(XPathExpressionHelper.encodeLiteralStringWithDelimiters(k));
				}
				for (final Adaptation r : EffectiveBlocks.select(table, pred.toString()))
				{
					// the links a state carries are shown only when the user may read them, like the dates it is placed by
					if (!Access.canRead(parentFk, r) || !Access.canRead(identityFk, r) || dateNode == null || !Access.canRead(dateNode, r))
					{
						continue;
					}
					final Node p = parentByKey.get(Values.key(Values.raw(r, l.path("parent").asText(""))));
					if (p == null)
					{
						continue;
					}
					read++;
					final String ik = Values.key(Values.raw(r, l.path("identity").asText("")));
					Node n = byIdentity.get(p.identity.getOccurrencePrimaryKey().format() + "\u0001" + ik);
					if (n == null)
					{
						final Adaptation identity = EffectiveBlocks.linked(r, identityFk);
						if (identity == null)
						{
							continue;
						}
						n = new Node(identity, EffectiveBlocks.safeLabel(identity, rc.locale), EffectiveBlocks.open(rc, w, identity));
						byIdentity.put(p.identity.getOccurrencePrimaryKey().format() + "\u0001" + ik, n);
						p.children.add(n);
						next.add(n);
					}
					final State s = EffectiveBlocks.state(rc, w, r, l);
					n.states.add(s);
					EffectiveBlocks.bound(bounds, s);
				}
			}
			if (read >= EffectiveBlocks.CAP)
			{
				truncated = true;
			}
			parents = next;
		}
		final StringBuilder h = new StringBuilder("<div class=\"ldf-aot\" data-def=\"").append(EffectiveBlocks.iso(def)).append("\"><div class=\"ldf-aot-bar\"><button type=\"button\" data-aot=\"prev\" title=\"")
			.append(EffectiveBlocks.esc(EffectiveBlocks.t("asOfPrev", rc))).append("\">◀</button><input type=\"date\" value=\"").append(EffectiveBlocks.iso(def))
			.append("\" aria-label=\"").append(EffectiveBlocks.esc(EffectiveBlocks.t("asOfDate", rc))).append("\"><button type=\"button\" data-aot=\"next\" title=\"")
			.append(EffectiveBlocks.esc(EffectiveBlocks.t("asOfNext", rc))).append("\">▶</button><button type=\"button\" data-aot=\"today\">")
			.append(EffectiveBlocks.esc(EffectiveBlocks.t("asOfToday", rc))).append("</button><span class=\"ldf-aot-cnt\"></span></div><ul class=\"ldf-aot-tree\">");
		EffectiveBlocks.node(h, top, 0, rc);
		h.append("</ul>");
		if (truncated)
		{
			h.append(EffectiveBlocks.note(RuntimeText.get("asOfCut", rc.locale, Integer.valueOf(EffectiveBlocks.CAP))));
		}
		h.append("<script type=\"application/json\">");
		final com.fasterxml.jackson.databind.node.ObjectNode cfg = LayoutStore.json().createObjectNode();
		cfg.put("def", EffectiveBlocks.iso(def));
		cfg.put("none", EffectiveBlocks.t("asOfNone", rc));
		cfg.put("fixed", !dateField.isEmpty() && Values.date(rc.raw(dateField)) != null);
		try
		{
			cfg.put("user", rc.context.getSession().getUserReference().getUserId());
		}
		catch (final RuntimeException ex)
		{
			// no session: the date is not shared
		}
		final com.fasterxml.jackson.databind.node.ArrayNode lv = cfg.putArray("levels");
		for (final String s : levelLabels)
		{
			lv.add(s);
		}
		final com.fasterxml.jackson.databind.node.ArrayNode bd = cfg.putArray("bounds");
		for (final String s : bounds)
		{
			bd.add(s);
		}
		h.append(cfg.toString().replace("</", "<\\/")).append("</script></div>");
		return h.toString();
	}

	/**
	 * What the count of a level calls its entities: the level's {@code name} text, else the label of the table the
	 * identity key points to, without a trailing parenthesis ("Sub-funds (identity)" reads "Sub-funds").
	 */
	private static String levelName(final RenderContext rc, final JsonNode l, final SchemaNode pIdentityFk, final Adaptation pDataset)
	{
		final String own = rc.text(l.get("name"));
		if (!own.isEmpty())
		{
			return own;
		}
		try
		{
			final AdaptationTable target = pDataset.getTable(pIdentityFk.getFacetOnTableReference().getTablePath());
			final String label = target.getTableNode().getLabel(rc.locale).trim();
			return label.endsWith(")") && label.lastIndexOf('(') > 0 ? label.substring(0, label.lastIndexOf('(')).trim() : label;
		}
		catch (final RuntimeException ex)
		{
			return l.path("states").asText("");
		}
	}

	private static void bound(final TreeSet<String> pBounds, final State s)
	{
		if (s.from != null)
		{
			pBounds.add(s.from.toString());
		}
		if (s.to != null)
		{
			pBounds.add(s.to.plusDays(1).toString());
		}
	}

	private static AdaptationTable table(final Adaptation pDataset, final String pPath)
	{
		try
		{
			return pDataset.getTable(Path.parse(pPath));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private static void node(final StringBuilder h, final Node n, final int pLevel, final RenderContext rc)
	{
		h.append("<li class=\"ldf-aot-i\" data-lv=\"").append(pLevel).append("\"><div class=\"ldf-aot-h\"><a class=\"ldf-aot-id\" ").append(n.link).append(">")
			.append(EffectiveBlocks.esc(n.label)).append("</a>");
		n.states.sort((p, q) -> EffectiveBlocks.iso(p.from).compareTo(EffectiveBlocks.iso(q.from)));
		for (final State s : n.states)
		{
			h.append("<a class=\"ldf-aot-s\" data-f=\"").append(EffectiveBlocks.iso(s.from)).append("\" data-t=\"").append(EffectiveBlocks.iso(s.to)).append("\" ")
				.append(s.link).append(" title=\"").append(EffectiveBlocks.esc(EffectiveBlocks.range(s.from, s.to, rc)))
				.append("\"><b>").append(EffectiveBlocks.esc(s.label)).append("</b>");
			if (!s.chip.isEmpty())
			{
				h.append("<small>").append(EffectiveBlocks.esc(s.chip)).append("</small>");
			}
			h.append("</a>");
		}
		h.append("<span class=\"ldf-aot-none\">").append(EffectiveBlocks.esc(EffectiveBlocks.t("asOfNone", rc))).append("</span></div>");
		if (!n.children.isEmpty())
		{
			h.append("<ul>");
			for (final Node c : n.children)
			{
				EffectiveBlocks.node(h, c, pLevel + 1, rc);
			}
			h.append("</ul>");
		}
		h.append("</li>");
	}

	private static String range(final LocalDate a, final LocalDate b, final RenderContext rc)
	{
		return (a == null ? "…" : a.toString()) + " → " + (b == null ? EffectiveBlocks.t("stOpen", rc) : b.toString());
	}

	// ----------------------------------------------------------------------------------------------- stateTimeline

	/**
	 * The states of one entity on a time scale: {@code a} (an association of the record to its states) or, when the
	 * record is itself a state, {@code via} (its foreign key to the entity: the siblings are drawn and this one marked).
	 * {@code effectiveDate}, {@code endDate}, {@code label}, {@code badge} + {@code colors}, {@code date} (a date field
	 * of the record drawn as a marker; empty: today only).
	 */
	static String stateTimeline(final RenderContext rc, final JsonNode b)
	{
		return EffectiveBlocks.stateTimeline(rc, b, null);
	}

	static String stateTimeline(final RenderContext rc, final JsonNode b, final UIFormPaneWriter w)
	{
		final String a = b.path("a").asText("");
		final String via = b.path("via").asText("");
		final String ed = b.path("effectiveDate").asText("");
		if (ed.isEmpty() || a.isEmpty() && via.isEmpty())
		{
			return EffectiveBlocks.note(EffectiveBlocks.t("setupBlock", rc));
		}
		if (rc.creating || rc.record == null)
		{
			return EffectiveBlocks.note(EffectiveBlocks.t("afterCreate", rc));
		}
		final List<Adaptation> rows;
		if (!a.isEmpty())
		{
			rows = rc.rows(a, EffectiveBlocks.CAP);
		}
		else
		{
			final Adaptation identity = MoreBlocks.linked(rc, via);
			if (identity == null)
			{
				return EffectiveBlocks.note(EffectiveBlocks.t("noLinked", rc));
			}
			rows = EffectiveBlocks.select(rc.record.getContainerTable(), EffectiveBlocks.keyPredicate(via, identity));
		}
		final String end = b.path("endDate").asText("");
		final String label = b.path("label").asText("");
		final String badge = b.path("badge").asText("");
		final List<Object[]> bars = new ArrayList<Object[]>();
		LocalDate min = null;
		LocalDate max = null;
		for (final Adaptation r : rows)
		{
			final LocalDate s = Values.date(Values.raw(r, ed));
			if (s == null || a.isEmpty() && !Access.canRead(Values.node(r, via), r))
			{
				continue;
			}
			final LocalDate e = end.isEmpty() ? null : Values.date(Values.raw(r, end));
			min = min == null || s.isBefore(min) ? s : min;
			max = max == null || s.isAfter(max) ? s : max;
			if (e != null && e.isAfter(max))
			{
				max = e;
			}
			bars.add(new Object[] { r, s, e });
		}
		if (bars.isEmpty())
		{
			return EffectiveBlocks.note(EffectiveBlocks.t("noDates", rc));
		}
		bars.sort((p, q) -> ((LocalDate) p[1]).compareTo((LocalDate) q[1]));
		final LocalDate today = LocalDate.now();
		final String dateField = b.path("date").asText("");
		final LocalDate marker = dateField.isEmpty() ? null : Values.date(rc.raw(dateField));
		for (final LocalDate d : new LocalDate[] { today, marker })
		{
			if (d != null && d.isAfter(max))
			{
				max = d;
			}
		}
		final LocalDate from = min.withDayOfMonth(1);
		final LocalDate to = max.plusMonths(1).withDayOfMonth(1);
		final double span = Math.max(1, ChronoUnit.DAYS.between(from, to));
		final StringBuilder h = new StringBuilder("<div class=\"ldf-gt ldf-st\"><span></span><div class=\"ldf-gt-sc\">");
		final long months = ChronoUnit.MONTHS.between(from, to);
		final int step = months > 48 ? 12 : months > 24 ? 6 : months > 9 ? 3 : 1;
		for (LocalDate m = from; m.isBefore(to); m = m.plusMonths(step))
		{
			h.append("<span style=\"left:").append(EffectiveBlocks.pct(ChronoUnit.DAYS.between(from, m) / span)).append("\">")
				.append(EffectiveBlocks.esc(step >= 12 ? String.valueOf(m.getYear()) : m.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, rc.locale) + " " + String.valueOf(m.getYear()).substring(2)))
				.append("</span>");
		}
		h.append("</div>");
		final StringBuilder lines = new StringBuilder();
		if (!today.isBefore(from) && today.isBefore(to))
		{
			lines.append("<i class=\"ldf-gt-now\" style=\"left:").append(EffectiveBlocks.pct(ChronoUnit.DAYS.between(from, today) / span)).append("\"></i>");
		}
		if (marker != null && !marker.equals(today) && !marker.isBefore(from) && marker.isBefore(to))
		{
			lines.append("<i class=\"ldf-st-mk\" style=\"left:").append(EffectiveBlocks.pct(ChronoUnit.DAYS.between(from, marker) / span)).append("\"></i>");
		}
		final Map<String, String> legend = new LinkedHashMap<String, String>();
		final String[] cycle = { " ldf-t-accent", " ldf-t-info", " ldf-t-ok", " ldf-t-warn", " ldf-t-muted", " ldf-t-bad" };
		for (final Object[] bar : bars)
		{
			final Adaptation r = (Adaptation) bar[0];
			final LocalDate s = (LocalDate) bar[1];
			final LocalDate e = (LocalDate) bar[2];
			String tone = " ldf-t-accent";
			if (!badge.isEmpty())
			{
				final String shown = Values.display(r, badge, rc.locale);
				tone = Values.tone(b.path("colors"), Values.key(Values.raw(r, badge)), shown);
				if (tone.isEmpty())
				{
					tone = legend.containsKey(shown) ? legend.get(shown) : cycle[legend.size() % cycle.length];
				}
				else
				{
					tone = " " + tone.trim();
				}
				legend.put(shown, tone);
			}
			final boolean current = a.isEmpty() && r.getOccurrencePrimaryKey().equals(rc.record.getOccurrencePrimaryKey());
			final String shown = label.isEmpty() ? "" : Values.display(r, label, rc.locale);
			h.append("<a class=\"ldf-gt-l").append(current ? " ldf-st-cur" : "").append("\" ").append(EffectiveBlocks.open(rc, w, r)).append(">")
				.append(EffectiveBlocks.esc(shown.isEmpty() ? EffectiveBlocks.safeLabel(r, rc.locale) : shown)).append("<small>").append(EffectiveBlocks.esc(EffectiveBlocks.range(s, e, rc)))
				.append("</small></a><div class=\"ldf-gt-tr\" title=\"").append(EffectiveBlocks.esc(EffectiveBlocks.range(s, e, rc))).append("\">").append(lines);
			final LocalDate en = e == null ? to : e.plusDays(1);
			h.append("<i class=\"ldf-st-b").append(tone).append(e == null ? " ldf-st-open" : "").append(current ? " ldf-st-cur" : "").append("\" style=\"left:")
				.append(EffectiveBlocks.pct(ChronoUnit.DAYS.between(from, s) / span)).append(";width:").append(EffectiveBlocks.pct(Math.max(1, ChronoUnit.DAYS.between(s, en)) / span)).append("\"></i></div>");
		}
		h.append("</div>");
		if (!legend.isEmpty() || marker != null)
		{
			h.append("<div class=\"ldf-hm-axes\">");
			for (final Map.Entry<String, String> e : legend.entrySet())
			{
				h.append("<span><i class=\"ldf-gt-k ldf-st-b").append(e.getValue()).append("\"></i>").append(EffectiveBlocks.esc(e.getKey().isEmpty() ? EffectiveBlocks.t("chEmpty", rc) : e.getKey())).append("</span>");
			}
			if (marker != null && !marker.equals(today))
			{
				h.append("<span><i class=\"ldf-gt-k ldf-st-mk-k\"></i>").append(EffectiveBlocks.esc(rc.label(dateField) + " " + marker)).append("</span>");
			}
			h.append("</div>");
		}
		return h.toString();
	}

	// --------------------------------------------------------------------------------------------------------- delta

	private static final class Change
	{
		final String field;
		final String before;
		final String after;

		Change(final String pField, final String pBefore, final String pAfter)
		{
			this.field = pField;
			this.before = pBefore;
			this.after = pAfter;
		}
	}

	/**
	 * A before/after report kept as JSON in a text field: {@code field} of the record, or of each row of {@code a}
	 * (one section per row, titled by {@code label}). Shapes read: an object of {@code {field: {before, after}}}; an
	 * array of {@code {field|path|name, before|old|from, after|new|to}}; either of them under a {@code changes},
	 * {@code fields}, {@code items} or {@code delta} key. {@code labelsFrom}: the table whose field labels name the
	 * keys (empty: this table); {@code max} (50).
	 */
	static String delta(final RenderContext rc, final JsonNode b)
	{
		return EffectiveBlocks.delta(rc, b, null);
	}

	static String delta(final RenderContext rc, final JsonNode b, final UIFormPaneWriter w)
	{
		final String field = b.path("field").asText("");
		if (field.isEmpty())
		{
			return EffectiveBlocks.note(EffectiveBlocks.t("setupBlock", rc));
		}
		if (rc.creating || rc.record == null)
		{
			return EffectiveBlocks.note(EffectiveBlocks.t("afterCreate", rc));
		}
		final int max = Math.max(1, b.path("max").asInt(50));
		SchemaNode labels = rc.root;
		final String labelsFrom = b.path("labelsFrom").asText("");
		if (!labelsFrom.isEmpty())
		{
			final AdaptationTable lt = EffectiveBlocks.table(rc.record.getContainer(), labelsFrom);
			if (lt != null)
			{
				labels = lt.getTableOccurrenceRootNode();
			}
		}
		final String a = b.path("a").asText("");
		if (a.isEmpty())
		{
			final Object v = rc.raw(field);
			final List<Change> ch = EffectiveBlocks.parse(v == null ? "" : String.valueOf(v));
			if (ch == null)
			{
				return EffectiveBlocks.note(EffectiveBlocks.t("deltaBad", rc));
			}
			return ch.isEmpty() ? EffectiveBlocks.note(EffectiveBlocks.t("deltaNone", rc)) : EffectiveBlocks.table(rc, ch, labels, max);
		}
		final StringBuilder h = new StringBuilder();
		int sections = 0;
		for (final Adaptation r : rc.rows(a, 100))
		{
			final Object v = Values.raw(r, field);
			final List<Change> ch = EffectiveBlocks.parse(v == null ? "" : String.valueOf(v));
			if (ch == null || ch.isEmpty())
			{
				continue;
			}
			final String rl = b.path("label").asText("").isEmpty() ? "" : Values.display(r, b.path("label").asText(""), rc.locale);
			h.append("<div class=\"ldf-dl-h\"><a ").append(EffectiveBlocks.open(rc, w, r)).append(">")
				.append(EffectiveBlocks.esc(rl.isEmpty() ? EffectiveBlocks.safeLabel(r, rc.locale) : rl)).append("</a><span class=\"ldf-badge ldf-t-warn\">")
				.append(EffectiveBlocks.esc(RuntimeText.get("chCount", rc.locale, Integer.valueOf(ch.size())))).append("</span></div>").append(EffectiveBlocks.table(rc, ch, labels, max));
			sections++;
		}
		return sections == 0 ? EffectiveBlocks.note(EffectiveBlocks.t("deltaNone", rc)) : h.toString();
	}

	private static String table(final RenderContext rc, final List<Change> ch, final SchemaNode labels, final int max)
	{
		final StringBuilder h = new StringBuilder("<table class=\"ldf-ch\">");
		int n = 0;
		for (final Change c : ch)
		{
			if (n++ >= max)
			{
				break;
			}
			h.append("<tr><th>").append(EffectiveBlocks.esc(EffectiveBlocks.fieldLabel(labels, c.field, rc))).append("</th><td class=\"ldf-ch-o\">")
				.append(EffectiveBlocks.esc(c.before.isEmpty() ? EffectiveBlocks.t("chEmpty", rc) : c.before)).append("</td><td class=\"ldf-ch-a\">→</td><td class=\"ldf-ch-n\">")
				.append(EffectiveBlocks.esc(c.after.isEmpty() ? EffectiveBlocks.t("chEmpty", rc) : c.after)).append("</td></tr>");
		}
		h.append("</table>");
		if (ch.size() > max)
		{
			h.append(EffectiveBlocks.note(RuntimeText.get("chMore", rc.locale, Integer.valueOf(ch.size() - max))));
		}
		return h.toString();
	}

	/** The label of the field a key names ({@code name}, {@code ./name}, {@code /root/FundState/name}), else the key. */
	private static String fieldLabel(final SchemaNode labels, final String pKey, final RenderContext rc)
	{
		if (labels == null || pKey == null || pKey.isEmpty())
		{
			return String.valueOf(pKey);
		}
		String k = pKey;
		if (k.startsWith("/root/"))
		{
			final String table = labels.getTableNode() == null ? "" : labels.getTableNode().getPathInSchema().format();
			k = !table.isEmpty() && k.startsWith(table + "/") ? k.substring(table.length() + 1) : k.substring(k.lastIndexOf('/') + 1);
		}
		if (k.startsWith("./"))
		{
			k = k.substring(2);
		}
		try
		{
			final SchemaNode n = labels.getNode(Path.parse("./" + k));
			return n == null ? pKey : n.getLabel(rc.locale);
		}
		catch (final RuntimeException ex)
		{
			return pKey;
		}
	}

	/** The changes a delta report holds; an empty list when it holds none, null when the text is not JSON. */
	static List<Change> parse(final String pJson)
	{
		if (pJson == null || pJson.trim().isEmpty())
		{
			return new ArrayList<Change>();
		}
		final JsonNode root;
		try
		{
			root = LayoutStore.json().readTree(pJson);
		}
		catch (final Exception ex)
		{
			return null;
		}
		final List<Change> out = new ArrayList<Change>();
		EffectiveBlocks.collect(root, out, 0);
		return out;
	}

	private static final String[] FIELD_KEYS = { "field", "path", "name", "key", "attribute" };
	private static final String[] BEFORE_KEYS = { "before", "old", "from", "previous", "oldValue" };
	private static final String[] AFTER_KEYS = { "after", "new", "to", "current", "newValue" };

	private static void collect(final JsonNode n, final List<Change> out, final int pDepth)
	{
		if (n == null || pDepth > 4)
		{
			return;
		}
		if (n.isArray())
		{
			for (final JsonNode x : n)
			{
				if (x.isObject() && EffectiveBlocks.has(x, EffectiveBlocks.BEFORE_KEYS, EffectiveBlocks.AFTER_KEYS))
				{
					out.add(new Change(EffectiveBlocks.first(x, EffectiveBlocks.FIELD_KEYS), EffectiveBlocks.first(x, EffectiveBlocks.BEFORE_KEYS), EffectiveBlocks.first(x, EffectiveBlocks.AFTER_KEYS)));
				}
				else
				{
					EffectiveBlocks.collect(x, out, pDepth + 1);
				}
			}
			return;
		}
		if (!n.isObject())
		{
			return;
		}
		final Iterator<Map.Entry<String, JsonNode>> it = n.fields();
		while (it.hasNext())
		{
			final Map.Entry<String, JsonNode> e = it.next();
			final JsonNode v = e.getValue();
			if (v.isObject() && EffectiveBlocks.has(v, EffectiveBlocks.BEFORE_KEYS, EffectiveBlocks.AFTER_KEYS))
			{
				out.add(new Change(e.getKey(), EffectiveBlocks.first(v, EffectiveBlocks.BEFORE_KEYS), EffectiveBlocks.first(v, EffectiveBlocks.AFTER_KEYS)));
			}
			else if (v.isArray() || v.isObject())
			{
				EffectiveBlocks.collect(v, out, pDepth + 1);
			}
		}
	}

	private static boolean has(final JsonNode o, final String[]... pGroups)
	{
		for (final String[] g : pGroups)
		{
			boolean one = false;
			for (final String k : g)
			{
				if (o.has(k))
				{
					one = true;
					break;
				}
			}
			if (!one)
			{
				return false;
			}
		}
		return true;
	}

	private static String first(final JsonNode o, final String[] pKeys)
	{
		for (final String k : pKeys)
		{
			final JsonNode v = o.get(k);
			if (v != null)
			{
				return v.isNull() ? "" : v.isValueNode() ? v.asText() : v.toString();
			}
		}
		return "";
	}
}
