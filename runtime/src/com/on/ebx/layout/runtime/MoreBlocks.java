package com.on.ebx.layout.runtime;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.schema.info.SchemaFacetTableRef;
import com.orchestranetworks.service.LoggingCategory;

/**
 * The blocks added in 1.4.0. Each one returns the HTML that goes inside its panel; every table and field comes from
 * the block's settings, read through the record, its associations ({@code AssociationLink.getAssociationResult}) and
 * its foreign keys ({@code SchemaFacetTableRef.getLinkedRecord}).
 */
final class MoreBlocks
{
	private static final int ROWS_CAP = 5000;
	private static final String[] PALETTE = { "#7F17FF", "#0B7A4B", "#3931EC", "#B86E00", "#C73200", "#0E7C86", "#8E8898" };

	private MoreBlocks()
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

	private static String empty(final String pText)
	{
		return "<div class=\"ldf-more\">" + MoreBlocks.esc(pText) + "</div>";
	}

	private static String creatingNote(final RenderContext rc)
	{
		return MoreBlocks.empty(MoreBlocks.t("afterCreate", rc));
	}

	private static String setup(final RenderContext rc)
	{
		return MoreBlocks.empty(MoreBlocks.t("setupBlock", rc));
	}

	// ------------------------------------------------------------------------------------------------ timeline

	static String timeline(final RenderContext rc, final JsonNode b)
	{
		final String a = b.path("a").asText("");
		final String date = b.path("date").asText("");
		if (a.isEmpty() || date.isEmpty())
		{
			return MoreBlocks.setup(rc);
		}
		if (rc.creating)
		{
			return MoreBlocks.creatingNote(rc);
		}
		final List<Adaptation> rows = rc.rows(a, MoreBlocks.ROWS_CAP);
		if (rows.isEmpty())
		{
			return MoreBlocks.empty(MoreBlocks.t("noRow", rc));
		}
		Collections.sort(rows, new Comparator<Adaptation>()
		{
			@Override
			public int compare(final Adaptation x, final Adaptation y)
			{
				final Object dx = Values.raw(x, date);
				final Object dy = Values.raw(y, date);
				final long lx = dx instanceof Date ? ((Date) dx).getTime() : Long.MIN_VALUE;
				final long ly = dy instanceof Date ? ((Date) dy).getTime() : Long.MIN_VALUE;
				return Long.compare(ly, lx);
			}
		});
		final int max = Math.max(1, b.path("max").asInt(10));
		final String title = b.path("titleField").asText("");
		final String detail = b.path("detail").asText("");
		final JsonNode color = b.path("color");
		final StringBuilder h = new StringBuilder("<ol class=\"ldf-tl\">");
		for (int i = 0; i < rows.size() && i < max; i++)
		{
			final Adaptation r = rows.get(i);
			String tone = "";
			if (!color.path("field").asText("").isEmpty())
			{
				final String f = color.path("field").asText();
				tone = Values.tone(color.path("map"), Values.key(Values.raw(r, f)), Values.display(r, f, rc.locale));
			}
			final String head = title.isEmpty() ? r.getLabel(rc.locale) : Values.display(r, title, rc.locale);
			h.append("<li class=\"ldf-tl-i").append(tone).append("\"><span class=\"ldf-tl-d\">").append(MoreBlocks.esc(Values.display(r, date, rc.locale)))
				.append("</span><b>").append(MoreBlocks.esc(head.isEmpty() ? r.getLabel(rc.locale) : head)).append("</b>");
			final String d = detail.isEmpty() ? "" : Values.display(r, detail, rc.locale);
			if (!d.isEmpty())
			{
				h.append("<span class=\"ldf-tl-s\">").append(MoreBlocks.esc(d)).append("</span>");
			}
			h.append("</li>");
		}
		h.append("</ol>");
		if (rows.size() > max)
		{
			h.append(MoreBlocks.empty(RuntimeText.get("more", rc.locale, Integer.valueOf(rows.size() - max))));
		}
		return h.toString();
	}

	// ------------------------------------------------------------------------------------------------ history

	static String history(final RenderContext rc, final JsonNode b)
	{
		if (rc.creating || rc.record == null)
		{
			return MoreBlocks.creatingNote(rc);
		}
		final AdaptationTable history;
		try
		{
			history = rc.context.getCurrentTable().getHistory();
		}
		catch (final RuntimeException ex)
		{
			return MoreBlocks.empty(MoreBlocks.t("historyOff", rc));
		}
		if (history == null)
		{
			return MoreBlocks.empty(MoreBlocks.t("historyOff", rc));
		}
		final List<String> fields = new ArrayList<String>();
		for (final JsonNode p : b.path("fields"))
		{
			if (rc.node(p.asText()) != null)
			{
				fields.add(p.asText());
			}
		}
		if (fields.isEmpty())
		{
			MoreBlocks.terminals(rc.root, fields);
		}
		// technical fields of the history view: those the record itself does not have
		final List<SchemaNode> technical = new ArrayList<SchemaNode>();
		MoreBlocks.technical(history.getTableOccurrenceRootNode(), rc, technical);
		SchemaNode when = null;
		SchemaNode who = null;
		SchemaNode op = null;
		for (final SchemaNode n : technical)
		{
			final String name = n.getPathInAdaptation().format().toLowerCase(Locale.ROOT);
			final Class<?> c = n.getOccurrencesJavaClass();
			if (when == null && c == Date.class)
			{
				when = n;
			}
			else if (who == null && (name.contains("user") || name.contains("author")))
			{
				who = n;
			}
			else if (op == null && (name.endsWith("op") || name.contains("operation")))
			{
				op = n;
			}
		}
		final List<Adaptation> rows = new ArrayList<Adaptation>();
		try
		{
			final RequestResult result = history.createRequestResult(rc.record.toXPathPredicateString());
			try
			{
				for (Adaptation r = result.nextAdaptation(); r != null && rows.size() < 2000; r = result.nextAdaptation())
				{
					rows.add(r);
				}
			}
			finally
			{
				result.close();
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] history of " + rc.record.toXPathPredicateString() + " not read: " + ex.getMessage());
			return MoreBlocks.empty(MoreBlocks.t("historyOff", rc));
		}
		if (rows.isEmpty())
		{
			return MoreBlocks.empty(MoreBlocks.t("historyNone", rc));
		}
		final SchemaNode whenNode = when;
		if (whenNode != null)
		{
			Collections.sort(rows, new Comparator<Adaptation>()
			{
				@Override
				public int compare(final Adaptation x, final Adaptation y)
				{
					final Object dx = x.get(whenNode);
					final Object dy = y.get(whenNode);
					return Long.compare(dx instanceof Date ? ((Date) dx).getTime() : 0L, dy instanceof Date ? ((Date) dy).getTime() : 0L);
				}
			});
		}
		// oldest first: each row is compared with the one before it
		final List<String> entries = new ArrayList<String>();
		final boolean author = b.path("author").asBoolean(true);
		Adaptation previous = null;
		for (final Adaptation r : rows)
		{
			final List<String> changes = new ArrayList<String>();
			for (final String f : fields)
			{
				final String now = Values.display(r, f, rc.locale);
				final String before = previous == null ? "" : Values.display(previous, f, rc.locale);
				if (!now.equals(before) && (previous != null || !now.isEmpty()))
				{
					changes.add("<span class=\"ldf-hc\"><i>" + MoreBlocks.esc(rc.label(f)) + "</i> " + (previous == null || before.isEmpty() ? "" : "<s>" + MoreBlocks.esc(MoreBlocks.cut(before)) + "</s> → ")
						+ "<b>" + MoreBlocks.esc(now.isEmpty() ? "—" : MoreBlocks.cut(now)) + "</b></span>");
				}
			}
			final String opValue = op == null ? "" : Values.key(r.get(op));
			final String kind = "C".equals(opValue) || previous == null ? MoreBlocks.t("histCreated", rc) : "D".equals(opValue) ? MoreBlocks.t("histDeleted", rc) : MoreBlocks.t("histChanged", rc);
			if (changes.isEmpty() && previous != null && !"D".equals(opValue))
			{
				previous = r;
				continue;
			}
			final StringBuilder e = new StringBuilder("<li><span class=\"ldf-tl-d\">");
			e.append(MoreBlocks.esc(whenNode == null ? "" : MoreBlocks.dateTime(r.get(whenNode), rc.locale)));
			if (author && who != null)
			{
				e.append(" · ").append(MoreBlocks.esc(Values.key(r.get(who))));
			}
			e.append("</span><b>").append(MoreBlocks.esc(kind)).append("</b><span class=\"ldf-hcs\">").append(String.join("", changes)).append("</span></li>");
			entries.add(e.toString());
			previous = r;
		}
		Collections.reverse(entries);
		final int max = Math.max(1, b.path("max").asInt(8));
		final StringBuilder h = new StringBuilder("<ol class=\"ldf-tl ldf-hist\">");
		for (int i = 0; i < entries.size() && i < max; i++)
		{
			h.append(entries.get(i));
		}
		h.append("</ol>");
		if (entries.size() > max)
		{
			h.append(MoreBlocks.empty(RuntimeText.get("more", rc.locale, Integer.valueOf(entries.size() - max))));
		}
		return h.toString();
	}

	private static String cut(final String s)
	{
		return s.length() > 80 ? s.substring(0, 79) + "…" : s;
	}

	private static String dateTime(final Object pValue, final Locale pLocale)
	{
		if (!(pValue instanceof Date))
		{
			return Values.key(pValue);
		}
		return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, pLocale).format((Date) pValue);
	}

	private static void terminals(final SchemaNode pNode, final List<String> pOut)
	{
		for (final SchemaNode c : pNode.getNodeChildren())
		{
			if (c.isAssociationNode() || c.isValueFunction())
			{
				continue;
			}
			if (c.isTerminalValue())
			{
				pOut.add(LayoutPaths.stored(c.getPathInAdaptation()));
			}
			else
			{
				MoreBlocks.terminals(c, pOut);
			}
		}
	}

	private static void technical(final SchemaNode pNode, final RenderContext rc, final List<SchemaNode> pOut)
	{
		for (final SchemaNode c : pNode.getNodeChildren())
		{
			final String stored = LayoutPaths.stored(c.getPathInAdaptation());
			if (rc.node(stored) != null)
			{
				continue;
			}
			if (c.isTerminalValue())
			{
				pOut.add(c);
			}
			else
			{
				MoreBlocks.technical(c, rc, pOut);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ completeness

	static String completeness(final RenderContext rc, final JsonNode b)
	{
		final List<String> paths = new ArrayList<String>();
		final List<Double> weights = new ArrayList<Double>();
		for (final JsonNode f : b.path("fields"))
		{
			final String p = f.isTextual() ? f.asText() : f.path("p").asText("");
			if (rc.node(p) != null)
			{
				paths.add(p);
				weights.add(Double.valueOf(f.isObject() ? Math.max(0, f.path("weight").asDouble(1)) : 1));
			}
		}
		if (paths.isEmpty())
		{
			for (final Map.Entry<String, RenderContext.Location> e : rc.locations.entrySet())
			{
				final SchemaNode n = rc.node(e.getKey());
				if (n != null && n.isTerminalValue() && !n.isAssociationNode() && !n.isValueFunction() && e.getValue().field != null)
				{
					paths.add(e.getKey());
					weights.add(Double.valueOf(1));
				}
			}
			Collections.sort(paths);
		}
		if (paths.isEmpty())
		{
			return MoreBlocks.setup(rc);
		}
		double total = 0;
		double filled = 0;
		final List<String> missing = new ArrayList<String>();
		for (int i = 0; i < paths.size(); i++)
		{
			total += weights.get(i).doubleValue();
			if (Values.filled(rc.raw(paths.get(i))))
			{
				filled += weights.get(i).doubleValue();
			}
			else
			{
				missing.add(paths.get(i));
			}
		}
		final int pct = total <= 0 ? 0 : (int) Math.round(100 * filled / total);
		final int threshold = b.path("threshold").asInt(80);
		final String tone = pct >= threshold ? "ldf-t-ok" : pct >= threshold / 2 ? "ldf-t-warn" : "ldf-t-bad";
		final StringBuilder h = new StringBuilder("<div class=\"ldf-cp ").append(tone).append("\"><b class=\"ldf-cp-n\">").append(pct).append(" %</b><span class=\"ldf-bar\"><i style=\"width:")
			.append(pct).append("%\"></i></span><span class=\"ldf-more\">").append(MoreBlocks.esc(RuntimeText.get("filledOf", rc.locale, (paths.size() - missing.size()) + " / " + paths.size())))
			.append("</span></div>");
		if (b.path("listMissing").asBoolean(true) && !missing.isEmpty())
		{
			h.append("<div class=\"ldf-cp-miss\"><span class=\"ldf-vgh\">").append(MoreBlocks.esc(MoreBlocks.t("missing", rc))).append("</span>");
			for (final String p : missing)
			{
				final RenderContext.Location l = rc.locate(p);
				h.append("<span class=\"ldf-cp-m\">").append(MoreBlocks.esc(rc.label(p)));
				if (l != null)
				{
					h.append(" <a class=\"ldf-go\" href=\"#\" onclick=\"return ldfGo('").append(l.anchor).append("',").append(l.tab).append(")\">").append(MoreBlocks.esc(MoreBlocks.t("goto", rc)))
						.append("</a>");
				}
				h.append("</span>");
			}
			h.append("</div>");
		}
		return h.toString();
	}

	// ------------------------------------------------------------------------------------------------ stepper

	static String stepper(final RenderContext rc, final JsonNode b)
	{
		final String field = b.path("field").asText("");
		final SchemaNode node = rc.node(field);
		if (node == null)
		{
			return MoreBlocks.setup(rc);
		}
		final List<String> steps = new ArrayList<String>();
		for (final JsonNode s : b.path("steps"))
		{
			if (!s.asText("").isEmpty())
			{
				steps.add(s.asText());
			}
		}
		if (steps.isEmpty())
		{
			try
			{
				final List<?> values = node.getEnumerationList(rc.context.getValueContext());
				if (values != null)
				{
					for (final Object v : values)
					{
						steps.add(Values.key(v));
					}
				}
			}
			catch (final RuntimeException ex)
			{
				// no enumeration
			}
		}
		final Set<String> exits = new HashSet<String>();
		for (final JsonNode s : b.path("exits"))
		{
			exits.add(s.asText().toLowerCase(Locale.ROOT));
		}
		steps.removeIf(s -> exits.contains(s.toLowerCase(Locale.ROOT)));
		final String current = Values.key(rc.raw(field));
		int at = -1;
		for (int i = 0; i < steps.size(); i++)
		{
			if (steps.get(i).equalsIgnoreCase(current))
			{
				at = i;
			}
		}
		final StringBuilder h = new StringBuilder("<ol class=\"ldf-step\">");
		for (int i = 0; i < steps.size(); i++)
		{
			final String cls = i < at ? "ldf-done" : i == at ? "ldf-on" : "";
			h.append("<li class=\"").append(cls).append("\"><i>").append(i < at ? "✓" : String.valueOf(i + 1)).append("</i><span>")
				.append(MoreBlocks.esc(MoreBlocks.codeLabel(rc, node, steps.get(i), b.path("labels")))).append("</span></li>");
		}
		h.append("</ol>");
		if (at < 0 && !current.isEmpty())
		{
			final boolean exit = exits.contains(current.toLowerCase(Locale.ROOT));
			h.append("<div class=\"ldf-step-x\"><span class=\"ldf-badge").append(exit ? " ldf-t-bad" : "").append("\">").append(MoreBlocks.esc(rc.display(field))).append("</span></div>");
		}
		return h.toString();
	}

	/** A code of a field shown as the model shows it (an enumeration's label), or as configured in "labels". */
	private static String codeLabel(final RenderContext rc, final SchemaNode node, final String code, final JsonNode labels)
	{
		if (labels != null && labels.has(code) && LText.hasText(labels.get(code)))
		{
			return rc.text(labels.get(code));
		}
		try
		{
			Object value = code;
			final Class<?> c = node.getOccurrencesJavaClass();
			if (c == Integer.class)
			{
				value = Integer.valueOf(code);
			}
			else if (c == Boolean.class)
			{
				value = Boolean.valueOf(code);
			}
			final String s = node.displayOccurrence(value, true, rc.context.getValueContext(), rc.locale);
			return s == null || s.isEmpty() ? code : s;
		}
		catch (final RuntimeException ex)
		{
			return code;
		}
	}

	// ------------------------------------------------------------------------------------------------ checklist

	static String checklist(final RenderContext rc, final JsonNode b)
	{
		final JsonNode items = b.path("items");
		if (items.size() == 0)
		{
			return MoreBlocks.setup(rc);
		}
		int ok = 0;
		final StringBuilder list = new StringBuilder();
		for (final JsonNode i : items)
		{
			final boolean done = MoreBlocks.check(rc, i.path("when"));
			if (done)
			{
				ok++;
			}
			list.append("<li class=\"").append(done ? "ldf-done" : "").append("\"><i>").append(done ? "✓" : "").append("</i><span>").append(MoreBlocks.esc(rc.text(i.get("label")))).append("</span>");
			final String go = i.path("go").asText(i.path("when").path("filled").asText(i.path("when").path("field").asText("")));
			final RenderContext.Location l = done || go.isEmpty() ? null : rc.locate(go);
			if (l != null)
			{
				list.append(" <a class=\"ldf-go\" href=\"#\" onclick=\"return ldfGo('").append(l.anchor).append("',").append(l.tab).append(")\">").append(MoreBlocks.esc(MoreBlocks.t("goto", rc)))
					.append("</a>");
			}
			list.append("</li>");
		}
		return "<div class=\"ldf-ck-h\"><b>" + ok + " / " + items.size() + "</b><span class=\"ldf-bar\"><i style=\"width:" + (100 * ok / items.size()) + "%\"></i></span></div><ul class=\"ldf-ck\">"
			+ list + "</ul>";
	}

	static boolean check(final RenderContext rc, final JsonNode w)
	{
		if (w == null || !w.isObject())
		{
			return false;
		}
		if (w.has("filled"))
		{
			return Values.filled(rc.raw(w.path("filled").asText()));
		}
		if (w.has("xpath"))
		{
			return rc.record != null && rc.xpath(w.path("xpath").asText(""), false);
		}
		if (!w.path("field").asText("").isEmpty())
		{
			return Behaviors.test(w, rc.raw(w.path("field").asText()));
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------ chart, aggregates, pivot

	static String chart(final RenderContext rc, final JsonNode b)
	{
		final String a = b.path("a").asText("");
		final String by = b.path("by").asText("");
		if (a.isEmpty() || by.isEmpty())
		{
			return MoreBlocks.setup(rc);
		}
		if (rc.creating)
		{
			return MoreBlocks.creatingNote(rc);
		}
		final String fn = b.path("measure").path("fn").asText("count");
		final String field = b.path("measure").path("field").asText("");
		final Map<String, double[]> groups = new LinkedHashMap<String, double[]>();
		for (final Adaptation r : rc.rows(a, MoreBlocks.ROWS_CAP))
		{
			String k = Values.display(r, by, rc.locale);
			if (k.isEmpty())
			{
				k = "—";
			}
			double[] g = groups.get(k);
			if (g == null)
			{
				g = new double[2];
				groups.put(k, g);
			}
			final Double v = field.isEmpty() ? null : Values.number(Values.raw(r, field));
			g[0] += "count".equals(fn) ? 1 : v == null ? 0 : v.doubleValue();
			g[1] += "count".equals(fn) || v != null ? 1 : 0;
		}
		if (groups.isEmpty())
		{
			return MoreBlocks.empty(MoreBlocks.t("noRow", rc));
		}
		final List<Map.Entry<String, Double>> list = new ArrayList<Map.Entry<String, Double>>();
		for (final Map.Entry<String, double[]> e : groups.entrySet())
		{
			final double v = "avg".equals(fn) ? (e.getValue()[1] == 0 ? 0 : e.getValue()[0] / e.getValue()[1]) : e.getValue()[0];
			list.add(new java.util.AbstractMap.SimpleEntry<String, Double>(e.getKey(), Double.valueOf(v)));
		}
		list.sort((x, y) -> Double.compare(y.getValue().doubleValue(), x.getValue().doubleValue()));
		final int top = Math.max(1, b.path("top").asInt(6));
		if (list.size() > top)
		{
			double rest = 0;
			for (int i = top; i < list.size(); i++)
			{
				rest += list.get(i).getValue().doubleValue();
			}
			final List<Map.Entry<String, Double>> cut = new ArrayList<Map.Entry<String, Double>>(list.subList(0, top));
			if (!"avg".equals(fn))
			{
				cut.add(new java.util.AbstractMap.SimpleEntry<String, Double>(MoreBlocks.t("other", rc), Double.valueOf(rest)));
			}
			list.clear();
			list.addAll(cut);
		}
		double max = 0;
		double sum = 0;
		for (final Map.Entry<String, Double> e : list)
		{
			max = Math.max(max, e.getValue().doubleValue());
			sum += e.getValue().doubleValue();
		}
		final StringBuilder h = new StringBuilder();
		if ("donut".equals(b.path("shape").asText("bars")) && sum > 0)
		{
			final StringBuilder grad = new StringBuilder();
			double acc = 0;
			for (int i = 0; i < list.size(); i++)
			{
				final double from = 100 * acc / sum;
				acc += list.get(i).getValue().doubleValue();
				grad.append(i == 0 ? "" : ",").append(MoreBlocks.PALETTE[i % MoreBlocks.PALETTE.length]).append(' ').append(String.format(Locale.ROOT, "%.2f%% %.2f%%", from, 100 * acc / sum));
			}
			h.append("<div class=\"ldf-donut\"><span class=\"ldf-donut-c\" style=\"background:conic-gradient(").append(grad).append(")\"><i>").append(MoreBlocks.esc(Values.number(sum, rc.locale)))
				.append("</i></span><ul>");
			for (int i = 0; i < list.size(); i++)
			{
				h.append("<li><i style=\"background:").append(MoreBlocks.PALETTE[i % MoreBlocks.PALETTE.length]).append("\"></i><span>").append(MoreBlocks.esc(list.get(i).getKey())).append("</span><b>")
					.append(MoreBlocks.esc(Values.number(list.get(i).getValue().doubleValue(), rc.locale))).append("</b></li>");
			}
			h.append("</ul></div>");
		}
		else
		{
			h.append("<div class=\"ldf-bars\">");
			for (final Map.Entry<String, Double> e : list)
			{
				final double w = max <= 0 ? 0 : 100 * e.getValue().doubleValue() / max;
				h.append("<span class=\"ldf-bars-l\">").append(MoreBlocks.esc(e.getKey())).append("</span><span class=\"ldf-bar\"><i style=\"width:").append(String.format(Locale.ROOT, "%.1f", w))
					.append("%\"></i></span><b>").append(MoreBlocks.esc(Values.number(e.getValue().doubleValue(), rc.locale))).append("</b>");
			}
			h.append("</div>");
		}
		return h.toString();
	}

	static String aggregates(final RenderContext rc, final JsonNode b)
	{
		final String a = b.path("a").asText("");
		if (a.isEmpty() || b.path("items").size() == 0)
		{
			return MoreBlocks.setup(rc);
		}
		final List<Adaptation> rows = rc.creating ? new ArrayList<Adaptation>() : rc.rows(a, MoreBlocks.ROWS_CAP);
		final StringBuilder h = new StringBuilder("<div class=\"ldf-facts\">");
		for (final JsonNode i : b.path("items"))
		{
			final String fn = i.path("fn").asText("sum");
			final String field = i.path("field").asText("");
			double acc = 0;
			int n = 0;
			Double min = null;
			Double max = null;
			for (final Adaptation r : rows)
			{
				if ("count".equals(fn))
				{
					n++;
					continue;
				}
				final Double v = Values.number(Values.raw(r, field));
				if (v == null)
				{
					continue;
				}
				acc += v.doubleValue();
				n++;
				min = min == null || v.doubleValue() < min.doubleValue() ? v : min;
				max = max == null || v.doubleValue() > max.doubleValue() ? v : max;
			}
			final Double value = "count".equals(fn) ? Double.valueOf(n) : n == 0 ? null
				: "avg".equals(fn) ? Double.valueOf(acc / n) : "min".equals(fn) ? min : "max".equals(fn) ? max : Double.valueOf(acc);
			final String label = LText.hasText(i.get("label")) ? rc.text(i.get("label")) : MoreBlocks.t("fn_" + fn, rc) + (field.isEmpty() ? "" : " · " + MoreBlocks.targetLabel(rc, a, field));
			h.append("<div class=\"ldf-fact\"><b>").append(MoreBlocks.esc(value == null ? "—" : MoreBlocks.formatNumber(rc, value.doubleValue(), i))).append("</b><span>").append(MoreBlocks.esc(label))
				.append("</span></div>");
		}
		return h.append("</div>").toString();
	}

	static String formatNumber(final RenderContext rc, final double v, final JsonNode fmt)
	{
		final String format = fmt.path("format").asText("number");
		final int decimals = fmt.has("decimals") ? fmt.path("decimals").asInt(2) : -1;
		if ("money".equals(format))
		{
			final String currency = !fmt.path("currencyField").asText("").isEmpty() ? Values.key(rc.raw(fmt.path("currencyField").asText())) : fmt.path("currency").asText("");
			return Values.money(v, currency, decimals, rc.locale);
		}
		if ("percent".equals(format))
		{
			return Values.number(v, Math.max(0, decimals), rc.locale) + " %";
		}
		return decimals >= 0 ? Values.number(v, decimals, rc.locale) : Values.number(v, rc.locale);
	}

	/** The label of a field of an association's target table. */
	private static String targetLabel(final RenderContext rc, final String pAssociation, final String pField)
	{
		final List<Adaptation> one = rc.rows(pAssociation, 1);
		if (!one.isEmpty())
		{
			final SchemaNode n = Values.node(one.get(0), pField);
			if (n != null)
			{
				return n.getLabel(rc.locale);
			}
		}
		return pField;
	}

	static String pivot(final RenderContext rc, final JsonNode b)
	{
		final String a = b.path("a").asText("");
		final String rowsF = b.path("rows").asText("");
		final String colsF = b.path("cols").asText("");
		if (a.isEmpty() || rowsF.isEmpty() || colsF.isEmpty())
		{
			return MoreBlocks.setup(rc);
		}
		if (rc.creating)
		{
			return MoreBlocks.creatingNote(rc);
		}
		final String valueF = b.path("value").path("field").asText("");
		final Map<String, String> cells = new LinkedHashMap<String, String>();
		final TreeSet<String> rowKeys = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		final TreeSet<String> colKeys = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		for (final Adaptation r : rc.rows(a, MoreBlocks.ROWS_CAP))
		{
			final String rk = Values.display(r, rowsF, rc.locale);
			final String ck = Values.display(r, colsF, rc.locale);
			if (rk.isEmpty() || ck.isEmpty())
			{
				continue;
			}
			rowKeys.add(rk);
			colKeys.add(ck);
			final String v = valueF.isEmpty() ? "✓" : Values.display(r, valueF, rc.locale);
			final String key = rk + "\u0001" + ck;
			cells.put(key, cells.containsKey(key) && !valueF.isEmpty() ? cells.get(key) + ", " + v : v);
		}
		if (rowKeys.isEmpty())
		{
			return MoreBlocks.empty(MoreBlocks.t("noRow", rc));
		}
		final List<String> cols = new ArrayList<String>(colKeys);
		final List<String> rows = new ArrayList<String>(rowKeys);
		final StringBuilder h = new StringBuilder("<div class=\"ldf-pv\"><table><thead><tr><th></th>");
		for (int c = 0; c < cols.size() && c < 30; c++)
		{
			h.append("<th>").append(MoreBlocks.esc(cols.get(c))).append("</th>");
		}
		h.append("</tr></thead><tbody>");
		for (int r = 0; r < rows.size() && r < 60; r++)
		{
			h.append("<tr><th>").append(MoreBlocks.esc(rows.get(r))).append("</th>");
			for (int c = 0; c < cols.size() && c < 30; c++)
			{
				final String v = cells.get(rows.get(r) + "\u0001" + cols.get(c));
				h.append(v == null ? "<td class=\"ldf-pv-0\">·</td>" : "<td" + ("✓".equals(v) ? " class=\"ldf-pv-y\"" : "") + ">" + MoreBlocks.esc(v) + "</td>");
			}
			h.append("</tr>");
		}
		h.append("</tbody></table></div>");
		if (rows.size() > 60)
		{
			h.append(MoreBlocks.empty(RuntimeText.get("more", rc.locale, Integer.valueOf(rows.size() - 60))));
		}
		return h.toString();
	}

	// ------------------------------------------------------------------------------------------------ linked record, contacts

	/** The record a foreign-key field of the form points to, or null. */
	static Adaptation linked(final RenderContext rc, final String pFk)
	{
		final SchemaNode n = rc.node(pFk);
		if (n == null || n.getFacetOnTableReference() == null)
		{
			return null;
		}
		try
		{
			final SchemaFacetTableRef ref = n.getFacetOnTableReference();
			return ref.getLinkedRecord(rc.context.getValueContext());
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	static String linkedBlock(final RenderContext rc, final JsonNode b)
	{
		final String fk = b.path("fk").asText("");
		if (rc.node(fk) == null)
		{
			return MoreBlocks.setup(rc);
		}
		final Adaptation target = MoreBlocks.linked(rc, fk);
		if (target == null)
		{
			return MoreBlocks.empty(MoreBlocks.t("noLinked", rc));
		}
		final StringBuilder h = new StringBuilder("<div class=\"ldf-lk\"><b class=\"ldf-lk-t\">").append(MoreBlocks.esc(target.getLabel(rc.locale))).append("</b>");
		for (final JsonNode p : b.path("fields"))
		{
			final SchemaNode n = Values.node(target, p.asText());
			if (n == null)
			{
				continue;
			}
			final String v = Values.display(target, p.asText(), rc.locale);
			h.append("<div class=\"ldf-ro\"><span class=\"ldf-l\">").append(MoreBlocks.esc(n.getLabel(rc.locale))).append("</span><span class=\"ldf-v").append(v.isEmpty() ? " ldf-empty" : "").append("\">")
				.append(MoreBlocks.esc(v.isEmpty() ? "—" : v)).append("</span></div>");
		}
		if (b.path("open").asBoolean(true))
		{
			h.append("<div><a class=\"ldf-btn\" target=\"_blank\" rel=\"noopener\" href=\"").append(MoreBlocks.esc(MoreBlocks.recordUrl(rc, target))).append("\">")
				.append(MoreBlocks.esc(MoreBlocks.t("openRecord", rc))).append("</a></div>");
		}
		return h.append("</div>").toString();
	}

	static String recordUrl(final RenderContext rc, final Adaptation pRecord)
	{
		try
		{
			return rc.context.getURLForSelection(pRecord);
		}
		catch (final RuntimeException ex)
		{
			return "#";
		}
	}

	static String contacts(final RenderContext rc, final JsonNode b)
	{
		final String a = b.path("a").asText("");
		if (a.isEmpty())
		{
			return MoreBlocks.setup(rc);
		}
		if (rc.creating)
		{
			return MoreBlocks.creatingNote(rc);
		}
		final List<Adaptation> rows = rc.rows(a, 60);
		if (rows.isEmpty())
		{
			return MoreBlocks.empty(MoreBlocks.t("noRow", rc));
		}
		final String main = b.path("main").asText("");
		if (!main.isEmpty())
		{
			rows.sort((x, y) -> Boolean.compare(Boolean.TRUE.equals(Values.raw(y, main)), Boolean.TRUE.equals(Values.raw(x, main))));
		}
		final StringBuilder h = new StringBuilder("<div class=\"ldf-cards\">");
		for (final Adaptation r : rows)
		{
			final String name = b.path("name").asText("").isEmpty() ? r.getLabel(rc.locale) : Values.display(r, b.path("name").asText(), rc.locale);
			final boolean isMain = !main.isEmpty() && Boolean.TRUE.equals(Values.raw(r, main));
			h.append("<div class=\"ldf-card ldf-ct").append(isMain ? " ldf-main" : "").append("\"><span class=\"ldf-av\">").append(MoreBlocks.esc(MoreBlocks.initials(name))).append("</span><div class=\"ldf-ct-b\"><b>")
				.append(MoreBlocks.esc(name.isEmpty() ? r.getLabel(rc.locale) : name)).append("</b>");
			if (isMain)
			{
				h.append(" <span class=\"ldf-badge ldf-t-accent\">").append(MoreBlocks.esc(MoreBlocks.t("mainContact", rc))).append("</span>");
			}
			final String role = b.path("role").asText("").isEmpty() ? "" : Values.display(r, b.path("role").asText(), rc.locale);
			if (!role.isEmpty())
			{
				h.append("<div class=\"ldf-s\">").append(MoreBlocks.esc(role)).append("</div>");
			}
			final String email = b.path("email").asText("").isEmpty() ? "" : Values.display(r, b.path("email").asText(), rc.locale);
			if (!email.isEmpty())
			{
				h.append("<div class=\"ldf-ct-l\">").append(DisplayWidgets.email(rc, email)).append("</div>");
			}
			final String phone = b.path("phone").asText("").isEmpty() ? "" : Values.display(r, b.path("phone").asText(), rc.locale);
			if (!phone.isEmpty())
			{
				h.append("<div class=\"ldf-ct-l\">").append(DisplayWidgets.phone(rc, phone)).append("</div>");
			}
			h.append("</div></div>");
		}
		return h.append("</div>").toString();
	}

	static String initials(final String pName)
	{
		final StringBuilder b = new StringBuilder();
		for (final String w : pName.trim().split("\\s+"))
		{
			if (!w.isEmpty() && b.length() < 2 && Character.isLetterOrDigit(w.charAt(0)))
			{
				b.append(Character.toUpperCase(w.charAt(0)));
			}
		}
		return b.length() == 0 ? "?" : b.toString();
	}

	// ------------------------------------------------------------------------------------------------ gallery, documents

	static String gallery(final RenderContext rc, final JsonNode b)
	{
		final List<Media.Asset> assets = new ArrayList<Media.Asset>();
		final List<String> captions = new ArrayList<String>();
		final String a = b.path("a").asText("");
		final String media = b.path("media").asText("");
		if (!a.isEmpty() && !media.isEmpty())
		{
			if (rc.creating)
			{
				return MoreBlocks.creatingNote(rc);
			}
			for (final Adaptation r : rc.rows(a, 60))
			{
				final Media.Asset m = Media.of(r, Values.raw(r, media), rc.locale);
				if (m != null)
				{
					assets.add(m);
					final String cap = b.path("caption").asText("");
					captions.add(cap.isEmpty() ? r.getLabel(rc.locale) : Values.display(r, cap, rc.locale));
				}
			}
		}
		for (final JsonNode p : b.path("fields"))
		{
			final Object v = rc.raw(p.asText());
			final List<?> list = v instanceof List ? (List<?>) v : Collections.singletonList(v);
			for (final Object o : list)
			{
				final Media.Asset m = Media.of(rc.record, o, rc.locale);
				if (m != null)
				{
					assets.add(m);
					captions.add(rc.label(p.asText()));
				}
			}
		}
		if (assets.isEmpty())
		{
			return a.isEmpty() && b.path("fields").size() == 0 ? MoreBlocks.setup(rc) : MoreBlocks.empty(MoreBlocks.t("noImage", rc));
		}
		final int per = Math.max(1, Math.min(8, b.path("perRow").asInt(4)));
		final StringBuilder h = new StringBuilder("<div class=\"ldf-gal\" style=\"grid-template-columns:repeat(").append(per).append(",minmax(0,1fr))\">");
		for (int i = 0; i < assets.size(); i++)
		{
			final Media.Asset m = assets.get(i);
			h.append("<a class=\"ldf-gal-i\" href=\"").append(MoreBlocks.esc(m.url)).append("\" target=\"_blank\" rel=\"noopener\">");
			if (m.isImage())
			{
				h.append("<img loading=\"lazy\" alt=\"").append(MoreBlocks.esc(captions.get(i))).append("\" src=\"").append(MoreBlocks.esc(m.url)).append("\">");
			}
			else
			{
				h.append("<span class=\"ldf-file\">").append(MoreBlocks.esc(Media.extension(m.url).toUpperCase(Locale.ROOT))).append("</span>");
			}
			h.append("<span>").append(MoreBlocks.esc(captions.get(i))).append("</span></a>");
		}
		return h.append("</div>").toString();
	}

	static String documents(final RenderContext rc, final JsonNode b)
	{
		final String a = b.path("a").asText("");
		final String file = b.path("file").asText("");
		if (a.isEmpty() || file.isEmpty())
		{
			return MoreBlocks.setup(rc);
		}
		if (rc.creating)
		{
			return MoreBlocks.creatingNote(rc);
		}
		final List<Adaptation> rows = rc.rows(a, 100);
		if (rows.isEmpty())
		{
			return MoreBlocks.empty(MoreBlocks.t("noRow", rc));
		}
		final LocalDate today = LocalDate.now();
		final StringBuilder h = new StringBuilder("<div class=\"ldf-docs\">");
		for (final Adaptation r : rows)
		{
			final Media.Asset m = Media.of(r, Values.raw(r, file), rc.locale);
			final String name = !b.path("name").asText("").isEmpty() ? Values.display(r, b.path("name").asText(), rc.locale) : m != null && !m.name.isEmpty() ? m.name : r.getLabel(rc.locale);
			final String ext = m == null ? "" : (m.extension == null || m.extension.isEmpty() ? Media.extension(m.url) : m.extension).toUpperCase(Locale.ROOT);
			h.append("<div class=\"ldf-doc\"><span class=\"ldf-file\">").append(MoreBlocks.esc(ext.isEmpty() ? "DOC" : ext.length() > 4 ? ext.substring(0, 4) : ext)).append("</span><div class=\"ldf-ct-b\"><b>")
				.append(MoreBlocks.esc(name)).append("</b><div class=\"ldf-s\">");
			final List<String> meta = new ArrayList<String>();
			if (!b.path("docType").asText("").isEmpty())
			{
				final String ty = Values.display(r, b.path("docType").asText(), rc.locale);
				if (!ty.isEmpty())
				{
					meta.add(MoreBlocks.esc(ty));
				}
			}
			if (!b.path("date").asText("").isEmpty())
			{
				final String d = Values.display(r, b.path("date").asText(), rc.locale);
				final LocalDate ld = Values.date(Values.raw(r, b.path("date").asText()));
				if (!d.isEmpty())
				{
					final boolean expired = b.path("expiry").asBoolean(false) && ld != null && ld.isBefore(today);
					meta.add(expired ? "<span class=\"ldf-badge ldf-t-bad\">" + MoreBlocks.esc(RuntimeText.get("expiredOn", rc.locale, d)) + "</span>" : MoreBlocks.esc(d));
				}
			}
			h.append(String.join(" · ", meta)).append("</div></div>");
			if (m != null)
			{
				h.append("<a class=\"ldf-btn\" href=\"").append(MoreBlocks.esc(m.url)).append("\" target=\"_blank\" rel=\"noopener\">").append(MoreBlocks.esc(MoreBlocks.t("openFile", rc))).append("</a>");
			}
			h.append("</div>");
		}
		return h.append("</div>").toString();
	}

	// ------------------------------------------------------------------------------------------------ links

	private static final Pattern VAR = Pattern.compile("\\$\\{\\s*\\.?(/[^}\\s]*)\\s*\\}");

	static String links(final RenderContext rc, final JsonNode b)
	{
		final StringBuilder h = new StringBuilder("<div class=\"ldf-links\">");
		int n = 0;
		for (final JsonNode i : b.path("items"))
		{
			final String template = i.path("url").asText("").trim();
			if (template.isEmpty() || !LText.hasText(i.get("label")))
			{
				continue;
			}
			final String url = MoreBlocks.fill(rc, template);
			if (url == null || !url.matches("(?i)^(https?://|mailto:|tel:|/).*"))
			{
				continue;
			}
			n++;
			h.append("<a class=\"ldf-btn").append(n == 1 && b.path("firstSolid").asBoolean(true) ? " ldf-solid" : "").append("\" href=\"").append(MoreBlocks.esc(url)).append("\"")
				.append(i.path("sameTab").asBoolean(false) ? " target=\"_top\"" : " target=\"_blank\" rel=\"noopener\"").append(">").append(MoreBlocks.esc(rc.text(i.get("label")))).append("</a>");
		}
		if (n == 0)
		{
			return b.path("items").size() == 0 ? MoreBlocks.setup(rc) : MoreBlocks.empty(MoreBlocks.t("noLink", rc));
		}
		return h.append("</div>").toString();
	}

	/** A URL template with ${./path} replaced by the record's values, URL-encoded; null when a value is missing. */
	static String fill(final RenderContext rc, final String pTemplate)
	{
		final Matcher whole = MoreBlocks.VAR.matcher(pTemplate.trim());
		if (whole.matches())
		{
			// the whole address comes from one field (a website): used as it is
			final String v = Values.key(rc.raw(whole.group(1))).trim();
			return v.isEmpty() ? null : v.matches("(?i)^(https?://|mailto:|tel:|/).*") ? v : "https://" + v;
		}
		final Matcher m = MoreBlocks.VAR.matcher(pTemplate);
		final StringBuffer out = new StringBuffer();
		while (m.find())
		{
			final Object v = rc.raw(m.group(1));
			final String s = Values.key(v);
			if (s.isEmpty())
			{
				return null;
			}
			m.appendReplacement(out, Matcher.quoteReplacement(URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")));
		}
		m.appendTail(out);
		return out.toString();
	}

	// ------------------------------------------------------------------------------------------------ validity, address

	static String validity(final RenderContext rc, final JsonNode b)
	{
		final String fromP = b.path("from").asText("");
		final String toP = b.path("to").asText("");
		if (fromP.isEmpty() && toP.isEmpty())
		{
			return MoreBlocks.setup(rc);
		}
		final LocalDate from = fromP.isEmpty() ? null : Values.date(rc.raw(fromP));
		final LocalDate to = toP.isEmpty() ? null : Values.date(rc.raw(toP));
		if (from == null && to == null)
		{
			return MoreBlocks.empty(MoreBlocks.t("noDates", rc));
		}
		final LocalDate today = LocalDate.now();
		final int warn = b.path("warnDays").asInt(30);
		String state;
		String tone;
		String detail = "";
		if (from != null && today.isBefore(from))
		{
			state = MoreBlocks.t("valUpcoming", rc);
			tone = "ldf-t-info";
			detail = RuntimeText.get("inDays", rc.locale, Long.valueOf(ChronoUnit.DAYS.between(today, from)));
		}
		else if (to != null && today.isAfter(to))
		{
			state = MoreBlocks.t("valExpired", rc);
			tone = "ldf-t-bad";
			detail = RuntimeText.get("daysAgo", rc.locale, Long.valueOf(ChronoUnit.DAYS.between(to, today)));
		}
		else
		{
			final long left = to == null ? -1 : ChronoUnit.DAYS.between(today, to);
			state = to != null && left <= warn ? MoreBlocks.t("valEnding", rc) : MoreBlocks.t("valActive", rc);
			tone = to != null && left <= warn ? "ldf-t-warn" : "ldf-t-ok";
			detail = to == null ? MoreBlocks.t("noEnd", rc) : RuntimeText.get("daysLeft", rc.locale, Long.valueOf(left));
		}
		final StringBuilder h = new StringBuilder("<div class=\"ldf-val\"><span class=\"ldf-badge ").append(tone).append("\">").append(MoreBlocks.esc(state)).append("</span><span class=\"ldf-more\">")
			.append(MoreBlocks.esc(detail)).append("</span></div>");
		if (from != null && to != null && !to.isBefore(from))
		{
			final long span = Math.max(1, ChronoUnit.DAYS.between(from, to));
			final long done = Math.max(0, Math.min(span, ChronoUnit.DAYS.between(from, today)));
			h.append("<div class=\"ldf-val-r\"><span>").append(MoreBlocks.esc(rc.display(fromP))).append("</span><span class=\"ldf-bar ").append(tone).append("\"><i style=\"width:")
				.append(100 * done / span).append("%\"></i></span><span>").append(MoreBlocks.esc(rc.display(toP))).append("</span></div>");
		}
		else
		{
			h.append("<div class=\"ldf-val-r\"><span>").append(MoreBlocks.esc(from == null ? "…" : rc.display(fromP))).append("</span><span class=\"ldf-bar\"></span><span>")
				.append(MoreBlocks.esc(to == null ? "…" : rc.display(toP))).append("</span></div>");
		}
		return h.toString();
	}

	// ------------------------------------------------------------------------------------------------ record information

	/**
	 * Who created the record and when, who changed it last and when: {@code Adaptation.getCreator()},
	 * {@code getTimeOfCreation()}, {@code getLastUser()}, {@code getTimeOfLastModification()}, the user's name through
	 * {@code DirectoryHandler.displayUser}. {@code createdBy}, {@code createdOn}, {@code modifiedBy}, {@code modifiedOn}
	 * (true by default) pick the items, {@code style} "line" puts them on one line, {@code relative} adds "3 days ago".
	 */
	static String recordInfo(final RenderContext rc, final JsonNode b)
	{
		final List<String> show = new ArrayList<String>();
		for (final String k : new String[] { "createdBy", "createdOn", "modifiedBy", "modifiedOn" })
		{
			if (b.path(k).asBoolean(true))
			{
				show.add(k);
			}
		}
		if (rc.creating || rc.record == null)
		{
			return MoreBlocks.empty(MoreBlocks.t("riNew", rc));
		}
		final Adaptation r = rc.record;
		final boolean relative = b.path("relative").asBoolean(true);
		final List<String[]> items = new ArrayList<String[]>();
		for (final String k : show)
		{
			switch (k)
			{
			case "createdBy":
				items.add(new String[] { MoreBlocks.t("riCreatedBy", rc), MoreBlocks.user(rc, r.getCreator()), "" });
				break;
			case "createdOn":
				items.add(new String[] { MoreBlocks.t("riCreatedOn", rc), MoreBlocks.dateTime(rc, r.getTimeOfCreation()), relative ? MoreBlocks.ago(rc, r.getTimeOfCreation()) : "" });
				break;
			case "modifiedBy":
				items.add(new String[] { MoreBlocks.t("riModifiedBy", rc), MoreBlocks.user(rc, r.getLastUser()), "" });
				break;
			case "modifiedOn":
				items.add(new String[] { MoreBlocks.t("riModifiedOn", rc), MoreBlocks.dateTime(rc, r.getTimeOfLastModification()), relative ? MoreBlocks.ago(rc, r.getTimeOfLastModification()) : "" });
				break;
			default:
				break;
			}
		}
		final boolean line = "line".equals(b.path("style").asText(""));
		final StringBuilder h = new StringBuilder(line ? "<div class=\"ldf-ri-line\">" : "<div class=\"ldf-ri\">");
		for (final String[] it : items)
		{
			if (line)
			{
				h.append("<span><span class=\"ldf-l\">").append(MoreBlocks.esc(it[0])).append("</span> <b>").append(MoreBlocks.esc(it[1])).append("</b>");
				if (!it[2].isEmpty())
				{
					h.append(" <span class=\"ldf-more\">(").append(MoreBlocks.esc(it[2])).append(")</span>");
				}
				h.append("</span>");
			}
			else
			{
				h.append("<div class=\"ldf-ri-i\"><span class=\"ldf-l\">").append(MoreBlocks.esc(it[0])).append("</span><b>").append(MoreBlocks.esc(it[1])).append("</b>");
				if (!it[2].isEmpty())
				{
					h.append("<span class=\"ldf-more\">").append(MoreBlocks.esc(it[2])).append("</span>");
				}
				h.append("</div>");
			}
		}
		return h.append("</div>").toString();
	}

	private static String user(final RenderContext rc, final com.orchestranetworks.service.UserReference pUser)
	{
		if (pUser == null)
		{
			return "—";
		}
		try
		{
			final String s = com.orchestranetworks.service.directory.DirectoryHandler.getInstance(rc.record.getHome().getRepository()).displayUser(pUser, rc.locale);
			return s == null || s.trim().isEmpty() ? pUser.getUserId() : s;
		}
		catch (final RuntimeException ex)
		{
			return pUser.getUserId();
		}
	}

	private static String dateTime(final RenderContext rc, final Date pDate)
	{
		return pDate == null ? "—" : java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, rc.locale).format(pDate);
	}

	private static String ago(final RenderContext rc, final Date pDate)
	{
		if (pDate == null)
		{
			return "";
		}
		final long days = ChronoUnit.DAYS.between(Values.date(pDate), LocalDate.now());
		return days <= 0 ? MoreBlocks.t("today", rc) : days == 1 ? MoreBlocks.t("riYesterday", rc) : RuntimeText.get("riAgo", rc.locale, Long.valueOf(days));
	}

	private static final Set<String> CITY_FIRST = new HashSet<String>(java.util.Arrays.asList("US", "CA", "AU", "NZ", "USA", "CAN", "AUS", "NZL"));
	private static final Set<String> ZIP_AFTER = new HashSet<String>(java.util.Arrays.asList("GB", "IE", "GBR", "IRL", "UK"));

	/** Countries that write the street number after the street name ("Hauptstraße 5"). */
	private static final Set<String> NUMBER_AFTER = new HashSet<String>(java.util.Arrays.asList("DE", "AT", "CH", "NL", "BE", "IT", "ES", "PT", "PL", "CZ", "SK",
		"DK", "SE", "NO", "FI", "HU", "HR", "SI", "GR", "DEU", "AUT", "CHE", "NLD", "BEL", "ITA", "ESP", "PRT", "POL", "CZE", "SVK", "DNK", "SWE", "NOR", "FIN", "HUN", "HRV",
		"SVN", "GRC"));

	static String address(final RenderContext rc, final JsonNode b)
	{
		final List<String> lines = new ArrayList<String>();
		for (final JsonNode p : b.path("lines"))
		{
			final String v = rc.display(p.asText());
			if (!v.isEmpty())
			{
				lines.add(v);
			}
		}
		// the street number goes on the first street line, before or after the street after the country's custom
		final String number = rc.display(b.path("number").asText(""));
		if (!number.isEmpty())
		{
			final String key = Values.key(rc.raw(b.path("country").asText(""))).toUpperCase(Locale.ROOT);
			final String fmt0 = b.path("format").asText("auto");
			final boolean after = "numberAfter".equals(b.path("numberPlace").asText("")) || !"numberBefore".equals(b.path("numberPlace").asText("")) && "auto".equals(fmt0) && MoreBlocks.NUMBER_AFTER.contains(key);
			if (lines.isEmpty())
			{
				lines.add(number);
			}
			else
			{
				lines.set(0, after ? lines.get(0) + " " + number : number + " " + lines.get(0));
			}
		}
		final String zip = rc.display(b.path("zip").asText(""));
		final String city = rc.display(b.path("city").asText(""));
		final String region = rc.display(b.path("region").asText(""));
		final String countryKey = Values.key(rc.raw(b.path("country").asText(""))).toUpperCase(Locale.ROOT);
		String country = rc.display(b.path("country").asText(""));
		if (country.length() == 2 || country.length() == 3)
		{
			final String name = DisplayWidgets.countryName(country, rc.locale);
			country = name == null ? country : name;
		}
		final String fmt = b.path("format").asText("auto");
		final boolean cityFirst = "cityFirst".equals(fmt) || "auto".equals(fmt) && MoreBlocks.CITY_FIRST.contains(countryKey);
		final boolean zipAfter = "zipAfter".equals(fmt) || "auto".equals(fmt) && MoreBlocks.ZIP_AFTER.contains(countryKey);
		if (cityFirst)
		{
			final String l = (city + (region.isEmpty() ? "" : ", " + region) + (zip.isEmpty() ? "" : " " + zip)).trim();
			if (!l.isEmpty())
			{
				lines.add(l);
			}
		}
		else if (zipAfter)
		{
			if (!city.isEmpty())
			{
				lines.add(city);
			}
			if (!region.isEmpty())
			{
				lines.add(region);
			}
			if (!zip.isEmpty())
			{
				lines.add(zip);
			}
		}
		else
		{
			final String l = (zip + " " + city).trim();
			if (!l.isEmpty())
			{
				lines.add(l);
			}
			if (!region.isEmpty())
			{
				lines.add(region);
			}
		}
		if (!country.isEmpty())
		{
			lines.add(country);
		}
		if (lines.isEmpty())
		{
			return b.path("city").asText("").isEmpty() && b.path("lines").size() == 0 ? MoreBlocks.setup(rc) : MoreBlocks.empty(MoreBlocks.t("noAddress", rc));
		}
		final StringBuilder h = new StringBuilder("<address class=\"ldf-addr\">");
		for (final String l : lines)
		{
			h.append(MoreBlocks.esc(l)).append("<br>");
		}
		h.append("</address>");
		if (b.path("mapLink").asBoolean(true))
		{
			h.append("<div class=\"ldf-more\"><a class=\"ldf-go\" target=\"_blank\" rel=\"noopener\" href=\"https://www.openstreetmap.org/search?query=")
				.append(MoreBlocks.esc(URLEncoder.encode(String.join(", ", lines), StandardCharsets.UTF_8))).append("\">").append(MoreBlocks.esc(MoreBlocks.t("openMap", rc))).append("</a></div>");
		}
		return h.toString();
	}

	// ------------------------------------------------------------------------------------------------ hierarchy

	static String hierarchy(final RenderContext rc, final JsonNode b)
	{
		final String parent = b.path("parent").asText("");
		final SchemaNode node = rc.node(parent);
		if (node == null || node.getFacetOnTableReference() == null)
		{
			return MoreBlocks.setup(rc);
		}
		final String label = b.path("label").asText("");
		final int depth = Math.max(1, Math.min(12, b.path("depth").asInt(6)));
		final String targetTable = node.getFacetOnTableReference().getTablePath().format();
		final boolean selfTable = targetTable.equals(rc.context.getCurrentTable().getTablePath().format());
		final String up = b.path("up").asText(selfTable ? parent : "");
		final List<String> chain = new ArrayList<String>();
		final Set<String> seen = new HashSet<String>();
		Adaptation cur = MoreBlocks.linked(rc, parent);
		boolean cut = false;
		while (cur != null)
		{
			if (!seen.add(cur.toXPathPredicateString()))
			{
				break;
			}
			if (chain.size() >= depth)
			{
				cut = true;
				break;
			}
			final String text = label.isEmpty() ? cur.getLabel(rc.locale) : Values.display(cur, label, rc.locale);
			chain.add(0, "<a href=\"" + MoreBlocks.esc(MoreBlocks.recordUrl(rc, cur)) + "\" target=\"_blank\" rel=\"noopener\">" + MoreBlocks.esc(text.isEmpty() ? cur.getLabel(rc.locale) : text) + "</a>");
			if (up.isEmpty())
			{
				break;
			}
			final SchemaNode upNode = Values.node(cur, up);
			if (upNode == null || upNode.getFacetOnTableReference() == null)
			{
				break;
			}
			try
			{
				cur = upNode.getFacetOnTableReference().getLinkedRecord(cur);
			}
			catch (final RuntimeException ex)
			{
				cur = null;
			}
		}
		final StringBuilder h = new StringBuilder("<nav class=\"ldf-crumb\">");
		if (cut)
		{
			h.append("<span>…</span><i>›</i>");
		}
		if (chain.isEmpty())
		{
			h.append("<span class=\"ldf-more\">").append(MoreBlocks.esc(MoreBlocks.t("topLevel", rc))).append("</span>");
		}
		for (final String c : chain)
		{
			h.append(c).append("<i>›</i>");
		}
		h.append("<b>").append(MoreBlocks.esc(rc.creating ? RuntimeText.get("newRecord", rc.locale) : rc.record == null ? "" : rc.record.getLabel(rc.locale))).append("</b></nav>");
		final String children = b.path("children").asText("");
		if (!children.isEmpty() && rc.record != null)
		{
			final int n = rc.count(children);
			if (n > 0)
			{
				h.append("<div class=\"ldf-kids\">");
				for (final Adaptation r : rc.rows(children, 12))
				{
					h.append("<a href=\"").append(MoreBlocks.esc(MoreBlocks.recordUrl(rc, r))).append("\" target=\"_blank\" rel=\"noopener\">").append(MoreBlocks.esc(r.getLabel(rc.locale))).append("</a>");
				}
				if (n > 12)
				{
					h.append("<span class=\"ldf-more\">").append(MoreBlocks.esc(RuntimeText.get("more", rc.locale, Integer.valueOf(n - 12)))).append("</span>");
				}
				h.append("</div>");
			}
		}
		return h.toString();
	}
}
