package com.on.ebx.layout.runtime;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.schema.SchemaNode;

/**
 * The read-only display widgets of a field (1.4.0). Each one formats the field's value; its settings sit on the field
 * of the layout ({@code {"p": "/Price", "w": "money", "currency": "EUR"}}). Like the other display forms, a display
 * widget is used when the field is read only; an editable field keeps EBX's input widget.
 */
final class DisplayWidgets
{
	static final Set<String> KINDS = new HashSet<String>(Arrays.asList("badgeMap", "money", "unit", "percent", "stars", "relativeDate", "duration", "country", "phone", "email", "url",
		"color", "barcode", "copyId", "tags", "masked", "recordLink", "thumbnail", "geo", "boolIcon"));

	/** Copy to the clipboard from a button (data-v), with the textarea fallback for pages where the API is refused. */
	static final String JS = "if(!window.ldfCopy){window.ldfCopy=function(b){var v=b.getAttribute('data-v')||'';var done=function(){var o=b.textContent;b.textContent='✓';setTimeout(function(){b.textContent=o;},1200);};"
		+ "var old=function(){var t=document.createElement('textarea');t.value=v;t.style.position='fixed';t.style.opacity='0';document.body.appendChild(t);t.select();try{document.execCommand('copy');done();}catch(e){}document.body.removeChild(t);};"
		+ "try{if(navigator.clipboard&&window.isSecureContext){navigator.clipboard.writeText(v).then(done,old);}else old();}catch(e){old();}return false;};"
		+ "window.ldfReveal=function(b){var s=b.previousElementSibling;if(s){s.textContent=b.getAttribute('data-v');}b.remove();return false;};}";

	private DisplayWidgets()
	{
	}

	static boolean isDisplay(final String pKind)
	{
		return DisplayWidgets.KINDS.contains(pKind);
	}

	private static String esc(final String s)
	{
		return LayoutPane.esc(s);
	}

	private static String v(final String pInner)
	{
		return "<span class=\"ldf-v\">" + pInner + "</span>";
	}

	static String none()
	{
		return "<span class=\"ldf-v ldf-empty\">—</span>";
	}

	static String copyButton(final RenderContext rc, final String pValue)
	{
		return "<button type=\"button\" class=\"ldf-cp-b\" title=\"" + DisplayWidgets.esc(RuntimeText.get("copy", rc.locale)) + "\" data-v=\"" + DisplayWidgets.esc(pValue)
			+ "\" onclick=\"return ldfCopy(this)\">" + DisplayWidgets.esc(RuntimeText.get("copy", rc.locale)) + "</button>";
	}

	/** The value HTML of a display widget, for a field of the record. */
	static String html(final RenderContext rc, final JsonNode f, final String stored, final String kind)
	{
		final Object raw = rc.raw(stored);
		final String shown = rc.display(stored);
		if (!"boolIcon".equals(kind) && !Values.filled(raw))
		{
			return DisplayWidgets.none();
		}
		switch (kind)
		{
		case "badgeMap":
		{
			String tone = Values.tone(f.path("colors"), Values.key(raw), shown);
			if (tone.isEmpty())
			{
				tone = Values.toneClass(f.path("defaultTone").asText("muted"));
			}
			return DisplayWidgets.v("<span class=\"ldf-badge" + tone + "\">" + DisplayWidgets.esc(shown) + "</span>");
		}
		case "money":
		{
			final Double n = Values.number(raw);
			if (n == null)
			{
				return DisplayWidgets.v(DisplayWidgets.esc(shown));
			}
			return DisplayWidgets.v("<span class=\"ldf-fig\">" + DisplayWidgets.esc(MoreBlocks.formatNumber(rc, n.doubleValue(), DisplayWidgets.withFormat(f, "money"))) + "</span>");
		}
		case "unit":
		{
			final Double n = Values.number(raw);
			final String unit = !f.path("unitField").asText("").isEmpty() ? rc.display(f.path("unitField").asText()) : f.path("unit").asText("");
			final String num = n == null ? shown : f.has("decimals") ? Values.number(n.doubleValue(), f.path("decimals").asInt(0), rc.locale) : Values.number(n.doubleValue(), rc.locale);
			return DisplayWidgets.v(DisplayWidgets.esc(num) + (unit.isEmpty() ? "" : " <span class=\"ldf-unit\">" + DisplayWidgets.esc(unit) + "</span>"));
		}
		case "percent":
		{
			final Double n = Values.number(raw);
			if (n == null)
			{
				return DisplayWidgets.v(DisplayWidgets.esc(shown));
			}
			final double pct = f.path("scale").asInt(100) == 1 ? n.doubleValue() * 100 : n.doubleValue();
			final JsonNode th = f.path("thresholds");
			final double low = th.size() > 0 ? th.get(0).asDouble(50) : 50;
			final double high = th.size() > 1 ? th.get(1).asDouble(80) : 80;
			final String tone = pct < low ? "ldf-t-bad" : pct < high ? "ldf-t-warn" : "ldf-t-ok";
			final double w = Math.max(0, Math.min(100, pct));
			return DisplayWidgets.v("<span class=\"ldf-pct " + tone + "\"><span class=\"ldf-bar\"><i style=\"width:" + String.format(Locale.ROOT, "%.1f", w) + "%\"></i></span><b>"
				+ DisplayWidgets.esc(Values.number(pct, f.has("decimals") ? f.path("decimals").asInt(0) : 0, rc.locale)) + " %</b></span>");
		}
		case "stars":
		{
			final Double n = Values.number(raw);
			if (n == null)
			{
				return DisplayWidgets.v(DisplayWidgets.esc(shown));
			}
			final int max = Math.max(1, Math.min(10, f.path("max").asInt(5)));
			final double r = Math.max(0, Math.min(max, n.doubleValue()));
			final StringBuilder stars = new StringBuilder();
			for (int i = 0; i < max; i++)
			{
				stars.append('★');
			}
			return DisplayWidgets.v("<span class=\"ldf-stars\" role=\"img\" aria-label=\"" + DisplayWidgets.esc(Values.number(r, rc.locale) + " / " + max) + "\" style=\"--v:"
				+ String.format(Locale.ROOT, "%.1f", 100 * r / max) + "%\">" + stars + "</span> <span class=\"ldf-more\">" + DisplayWidgets.esc(Values.number(r, 1, rc.locale) + " / " + max) + "</span>");
		}
		case "relativeDate":
		{
			final LocalDate d = Values.date(raw);
			if (d == null)
			{
				return DisplayWidgets.v(DisplayWidgets.esc(shown));
			}
			final long days = ChronoUnit.DAYS.between(LocalDate.now(), d);
			final int warn = f.path("warnDays").asInt(0);
			final String tone = warn <= 0 ? "" : days < 0 ? " ldf-t-bad" : days <= warn ? " ldf-t-warn" : " ldf-t-ok";
			return DisplayWidgets.v("<span class=\"ldf-badge" + (tone.isEmpty() ? " ldf-t-muted" : tone) + "\" title=\"" + DisplayWidgets.esc(shown) + "\">"
				+ DisplayWidgets.esc(DisplayWidgets.relative(days, rc.locale)) + "</span> <span class=\"ldf-more\">" + DisplayWidgets.esc(shown) + "</span>");
		}
		case "duration":
		{
			final Double n = Values.number(raw);
			return DisplayWidgets.v(DisplayWidgets.esc(n == null ? shown : DisplayWidgets.duration(n.doubleValue(), f.path("unit").asText("day"), rc.locale)));
		}
		case "country":
		{
			final String code = Values.key(raw).trim();
			final String name = DisplayWidgets.countryName(code, rc.locale);
			final String a2 = DisplayWidgets.alpha2(code);
			final String flag = a2 == null || !f.path("flag").asBoolean(true) ? "" : DisplayWidgets.flag(a2) + " ";
			final String show = f.path("show").asText("both");
			final String text = name == null ? shown : "code".equals(show) ? code.toUpperCase(Locale.ROOT) : "name".equals(show) ? name : name + " (" + code.toUpperCase(Locale.ROOT) + ")";
			return DisplayWidgets.v(DisplayWidgets.esc(flag + text));
		}
		case "phone":
			return DisplayWidgets.v(DisplayWidgets.phone(rc, shown));
		case "email":
			return DisplayWidgets.v(DisplayWidgets.email(rc, shown));
		case "url":
		{
			final String href = shown.matches("(?i)^https?://.*") ? shown : "https://" + shown;
			final String text = "full".equals(f.path("show").asText("domain")) ? shown : href.replaceFirst("(?i)^https?://", "").replaceFirst("^www\\.", "").replaceAll("[/?#].*$", "");
			return DisplayWidgets.v("<a href=\"" + DisplayWidgets.esc(href) + "\" target=\"_blank\" rel=\"noopener\" title=\"" + DisplayWidgets.esc(shown) + "\">" + DisplayWidgets.esc(text) + " ↗</a>");
		}
		case "color":
		{
			String css = f.path("colors").path(Values.key(raw)).asText("");
			if (css.isEmpty())
			{
				css = Values.key(raw).trim();
			}
			final boolean valid = css.matches("#[0-9A-Fa-f]{3}|#[0-9A-Fa-f]{6}|[A-Za-z]{3,20}|rgb\\([0-9 ,.%]+\\)");
			return DisplayWidgets.v((valid ? "<span class=\"ldf-sw\" style=\"background:" + DisplayWidgets.esc(css) + "\"></span>" : "")
				+ (f.path("showCode").asBoolean(true) ? "<span class=\"ldf-mono\">" + DisplayWidgets.esc(shown) + "</span>" : ""));
		}
		case "barcode":
			return DisplayWidgets.v(Barcode.html(rc, Values.key(raw).trim(), f));
		case "copyId":
			return DisplayWidgets.v("<span class=\"ldf-mono\">" + DisplayWidgets.esc(raw instanceof Number ? Values.key(raw) : shown) + "</span> " + DisplayWidgets.copyButton(rc, Values.key(raw)));
		case "tags":
		{
			final SchemaNode n = rc.node(stored);
			final List<?> list = raw instanceof List ? (List<?>) raw : java.util.Collections.singletonList(raw);
			final int max = Math.max(1, f.path("max").asInt(6));
			final StringBuilder h = new StringBuilder("<span class=\"ldf-tags\">");
			int i = 0;
			for (final Object o : list)
			{
				if (o == null)
				{
					continue;
				}
				if (i++ >= max)
				{
					break;
				}
				String label;
				try
				{
					label = n.displayOccurrence(o, true, rc.context.getValueContext(), rc.locale);
				}
				catch (final RuntimeException ex)
				{
					label = String.valueOf(o);
				}
				h.append("<span class=\"ldf-badge").append(Values.tone(f.path("colors"), Values.key(o), label)).append("\">").append(DisplayWidgets.esc(label)).append("</span>");
			}
			if (list.size() > max)
			{
				h.append("<span class=\"ldf-badge ldf-t-muted\">+ ").append(list.size() - max).append("</span>");
			}
			return DisplayWidgets.v(h.append("</span>").toString());
		}
		case "masked":
		{
			final String value = Values.key(raw);
			final int visible = Math.max(0, f.path("visible").asInt(4));
			final StringBuilder m = new StringBuilder();
			for (int i = 0; i < value.length() - visible; i++)
			{
				m.append(Character.isWhitespace(value.charAt(i)) ? ' ' : '•');
			}
			m.append(value.substring(Math.max(0, value.length() - visible)));
			final boolean reveal = Behaviors.inAnyRole(rc.context.getSession(), f.path("revealRoles"));
			return DisplayWidgets.v("<span class=\"ldf-mono\">" + DisplayWidgets.esc(m.toString()) + "</span>"
				+ (reveal ? " <button type=\"button\" class=\"ldf-cp-b\" data-v=\"" + DisplayWidgets.esc(value) + "\" onclick=\"return ldfReveal(this)\">" + DisplayWidgets.esc(RuntimeText.get("show", rc.locale))
					+ "</button>" : ""));
		}
		case "recordLink":
		{
			final Adaptation target = MoreBlocks.linked(rc, stored);
			if (target == null)
			{
				return DisplayWidgets.v(DisplayWidgets.esc(shown));
			}
			return DisplayWidgets.v("<a class=\"ldf-chip\" href=\"" + DisplayWidgets.esc(MoreBlocks.recordUrl(rc, target)) + "\"" + (f.path("sameTab").asBoolean(false) ? " target=\"_top\"" : " target=\"_blank\" rel=\"noopener\"")
				+ ">" + DisplayWidgets.esc(shown) + " ↗</a>");
		}
		case "thumbnail":
		{
			final Media.Asset m = Media.of(rc.record, raw, rc.locale);
			if (m == null)
			{
				return DisplayWidgets.v(DisplayWidgets.esc(shown));
			}
			final String size = f.path("size").asText("small");
			final int px = "large".equals(size) ? 180 : "medium".equals(size) ? 110 : 56;
			return DisplayWidgets.v("<a href=\"" + DisplayWidgets.esc(m.url) + "\" target=\"_blank\" rel=\"noopener\"><img class=\"ldf-thumb\" loading=\"lazy\" alt=\"\" style=\"width:" + px + "px;height:" + px
				+ "px\" src=\"" + DisplayWidgets.esc(m.url) + "\"></a>");
		}
		case "geo":
		{
			final Double lat = Values.number(raw);
			final Double lng = f.path("lng").asText("").isEmpty() ? null : Values.number(rc.raw(f.path("lng").asText()));
			if (lat == null || lng == null)
			{
				return DisplayWidgets.v(DisplayWidgets.esc(shown));
			}
			final boolean dms = "dms".equals(f.path("format").asText("decimal"));
			final String text = DisplayWidgets.coord(lat.doubleValue(), true, dms) + ", " + DisplayWidgets.coord(lng.doubleValue(), false, dms);
			return DisplayWidgets.v("<a target=\"_blank\" rel=\"noopener\" href=\"https://www.openstreetmap.org/?mlat=" + lat + "&amp;mlon=" + lng + "#map=15/" + lat + "/" + lng + "\">"
				+ DisplayWidgets.esc(text) + " ↗</a>");
		}
		case "boolIcon":
		{
			if (raw == null)
			{
				return DisplayWidgets.none();
			}
			final boolean on = Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw));
			final JsonNode label = f.path("labels").path(on ? "true" : "false");
			final String text = LText.hasText(label) ? rc.text(label) : shown;
			return DisplayWidgets.v("<span class=\"ldf-bi " + (on ? "ldf-t-ok" : "ldf-t-muted") + "\"><i>" + (on ? "✓" : "✕") + "</i>" + DisplayWidgets.esc(text) + "</span>");
		}
		default:
			return DisplayWidgets.v(DisplayWidgets.esc(shown));
		}
	}

	private static JsonNode withFormat(final JsonNode f, final String pFormat)
	{
		final com.fasterxml.jackson.databind.node.ObjectNode o = f.isObject() ? ((com.fasterxml.jackson.databind.node.ObjectNode) f.deepCopy()) : LayoutStore.json().createObjectNode();
		o.put("format", pFormat);
		return o;
	}

	static String phone(final RenderContext rc, final String pPhone)
	{
		final String tel = pPhone.replaceAll("[^0-9+]", "");
		return "<a href=\"tel:" + DisplayWidgets.esc(tel) + "\">" + DisplayWidgets.esc(pPhone) + "</a> " + DisplayWidgets.copyButton(rc, pPhone);
	}

	static String email(final RenderContext rc, final String pEmail)
	{
		return "<a href=\"mailto:" + DisplayWidgets.esc(pEmail) + "\">" + DisplayWidgets.esc(pEmail) + "</a> " + DisplayWidgets.copyButton(rc, pEmail);
	}

	static String relative(final long pDays, final Locale pLocale)
	{
		if (pDays == 0)
		{
			return RuntimeText.get("today", pLocale);
		}
		final long a = Math.abs(pDays);
		final String amount = a < 60 ? RuntimeText.get(a == 1 ? "day1" : "dayN", pLocale, Long.valueOf(a))
			: a < 730 ? RuntimeText.get("monthN", pLocale, Long.valueOf(Math.round(a / 30.44))) : RuntimeText.get("yearN", pLocale, Long.valueOf(Math.round(a / 365.25)));
		return RuntimeText.get(pDays > 0 ? "inX" : "xAgo", pLocale, amount);
	}

	static String duration(final double pValue, final String pUnit, final Locale pLocale)
	{
		final double minutes = "minute".equals(pUnit) ? pValue : "hour".equals(pUnit) ? pValue * 60 : "day".equals(pUnit) ? pValue * 1440 : "month".equals(pUnit) ? pValue * 43830 : "year".equals(pUnit) ? pValue * 525960 : pValue;
		final StringBuilder b = new StringBuilder();
		if ("minute".equals(pUnit) || "hour".equals(pUnit))
		{
			final long m = Math.round(minutes);
			final long d = m / 1440;
			final long h = m % 1440 / 60;
			final long min = m % 60;
			DisplayWidgets.part(b, d, "durD", pLocale);
			DisplayWidgets.part(b, h, "durH", pLocale);
			DisplayWidgets.part(b, min, "durMin", pLocale);
		}
		else
		{
			final long totalMonths = "day".equals(pUnit) ? 0 : Math.round(minutes / 43830);
			if ("day".equals(pUnit))
			{
				final long days = Math.round(pValue);
				DisplayWidgets.part(b, days / 365, "durY", pLocale);
				DisplayWidgets.part(b, days % 365 / 30, "durMo", pLocale);
				DisplayWidgets.part(b, days % 365 % 30, "durD", pLocale);
			}
			else
			{
				DisplayWidgets.part(b, totalMonths / 12, "durY", pLocale);
				DisplayWidgets.part(b, totalMonths % 12, "durMo", pLocale);
			}
		}
		return b.length() == 0 ? "0" : b.toString().trim();
	}

	private static void part(final StringBuilder b, final long n, final String pKey, final Locale pLocale)
	{
		if (n > 0)
		{
			b.append(RuntimeText.get(pKey, pLocale, Long.valueOf(n))).append(' ');
		}
	}

	static String countryName(final String pCode, final Locale pLocale)
	{
		final String a2 = DisplayWidgets.alpha2(pCode);
		if (a2 == null)
		{
			return null;
		}
		final String name = new Locale("", a2).getDisplayCountry(pLocale);
		return name == null || name.isEmpty() || name.equalsIgnoreCase(a2) ? null : name;
	}

	static String alpha2(final String pCode)
	{
		final String c = pCode == null ? "" : pCode.trim().toUpperCase(Locale.ROOT);
		if (c.matches("[A-Z]{2}"))
		{
			return Arrays.asList(Locale.getISOCountries()).contains(c) ? c : "UK".equals(c) ? "GB" : null;
		}
		if (c.matches("[A-Z]{3}"))
		{
			for (final String a2 : Locale.getISOCountries())
			{
				try
				{
					if (new Locale("", a2).getISO3Country().equals(c))
					{
						return a2;
					}
				}
				catch (final RuntimeException ex)
				{
					// no ISO 3 code
				}
			}
		}
		return null;
	}

	private static String flag(final String pAlpha2)
	{
		return new String(Character.toChars(0x1F1E6 + pAlpha2.charAt(0) - 'A')) + new String(Character.toChars(0x1F1E6 + pAlpha2.charAt(1) - 'A'));
	}

	private static String coord(final double v, final boolean pLat, final boolean pDms)
	{
		final String hemi = pLat ? (v >= 0 ? "N" : "S") : (v >= 0 ? "E" : "W");
		final double a = Math.abs(v);
		if (!pDms)
		{
			return String.format(Locale.ROOT, "%.5f° %s", a, hemi);
		}
		final int d = (int) a;
		final double mf = (a - d) * 60;
		final int m = (int) mf;
		final double s = (mf - m) * 60;
		return String.format(Locale.ROOT, "%d°%02d′%04.1f″ %s", d, m, s, hemi);
	}
}
