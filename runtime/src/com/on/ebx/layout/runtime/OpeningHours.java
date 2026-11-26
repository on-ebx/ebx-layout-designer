package com.on.ebx.layout.runtime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.schema.Path;

/**
 * The "opening hours" block ({@code "type": "hours"}): whether the record is open now in its own time zone, the current week
 * one card per day, and the next exceptional days. Read only; every table and field comes from the block:
 * <ul>
 * <li>{@code week}: {@code a} (the association of the regular hours), {@code day}, {@code open}, {@code close}, {@code closed}
 * (optional) — paths of the target table's fields;</li>
 * <li>{@code days} (optional): the day field's code for each ISO day, {@code {"1": "MON", ..., "7": "SUN"}}; when empty the
 * code is recognised by itself (1 to 7, Monday = 1; English, French or Italian day names, full or abbreviated);</li>
 * <li>{@code exceptions} (optional): {@code a}, {@code date}, {@code open}, {@code close}, {@code closed}, {@code reason};</li>
 * <li>{@code max}: upcoming exceptions shown (default 3, 0 hides them); {@code tz} (optional): the record's time-zone field.</li>
 * </ul>
 * Times are read from a text "HH:mm" (also "H:mm", "HH:mm:ss") or a time value; dates from a date value or an ISO text. A
 * day may hold several slots (several rows), and a slot may end after midnight. An exception of a date replaces the week's
 * hours for that date.
 */
final class OpeningHours
{
	/** One opening slot; {@code close} before {@code open} means the slot ends after midnight. */
	static final class Slot implements Comparable<Slot>
	{
		final LocalTime open;
		final LocalTime close;

		Slot(final LocalTime pOpen, final LocalTime pClose)
		{
			this.open = pOpen;
			this.close = pClose;
		}

		boolean contains(final LocalTime t)
		{
			return this.close.isAfter(this.open) ? !t.isBefore(this.open) && t.isBefore(this.close) : !t.isBefore(this.open) || t.isBefore(this.close);
		}

		@Override
		public int compareTo(final Slot o)
		{
			return this.open.compareTo(o.open);
		}

		@Override
		public String toString()
		{
			return OpeningHours.hhmm(this.open) + " – " + OpeningHours.hhmm(this.close);
		}
	}

	/** The hours of one day: its slots (empty = closed) and, for an exception, its reason. */
	static final class Day
	{
		final List<Slot> slots = new ArrayList<Slot>();
		String reason;

		boolean closed()
		{
			return this.slots.isEmpty();
		}

		String text(final Locale l)
		{
			if (this.closed())
			{
				return RuntimeText.get("hoursClosed", l);
			}
			final StringBuilder s = new StringBuilder();
			for (final Slot slot : this.slots)
			{
				s.append(s.length() == 0 ? "" : ", ").append(slot);
			}
			return s.toString();
		}
	}

	private static final String[][] DAY_NAMES = {
		{ "MON", "MONDAY", "LUN", "LUNDI", "LUNEDI", "LUNEDÌ" },
		{ "TUE", "TUESDAY", "MAR", "MARDI", "MARTEDI", "MARTEDÌ" },
		{ "WED", "WEDNESDAY", "MER", "MERCREDI", "MERCOLEDI", "MERCOLEDÌ" },
		{ "THU", "THURSDAY", "JEU", "JEUDI", "GIO", "GIOVEDI", "GIOVEDÌ" },
		{ "FRI", "FRIDAY", "VEN", "VENDREDI", "VENERDI", "VENERDÌ" },
		{ "SAT", "SATURDAY", "SAM", "SAMEDI", "SAB", "SABATO" },
		{ "SUN", "SUNDAY", "DIM", "DIMANCHE", "DOM", "DOMENICA" } };

	private OpeningHours()
	{
	}

	/** The block's body (inside its panel). */
	static String render(final RenderContext rc, final JsonNode b)
	{
		final Locale l = rc.locale;
		final StringBuilder h = new StringBuilder("<div class=\"ldf-oh\">");
		final JsonNode week = b.path("week");
		if (week.path("a").asText("").isEmpty())
		{
			return h.append("<div class=\"ldf-oh-empty\">").append(LayoutPane.esc(RuntimeText.get("hoursSetup", l))).append("</div></div>").toString();
		}
		if (rc.record == null || rc.creating)
		{
			return h.append("<div class=\"ldf-oh-empty\">").append(LayoutPane.esc(RuntimeText.get("hoursCreate", l))).append("</div></div>").toString();
		}
		final ZoneId zone = OpeningHours.zone(rc, b.path("tz").asText(""));
		final ZonedDateTime now = ZonedDateTime.now(zone);
		final LocalDate today = now.toLocalDate();

		final Map<Integer, Day> regular = OpeningHours.week(rc, week, b.path("days"));
		final JsonNode exc = b.path("exceptions");
		final TreeMap<LocalDate, Day> exceptions = exc.path("a").asText("").isEmpty() ? new TreeMap<LocalDate, Day>() : OpeningHours.exceptions(rc, exc, today);

		OpeningHours.now(h, l, now, regular, exceptions);
		OpeningHours.weekCards(h, l, today, regular, exceptions);
		final int max = b.path("max").asInt(3);
		if (max > 0 && !exc.path("a").asText("").isEmpty())
		{
			final String title = LText.hasText(b.get("excTitle")) ? rc.text(b.get("excTitle")) : RuntimeText.get("hoursUpcoming", l);
			OpeningHours.upcoming(h, l, title, exceptions, max);
		}
		return h.append("</div>").toString();
	}

	// ------------------------------------------------------------------------------------------------ reading

	private static Map<Integer, Day> week(final RenderContext rc, final JsonNode cfg, final JsonNode pDays)
	{
		final Map<String, Integer> codes = new HashMap<String, Integer>();
		for (int d = 1; d <= 7; d++)
		{
			final String code = pDays.path(String.valueOf(d)).asText("").trim();
			if (!code.isEmpty())
			{
				codes.put(code.toUpperCase(Locale.ROOT), Integer.valueOf(d));
			}
		}
		final Map<Integer, Day> out = new HashMap<Integer, Day>();
		for (final Adaptation row : rc.rows(cfg.path("a").asText(), 500))
		{
			final Integer day = OpeningHours.isoDay(OpeningHours.value(row, cfg.path("day").asText("")), codes);
			if (day == null)
			{
				continue;
			}
			Day d = out.get(day);
			if (d == null)
			{
				d = new Day();
				out.put(day, d);
			}
			OpeningHours.addSlot(d, row, cfg);
		}
		for (final Day d : out.values())
		{
			Collections.sort(d.slots);
		}
		return out;
	}

	private static TreeMap<LocalDate, Day> exceptions(final RenderContext rc, final JsonNode cfg, final LocalDate pToday)
	{
		final TreeMap<LocalDate, Day> out = new TreeMap<LocalDate, Day>();
		for (final Adaptation row : rc.rows(cfg.path("a").asText(), 2000))
		{
			final LocalDate date = OpeningHours.date(OpeningHours.value(row, cfg.path("date").asText("")));
			if (date == null || date.isBefore(pToday))
			{
				continue;
			}
			Day d = out.get(date);
			if (d == null)
			{
				d = new Day();
				out.put(date, d);
			}
			OpeningHours.addSlot(d, row, cfg);
			final Object reason = OpeningHours.value(row, cfg.path("reason").asText(""));
			if (d.reason == null && reason != null && !String.valueOf(reason).trim().isEmpty())
			{
				d.reason = String.valueOf(reason);
			}
		}
		for (final Day d : out.values())
		{
			Collections.sort(d.slots);
		}
		return out;
	}

	/** A row adds a slot unless it is marked closed or its times are missing or unreadable. */
	private static void addSlot(final Day d, final Adaptation row, final JsonNode cfg)
	{
		if (OpeningHours.isTrue(OpeningHours.value(row, cfg.path("closed").asText(""))))
		{
			return;
		}
		final LocalTime open = OpeningHours.time(OpeningHours.value(row, cfg.path("open").asText("")));
		final LocalTime close = OpeningHours.time(OpeningHours.value(row, cfg.path("close").asText("")));
		if (open != null && close != null && !open.equals(close))
		{
			d.slots.add(new Slot(open, close));
		}
	}

	/** The value of a field of a row of the target table, null when the path is empty or the table has no such field. */
	static Object value(final Adaptation pRow, final String pStored)
	{
		if (pStored == null || pStored.isEmpty())
		{
			return null;
		}
		final Path p = LayoutPaths.relative(pStored);
		if (p == null || pRow.getSchemaNode().getNode(p) == null)
		{
			return null;
		}
		return pRow.get(p);
	}

	/** ISO day (Monday = 1) of a day field's value: the block's codes first, else a number 1-7 or a day name. */
	static Integer isoDay(final Object pValue, final Map<String, Integer> pCodes)
	{
		if (pValue == null)
		{
			return null;
		}
		final String v = String.valueOf(pValue).trim().toUpperCase(Locale.ROOT);
		if (!pCodes.isEmpty())
		{
			return pCodes.get(v);
		}
		if (v.matches("[1-7]"))
		{
			return Integer.valueOf(v);
		}
		final String name = v.replace(".", "");
		for (int d = 0; d < 7; d++)
		{
			for (final String n : OpeningHours.DAY_NAMES[d])
			{
				if (n.equals(name))
				{
					return Integer.valueOf(d + 1);
				}
			}
		}
		return null;
	}

	static LocalTime time(final Object pValue)
	{
		if (pValue instanceof LocalTime)
		{
			return (LocalTime) pValue;
		}
		if (pValue instanceof Date)
		{
			return new java.sql.Time(((Date) pValue).getTime()).toLocalTime();
		}
		if (pValue == null)
		{
			return null;
		}
		final String s = String.valueOf(pValue).trim();
		if (!s.matches("\\d{1,2}[:hH.]\\d{2}(:\\d{2})?"))
		{
			return null;
		}
		final String[] p = s.split("[:hH.]");
		final int hh = Integer.parseInt(p[0]);
		final int mm = Integer.parseInt(p[1]);
		if (hh == 24 && mm == 0)
		{
			return LocalTime.MAX.withSecond(0).withNano(0);
		}
		return hh < 24 && mm < 60 ? LocalTime.of(hh, mm) : null;
	}

	static LocalDate date(final Object pValue)
	{
		if (pValue instanceof LocalDate)
		{
			return (LocalDate) pValue;
		}
		if (pValue instanceof Date)
		{
			return new java.sql.Date(((Date) pValue).getTime()).toLocalDate();
		}
		if (pValue == null)
		{
			return null;
		}
		try
		{
			return LocalDate.parse(String.valueOf(pValue).trim().substring(0, Math.min(10, String.valueOf(pValue).trim().length())));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	static boolean isTrue(final Object pValue)
	{
		if (pValue instanceof Boolean)
		{
			return ((Boolean) pValue).booleanValue();
		}
		if (pValue == null)
		{
			return false;
		}
		final String s = String.valueOf(pValue).trim().toUpperCase(Locale.ROOT);
		return "TRUE".equals(s) || "1".equals(s) || "Y".equals(s) || "YES".equals(s) || "O".equals(s) || "OUI".equals(s);
	}

	private static ZoneId zone(final RenderContext rc, final String pTz)
	{
		if (!pTz.isEmpty())
		{
			try
			{
				final Object id = OpeningHours.value(rc.record, pTz);
				if (id != null && !String.valueOf(id).trim().isEmpty())
				{
					return ZoneId.of(String.valueOf(id).trim());
				}
			}
			catch (final RuntimeException ex)
			{
				// an unknown zone: the server's
			}
		}
		return ZoneId.systemDefault();
	}

	// ------------------------------------------------------------------------------------------------ writing

	private static Day dayOf(final LocalDate pDate, final LocalDate pToday, final Map<Integer, Day> pWeek, final TreeMap<LocalDate, Day> pExc)
	{
		final Day exc = pDate.isBefore(pToday) ? null : pExc.get(pDate);
		if (exc != null)
		{
			return exc;
		}
		final Day d = pWeek.get(Integer.valueOf(pDate.getDayOfWeek().getValue()));
		return d == null ? new Day() : d;
	}

	private static void now(final StringBuilder h, final Locale l, final ZonedDateTime pNow, final Map<Integer, Day> pWeek, final TreeMap<LocalDate, Day> pExc)
	{
		final LocalDate today = pNow.toLocalDate();
		final Day day = OpeningHours.dayOf(today, today, pWeek, pExc);
		final LocalTime t = pNow.toLocalTime();
		Slot open = null;
		for (final Slot s : day.slots)
		{
			if (s.contains(t) && (s.close.isAfter(s.open) || !t.isBefore(s.open)))
			{
				open = s;
			}
		}
		// a slot of yesterday that ends after midnight
		final Day yesterday = OpeningHours.dayOf(today.minusDays(1), today.minusDays(1), pWeek, pExc);
		for (final Slot s : yesterday.slots)
		{
			if (!s.close.isAfter(s.open) && t.isBefore(s.close))
			{
				open = s;
			}
		}
		h.append(open != null ? "<div class=\"ldf-oh-now ldf-oh-on\">" : "<div class=\"ldf-oh-now\">").append("<i class=\"ldf-oh-dot\"></i><b>")
			.append(LayoutPane.esc(RuntimeText.get(open != null ? "hoursOpenNow" : "hoursClosedNow", l))).append("</b><span>");
		final StringBuilder s = new StringBuilder();
		if (open != null)
		{
			s.append(RuntimeText.get("hoursUntil", l, OpeningHours.hhmm(open.close))).append(" · ");
		}
		if (pExc.containsKey(today))
		{
			s.append(RuntimeText.get("hoursExceptionToday", l)).append(" · ");
		}
		s.append(RuntimeText.get("hoursLocalTime", l, OpeningHours.hhmm(t) + " (" + pNow.getZone().getId() + ")"));
		h.append(LayoutPane.esc(s.toString())).append("</span></div>");
	}

	private static void weekCards(final StringBuilder h, final Locale l, final LocalDate pToday, final Map<Integer, Day> pWeek, final TreeMap<LocalDate, Day> pExc)
	{
		final LocalDate monday = pToday.with(DayOfWeek.MONDAY);
		h.append("<div class=\"ldf-oh-week\">");
		for (int i = 0; i < 7; i++)
		{
			final LocalDate date = monday.plusDays(i);
			final Day d = OpeningHours.dayOf(date, pToday, pWeek, pExc);
			final boolean exc = !date.isBefore(pToday) && pExc.containsKey(date);
			h.append("<div class=\"ldf-oh-day").append(d.closed() ? " ldf-oh-off" : "").append(exc ? " ldf-oh-exc" : "").append(date.equals(pToday) ? " ldf-oh-today" : "")
				.append("\"><div class=\"ldf-oh-dl\"><span>").append(LayoutPane.esc(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, l))).append("</span>");
			if (date.equals(pToday))
			{
				h.append("<i>").append(LayoutPane.esc(RuntimeText.get("hoursToday", l))).append("</i>");
			}
			h.append("</div><div class=\"ldf-oh-h\">").append(LayoutPane.esc(d.text(l))).append("</div><div class=\"ldf-oh-bar\">");
			for (final Slot s : d.slots)
			{
				final double left = OpeningHours.fraction(s.open);
				final double right = s.close.isAfter(s.open) ? OpeningHours.fraction(s.close) : 1;
				h.append(String.format(Locale.ROOT, "<b style=\"left:%.2f%%;width:%.2f%%\"></b>", left * 100, Math.max(0, right - left) * 100));
			}
			h.append("</div></div>");
		}
		h.append("</div>");
	}

	private static void upcoming(final StringBuilder h, final Locale l, final String pTitle, final TreeMap<LocalDate, Day> pExc, final int pMax)
	{
		h.append("<div class=\"ldf-oh-xt\">").append(LayoutPane.esc(pTitle)).append("</div>");
		if (pExc.isEmpty())
		{
			h.append("<div class=\"ldf-oh-empty\">").append(LayoutPane.esc(RuntimeText.get("hoursNone", l))).append("</div>");
			return;
		}
		final DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(l);
		int n = 0;
		for (final Map.Entry<LocalDate, Day> e : pExc.entrySet())
		{
			if (n++ == pMax)
			{
				h.append("<div class=\"ldf-oh-empty\">").append(LayoutPane.esc(RuntimeText.get("hoursMore", l, Integer.valueOf(pExc.size() - pMax)))).append("</div>");
				break;
			}
			final Day d = e.getValue();
			h.append("<div class=\"ldf-oh-x\"><b>").append(LayoutPane.esc(e.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, l) + " " + e.getKey().format(fmt)))
				.append("</b><span>").append(LayoutPane.esc(d.reason == null ? "" : d.reason)).append(d.closed() ? "</span><em class=\"ldf-oh-c\">" : "</span><em>")
				.append(LayoutPane.esc(d.text(l))).append("</em></div>");
		}
	}

	static String hhmm(final LocalTime t)
	{
		return String.format(Locale.ROOT, "%02d:%02d", Integer.valueOf(t.getHour()), Integer.valueOf(t.getMinute()));
	}

	private static double fraction(final LocalTime t)
	{
		return (t.getHour() * 60 + t.getMinute()) / (24.0 * 60);
	}
}
