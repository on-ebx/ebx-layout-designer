package com.on.ebx.layout.runtime;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.schema.SchemaNode;

/**
 * The blocks added in 1.22.0, all over the rows of an association of the record: {@code heatmap} (a matrix of two
 * fields, e.g. probability × impact of risks), {@code gantt} (bars between two dates, planned and actual),
 * {@code compare} (two measures side by side with the variance, e.g. budget against actual) and {@code coverage}
 * (how many rows have a field filled, and which do not, e.g. local accounts mapped to the group chart).
 */
final class ChartBlocks
{
	private static final int ROWS_CAP = 5000;

	private ChartBlocks()
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
		return "<div class=\"ldf-more\">" + ChartBlocks.esc(pText) + "</div>";
	}

	/** The rows of the block's association, null when the block cannot render yet (its message is then in pOut). */
	private static List<Adaptation> rows(final RenderContext rc, final JsonNode b, final String[] pOut, final String... pRequired)
	{
		if (b.path("a").asText("").isEmpty())
		{
			pOut[0] = ChartBlocks.note(ChartBlocks.t("setupBlock", rc));
			return null;
		}
		for (final String r : pRequired)
		{
			if (b.path(r).asText("").isEmpty())
			{
				pOut[0] = ChartBlocks.note(ChartBlocks.t("setupBlock", rc));
				return null;
			}
		}
		if (rc.creating)
		{
			pOut[0] = ChartBlocks.note(ChartBlocks.t("afterCreate", rc));
			return null;
		}
		final List<Adaptation> rows = ChartBlocks.only(rc.rows(b.path("a").asText(), ChartBlocks.ROWS_CAP), b);
		if (rows.isEmpty())
		{
			pOut[0] = ChartBlocks.note(ChartBlocks.t("noRow", rc));
			return null;
		}
		return rows;
	}

	/** {@code "only": {"field": "status", "values": ["OPEN"]}} keeps the rows whose field holds one of the values. */
	private static List<Adaptation> only(final List<Adaptation> pRows, final JsonNode b)
	{
		final JsonNode o = b.path("only");
		final String f = o.path("field").asText("");
		if (f.isEmpty() || !o.path("values").isArray())
		{
			return pRows;
		}
		final Set<String> keep = new HashSet<String>();
		for (final JsonNode v : o.path("values"))
		{
			keep.add(v.asText().toLowerCase(Locale.ROOT));
		}
		final boolean not = o.path("not").asBoolean(false);
		final List<Adaptation> out = new ArrayList<Adaptation>();
		for (final Adaptation r : pRows)
		{
			if (keep.contains(Values.key(Values.raw(r, f)).toLowerCase(Locale.ROOT)) != not)
			{
				out.add(r);
			}
		}
		return out;
	}

	private static String label(final RenderContext rc, final Adaptation r, final String pField)
	{
		if (!pField.isEmpty())
		{
			final String d = Values.display(r, pField, rc.locale);
			if (!d.isEmpty())
			{
				return d;
			}
		}
		try
		{
			return r.getLabel(rc.locale);
		}
		catch (final RuntimeException ex)
		{
			return r.getOccurrencePrimaryKey().format();
		}
	}

	/** The ordered keys of an axis: the block's list, else the field's enumeration, else the values met, sorted. */
	private static List<String> axis(final RenderContext rc, final JsonNode pList, final List<Adaptation> rows, final String pField)
	{
		final List<String> out = new ArrayList<String>();
		for (final JsonNode s : pList)
		{
			if (!s.asText("").isEmpty())
			{
				out.add(s.asText());
			}
		}
		if (out.isEmpty())
		{
			final SchemaNode n = Values.node(rows.get(0), pField);
			try
			{
				final List<?> values = n == null ? null : n.getEnumerationList(rc.context.getValueContext());
				if (values != null)
				{
					for (final Object v : values)
					{
						out.add(Values.key(v));
					}
				}
			}
			catch (final RuntimeException ex)
			{
				// no enumeration
			}
		}
		if (out.isEmpty())
		{
			final TreeSet<String> met = new TreeSet<String>();
			for (final Adaptation r : rows)
			{
				final String k = Values.key(Values.raw(r, pField));
				if (!k.isEmpty())
				{
					met.add(k);
				}
			}
			out.addAll(met);
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------ heatmap

	static String heatmap(final RenderContext rc, final JsonNode b)
	{
		final String[] msg = new String[1];
		final String x = b.path("x").asText("");
		final String y = b.path("y").asText("");
		final List<Adaptation> rows = ChartBlocks.rows(rc, b, msg, "x", "y");
		if (rows == null)
		{
			return msg[0];
		}
		final List<String> xs = ChartBlocks.axis(rc, b.path("xSteps"), rows, x);
		final List<String> ys = ChartBlocks.axis(rc, b.path("ySteps"), rows, y);
		final Map<String, String> names = new HashMap<String, String>();
		final Map<String, List<String>> cells = new HashMap<String, List<String>>();
		int outside = 0;
		for (final Adaptation r : rows)
		{
			final String kx = Values.key(Values.raw(r, x));
			final String ky = Values.key(Values.raw(r, y));
			final int ix = ChartBlocks.indexOf(xs, kx);
			final int iy = ChartBlocks.indexOf(ys, ky);
			if (ix < 0 || iy < 0)
			{
				outside++;
				continue;
			}
			names.put("x" + ix, Values.display(r, x, rc.locale));
			names.put("y" + iy, Values.display(r, y, rc.locale));
			cells.computeIfAbsent(ix + ":" + iy, k -> new ArrayList<String>()).add(ChartBlocks.label(rc, r, b.path("label").asText("")));
		}
		final StringBuilder h = new StringBuilder("<div class=\"ldf-hm\" style=\"grid-template-columns:minmax(70px,max-content) repeat(").append(xs.size()).append(",minmax(44px,1fr))\">");
		final double maxScore = (double) xs.size() * ys.size();
		// the highest y on top, like a risk matrix
		for (int iy = ys.size() - 1; iy >= 0; iy--)
		{
			h.append("<span class=\"ldf-hm-y\">").append(ChartBlocks.esc(names.getOrDefault("y" + iy, ys.get(iy)))).append("</span>");
			for (int ix = 0; ix < xs.size(); ix++)
			{
				final List<String> in = cells.get(ix + ":" + iy);
				final double score = (ix + 1) * (iy + 1) / maxScore;
				final String tone = score > 0.5 ? "bad" : score > 0.2 ? "warn" : "ok";
				h.append("<span class=\"ldf-hm-c ldf-t-").append(tone).append(in == null ? " ldf-hm-0" : "").append('"');
				if (in != null)
				{
					h.append(" title=\"").append(ChartBlocks.esc(String.join("\n", in))).append('"');
				}
				h.append('>').append(in == null ? "" : String.valueOf(in.size())).append("</span>");
			}
		}
		h.append("<span></span>");
		for (int ix = 0; ix < xs.size(); ix++)
		{
			h.append("<span class=\"ldf-hm-x\">").append(ChartBlocks.esc(names.getOrDefault("x" + ix, xs.get(ix)))).append("</span>");
		}
		h.append("</div><div class=\"ldf-hm-axes\"><span>↑ ").append(ChartBlocks.esc(ChartBlocks.fieldLabel(rc, rows.get(0), y))).append("</span><span>→ ")
			.append(ChartBlocks.esc(ChartBlocks.fieldLabel(rc, rows.get(0), x))).append("</span>");
		if (outside > 0)
		{
			h.append("<span>").append(ChartBlocks.esc(ChartBlocks.t("hmOutside", rc).replace("{n}", String.valueOf(outside)))).append("</span>");
		}
		return h.append("</div>").toString();
	}

	private static int indexOf(final List<String> pList, final String pKey)
	{
		for (int i = 0; i < pList.size(); i++)
		{
			if (pList.get(i).equalsIgnoreCase(pKey))
			{
				return i;
			}
		}
		return -1;
	}

	private static String fieldLabel(final RenderContext rc, final Adaptation r, final String pField)
	{
		final SchemaNode n = Values.node(r, pField);
		return n == null ? pField : n.getLabel(rc.locale);
	}

	// ------------------------------------------------------------------------------------------------ gantt

	static String gantt(final RenderContext rc, final JsonNode b)
	{
		final String[] msg = new String[1];
		final List<Adaptation> rows = ChartBlocks.rows(rc, b, msg, "start", "end");
		if (rows == null)
		{
			return msg[0];
		}
		final String actualStart = b.path("actualStart").asText("");
		final String actualEnd = b.path("actualEnd").asText("");
		final String status = b.path("status").asText("");
		final List<Object[]> bars = new ArrayList<Object[]>();
		LocalDate min = null;
		LocalDate max = null;
		for (final Adaptation r : rows)
		{
			final LocalDate s = Values.date(Values.raw(r, b.path("start").asText()));
			final LocalDate e = Values.date(Values.raw(r, b.path("end").asText()));
			final LocalDate as = actualStart.isEmpty() ? null : Values.date(Values.raw(r, actualStart));
			final LocalDate ae = actualEnd.isEmpty() ? null : Values.date(Values.raw(r, actualEnd));
			if (s == null && as == null)
			{
				continue;
			}
			for (final LocalDate d : new LocalDate[] { s, e, as, ae })
			{
				if (d != null)
				{
					min = min == null || d.isBefore(min) ? d : min;
					max = max == null || d.isAfter(max) ? d : max;
				}
			}
			bars.add(new Object[] { r, s, e, as, ae });
		}
		if (bars.isEmpty())
		{
			return ChartBlocks.note(ChartBlocks.t("noDates", rc));
		}
		bars.sort((p, q) -> ChartBlocks.first((LocalDate) p[1], (LocalDate) p[3]).compareTo(ChartBlocks.first((LocalDate) q[1], (LocalDate) q[3])));
		final LocalDate today = LocalDate.now();
		final LocalDate from = min.withDayOfMonth(1);
		final LocalDate to = max.plusMonths(1).withDayOfMonth(1);
		final double span = Math.max(1, ChronoUnit.DAYS.between(from, to));
		final StringBuilder h = new StringBuilder("<div class=\"ldf-gt\"><span></span><div class=\"ldf-gt-sc\">");
		// month or quarter ticks
		final long months = ChronoUnit.MONTHS.between(from, to);
		final int step = months > 24 ? 6 : months > 9 ? 3 : 1;
		for (LocalDate m = from; m.isBefore(to); m = m.plusMonths(step))
		{
			h.append("<span style=\"left:").append(ChartBlocks.pct(ChronoUnit.DAYS.between(from, m) / span)).append("\">")
				.append(ChartBlocks.esc(m.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, rc.locale) + " " + String.valueOf(m.getYear()).substring(2))).append("</span>");
		}
		h.append("</div>");
		final String todayLine = !today.isBefore(from) && today.isBefore(to)
			? "<i class=\"ldf-gt-now\" style=\"left:" + ChartBlocks.pct(ChronoUnit.DAYS.between(from, today) / span) + "\"></i>" : "";
		for (final Object[] bar : bars)
		{
			final Adaptation r = (Adaptation) bar[0];
			final LocalDate s = (LocalDate) bar[1];
			final LocalDate e = (LocalDate) bar[2];
			final LocalDate as = (LocalDate) bar[3];
			final LocalDate ae = (LocalDate) bar[4];
			String tone = status.isEmpty() ? "" : Values.tone(b.path("colors"), Values.key(Values.raw(r, status)), Values.display(r, status, rc.locale));
			final boolean late = e != null && (ae != null ? ae.isAfter(e) : today.isAfter(e) && as != null);
			if (tone.isEmpty())
			{
				tone = late ? " ldf-t-bad" : ae != null ? " ldf-t-ok" : " ldf-t-accent";
			}
			h.append("<a class=\"ldf-gt-l\" href=\"").append(ChartBlocks.esc(MoreBlocks.recordUrl(rc, r))).append("\">").append(ChartBlocks.esc(ChartBlocks.label(rc, r, b.path("label").asText(""))))
				.append("</a><div class=\"ldf-gt-tr\" title=\"").append(ChartBlocks.esc(ChartBlocks.range(s, e) + (as == null && ae == null ? "" : "\n" + ChartBlocks.t("gtActual", rc) + " " + ChartBlocks.range(as, ae))))
				.append("\">").append(todayLine);
			if (s != null)
			{
				final LocalDate end = e == null ? s : e;
				h.append("<i class=\"ldf-gt-p\" style=\"left:").append(ChartBlocks.pct(ChronoUnit.DAYS.between(from, s) / span)).append(";width:")
					.append(ChartBlocks.pct(Math.max(1, ChronoUnit.DAYS.between(s, end)) / span)).append("\"></i>");
			}
			if (as != null || ae != null)
			{
				final LocalDate st = as != null ? as : s;
				final LocalDate en = ae != null ? ae : today.isAfter(st) ? today : st;
				h.append("<i class=\"ldf-gt-a").append(tone).append("\" style=\"left:").append(ChartBlocks.pct(ChronoUnit.DAYS.between(from, st) / span)).append(";width:")
					.append(ChartBlocks.pct(Math.max(1, ChronoUnit.DAYS.between(st, en)) / span)).append("\"></i>");
			}
			h.append("</div>");
		}
		h.append("</div><div class=\"ldf-hm-axes\"><span><i class=\"ldf-gt-k ldf-gt-p\"></i>").append(ChartBlocks.esc(ChartBlocks.t("gtPlanned", rc)))
			.append("</span><span><i class=\"ldf-gt-k ldf-gt-a ldf-t-ok\"></i>").append(ChartBlocks.esc(ChartBlocks.t("gtOnTime", rc))).append("</span><span><i class=\"ldf-gt-k ldf-gt-a ldf-t-accent\"></i>")
			.append(ChartBlocks.esc(ChartBlocks.t("gtRunning", rc))).append("</span><span><i class=\"ldf-gt-k ldf-gt-a ldf-t-bad\"></i>")
			.append(ChartBlocks.esc(ChartBlocks.t("gtLate", rc))).append("</span></div>");
		return h.toString();
	}

	private static LocalDate first(final LocalDate a, final LocalDate b)
	{
		return a != null ? a : b != null ? b : LocalDate.MAX;
	}

	private static String range(final LocalDate a, final LocalDate b)
	{
		return (a == null ? "…" : a.toString()) + " → " + (b == null ? "…" : b.toString());
	}

	private static String pct(final double f)
	{
		return String.format(Locale.ROOT, "%.2f%%", 100 * Math.max(0, Math.min(1, f)));
	}

	// ------------------------------------------------------------------------------------------------ compare

	static String compare(final RenderContext rc, final JsonNode b)
	{
		final String[] msg = new String[1];
		final List<Adaptation> rows = ChartBlocks.rows(rc, b, msg, "planned", "actual");
		if (rows == null)
		{
			return msg[0];
		}
		final String by = b.path("by").asText("");
		final String planned = b.path("planned").asText();
		final String actual = b.path("actual").asText();
		final Map<String, double[]> groups = new LinkedHashMap<String, double[]>();
		final double[] total = new double[2];
		for (final Adaptation r : rows)
		{
			String k = by.isEmpty() ? "" : Values.display(r, by, rc.locale);
			if (!by.isEmpty() && k.isEmpty())
			{
				k = "—";
			}
			final double[] g = groups.computeIfAbsent(k, x -> new double[2]);
			final Double p = Values.number(Values.raw(r, planned));
			final Double a = Values.number(Values.raw(r, actual));
			g[0] += p == null ? 0 : p.doubleValue();
			g[1] += a == null ? 0 : a.doubleValue();
			total[0] += p == null ? 0 : p.doubleValue();
			total[1] += a == null ? 0 : a.doubleValue();
		}
		final boolean higherIsBetter = b.path("higherIsBetter").asBoolean(false);
		final int dec = b.path("decimals").asInt(0);
		final String unit = b.path("unit").asText("");
		final String pName = LText.pick(b.get("plannedLabel"), rc.locale, rc.locale);
		final String aName = LText.pick(b.get("actualLabel"), rc.locale, rc.locale);
		double max = 0;
		for (final double[] g : groups.values())
		{
			max = Math.max(max, Math.max(g[0], g[1]));
		}
		final StringBuilder h = new StringBuilder("<div class=\"ldf-cmp-tot\">");
		h.append(ChartBlocks.figure(pName.isEmpty() ? ChartBlocks.fieldLabel(rc, rows.get(0), planned) : pName, total[0], dec, unit, "", rc));
		h.append(ChartBlocks.figure(aName.isEmpty() ? ChartBlocks.fieldLabel(rc, rows.get(0), actual) : aName, total[1], dec, unit, "", rc));
		h.append(ChartBlocks.figure(ChartBlocks.t("cmpVariance", rc), total[1] - total[0], dec, unit, ChartBlocks.varTone(total, higherIsBetter), rc));
		h.append("</div>");
		if (!by.isEmpty())
		{
			h.append("<div class=\"ldf-cmp\">");
			for (final Map.Entry<String, double[]> e : groups.entrySet())
			{
				final double[] g = e.getValue();
				h.append("<span class=\"ldf-bars-l\">").append(ChartBlocks.esc(e.getKey())).append("</span><span class=\"ldf-cmp-b\"><span class=\"ldf-bar ldf-cmp-p\"><i style=\"width:")
					.append(ChartBlocks.pct(max <= 0 ? 0 : g[0] / max)).append("\"></i></span><span class=\"ldf-bar").append(ChartBlocks.varTone(g, higherIsBetter).isEmpty() ? " ldf-t-accent" : ChartBlocks.varTone(g, higherIsBetter))
					.append("\"><i style=\"width:").append(ChartBlocks.pct(max <= 0 ? 0 : g[1] / max)).append("\"></i></span></span><b>")
					.append(ChartBlocks.esc(Values.number(g[1], dec, rc.locale))).append(" / ").append(ChartBlocks.esc(Values.number(g[0], dec, rc.locale))).append("</b><small class=\"")
					.append(ChartBlocks.varTone(g, higherIsBetter).trim()).append("\">").append(ChartBlocks.esc(ChartBlocks.variance(g, rc))).append("</small>");
			}
			h.append("</div>");
		}
		return h.toString();
	}

	/** bad when the actual is worse than planned, ok when better or equal; "" when nothing was planned. */
	private static String varTone(final double[] g, final boolean pHigherIsBetter)
	{
		if (g[0] == 0 && g[1] == 0)
		{
			return "";
		}
		final boolean worse = pHigherIsBetter ? g[1] < g[0] : g[1] > g[0];
		return worse ? " ldf-t-bad" : " ldf-t-ok";
	}

	private static String variance(final double[] g, final RenderContext rc)
	{
		if (g[0] == 0)
		{
			return "—";
		}
		final double v = 100 * (g[1] - g[0]) / Math.abs(g[0]);
		return (v > 0 ? "+" : "") + Values.number(v, 1, rc.locale) + " %";
	}

	private static String figure(final String pLabel, final double pValue, final int pDecimals, final String pUnit, final String pTone, final RenderContext rc)
	{
		return "<div class=\"ldf-fact" + pTone + "\"><span>" + ChartBlocks.esc(pLabel) + "</span><b>" + ChartBlocks.esc(Values.number(pValue, pDecimals, rc.locale) + (pUnit.isEmpty() ? "" : " " + pUnit))
			+ "</b></div>";
	}

	// ------------------------------------------------------------------------------------------------ coverage

	static String coverage(final RenderContext rc, final JsonNode b)
	{
		final String[] msg = new String[1];
		final List<Adaptation> rows = ChartBlocks.rows(rc, b, msg, "field");
		if (rows == null)
		{
			return msg[0];
		}
		final String field = b.path("field").asText();
		final List<Adaptation> missing = new ArrayList<Adaptation>();
		for (final Adaptation r : rows)
		{
			if (!Values.filled(Values.raw(r, field)))
			{
				missing.add(r);
			}
		}
		final int n = rows.size();
		final int ok = n - missing.size();
		final double f = (double) ok / n;
		final double target = b.path("target").asDouble(100) / 100;
		final String tone = f >= target ? "ok" : f >= target * 0.8 ? "warn" : "bad";
		final StringBuilder h = new StringBuilder("<div class=\"ldf-cp ldf-t-").append(tone).append("\"><b class=\"ldf-cp-n\">").append(Math.round(100 * f)).append(" %</b><span class=\"ldf-bar\"><i style=\"width:")
			.append(ChartBlocks.pct(f)).append("\"></i></span><span>").append(ChartBlocks.esc(ChartBlocks.t("covOf", rc).replace("{n}", String.valueOf(ok)).replace("{m}", String.valueOf(n))
				.replace("{f}", ChartBlocks.fieldLabel(rc, rows.get(0), field))))
			.append("</span></div>");
		if (!missing.isEmpty())
		{
			final int max = Math.max(1, b.path("max").asInt(8));
			h.append("<div class=\"ldf-wl\">").append(ChartBlocks.esc(ChartBlocks.t("covMissing", rc).replace("{n}", String.valueOf(missing.size())))).append("</div><ul class=\"ldf-cov\">");
			for (int i = 0; i < Math.min(max, missing.size()); i++)
			{
				final Adaptation r = missing.get(i);
				h.append("<li><a href=\"").append(ChartBlocks.esc(MoreBlocks.recordUrl(rc, r))).append("\">").append(ChartBlocks.esc(ChartBlocks.label(rc, r, b.path("label").asText("")))).append("</a></li>");
			}
			if (missing.size() > max)
			{
				h.append("<li class=\"ldf-cov-more\">").append(ChartBlocks.esc(ChartBlocks.t("covMore", rc).replace("{n}", String.valueOf(missing.size() - max)))).append("</li>");
			}
			h.append("</ul>");
		}
		return h.toString();
	}
}
