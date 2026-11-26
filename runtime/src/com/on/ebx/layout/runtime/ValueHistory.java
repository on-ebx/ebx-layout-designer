package com.on.ebx.layout.runtime;

import java.util.Date;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.onwbp.adaptation.Adaptation;

/**
 * The "Value over time" block: the successive values of a few fields of the record, read in the table's history
 * ({@code AdaptationTable.getHistory()}). Only the versions where a value changed are kept; a number is drawn as a curve
 * (steps or line), any other value is listed. Drawn in the browser by {@code blocks.js}.
 */
final class ValueHistory
{
	private static final long DAY = 86_400_000L;

	private ValueHistory()
	{
	}

	static String render(final RenderContext rc, final JsonNode b, final String pId)
	{
		if (!b.path("fields").isArray() || b.path("fields").size() == 0)
		{
			return "<div class=\"ldf-more\">" + LayoutPane.esc(RuntimeText.get("setupBlock", rc.locale)) + "</div>";
		}
		if (rc.creating || rc.record == null)
		{
			return "<div class=\"ldf-more\">" + LayoutPane.esc(RuntimeText.get("afterCreate", rc.locale)) + "</div>";
		}
		final MoreBlocks.Hist h = MoreBlocks.readHistory(rc);
		if (h.error != null)
		{
			return "<div class=\"ldf-more\">" + LayoutPane.esc(RuntimeText.get(h.error, rc.locale)) + "</div>";
		}
		final String period = b.path("period").asText("all");
		final long from = "90d".equals(period) ? System.currentTimeMillis() - 90 * ValueHistory.DAY
			: "12m".equals(period) ? System.currentTimeMillis() - 365 * ValueHistory.DAY : Long.MIN_VALUE;
		final ObjectNode data = LayoutStore.json().createObjectNode();
		data.put("shape", b.path("shape").asText("step"));
		data.put("list", b.path("listChanges").asBoolean(true));
		data.put("author", b.path("author").asBoolean(true));
		data.put("versions", h.rows.size());
		data.put("now", System.currentTimeMillis());
		data.put("locale", rc.locale.toLanguageTag());
		final ObjectNode t = data.putObject("t");
		for (final String k : new String[] { "since", "changeSince", "unchanged", "values", "first", "noValue", "noChange", "system", "ofVersions" })
		{
			t.put(k, RuntimeText.get("vh." + k, rc.locale));
		}
		final ArrayNode fields = data.putArray("fields");
		for (final JsonNode fp : b.path("fields"))
		{
			final String path = fp.asText();
			if (rc.node(path) == null)
			{
				continue;
			}
			final ObjectNode f = fields.addObject();
			f.put("label", rc.label(path));
			boolean numeric = true;
			final ArrayNode pts = f.putArray("points");
			Object previous = null;
			boolean any = false;
			for (final Adaptation r : h.rows)
			{
				final Object raw = Values.raw(r, path);
				if (raw == null || (any && Objects.equals(raw, previous)))
				{
					continue;
				}
				any = true;
				previous = raw;
				final Object when = h.when == null ? null : r.get(h.when);
				final long ms = when instanceof Date ? ((Date) when).getTime() : 0L;
				if (ms < from)
				{
					// before the period: the value it held when the period starts
					pts.removeAll();
				}
				final ObjectNode p = pts.addObject();
				p.put("t", Math.max(ms, from == Long.MIN_VALUE ? ms : from));
				final Double n = Values.number(raw);
				if (n == null || raw instanceof String)
				{
					numeric = false;
				}
				else
				{
					p.put("v", n.doubleValue());
				}
				p.put("s", Values.display(r, path, rc.locale));
				final Object who = h.who == null ? null : r.get(h.who);
				final String user = who == null ? "" : String.valueOf(who);
				p.put("u", "ebx-systemUser".equals(user) ? RuntimeText.get("vh.system", rc.locale) : user);
			}
			f.put("numeric", numeric);
		}
		final String json = data.toString().replace("</", "<\\/");
		return "<div class=\"ldf-vh\" data-vh=\"" + LayoutPane.esc(pId) + "\"><div class=\"ldf-vh-body\"></div><script type=\"application/json\">" + json + "</script></div>";
	}
}
