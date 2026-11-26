package com.on.ebx.layout.explorer;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The conditions of an explorer ({@code docs/EXPLORER-FORMAT.md}, "Conditions"): flags, tiles, {@code case} values and
 * actions' {@code cond}. Pure: values come from a {@link Lookup}, so it runs on an index row, on a record or in a test.
 */
public final class Cond
{
	/** Where a condition reads its values. A value is a String, a Number, a Boolean, a Date, a Collection of these, or null. */
	public interface Lookup
	{
		Object field(String pPath);

		Object derived(String pName);
	}

	private Cond()
	{
	}

	/** An empty or absent condition holds. */
	public static boolean test(final JsonNode pCond, final Lookup pValues)
	{
		if (pCond == null || pCond.isNull() || pCond.isMissingNode() || pCond.size() == 0)
		{
			return true;
		}
		if (pCond.has("all"))
		{
			for (final JsonNode c : pCond.get("all"))
			{
				if (!Cond.test(c, pValues))
				{
					return false;
				}
			}
			return true;
		}
		if (pCond.has("any"))
		{
			for (final JsonNode c : pCond.get("any"))
			{
				if (Cond.test(c, pValues))
				{
					return true;
				}
			}
			return false;
		}
		if (pCond.has("not"))
		{
			return !Cond.test(pCond.get("not"), pValues);
		}
		if (pCond.has("days"))
		{
			return Cond.test(Cond.days(pCond), pValues);
		}
		final Object v = pCond.has("derived") ? pValues.derived(pCond.path("derived").asText()) : pValues.field(pCond.path("field").asText(""));
		if (pCond.has("in"))
		{
			return Cond.inList(v, pCond.get("in"));
		}
		if (pCond.has("notIn"))
		{
			return !Cond.inList(v, pCond.get("notIn"));
		}
		if (pCond.path("filled").asBoolean(false))
		{
			return !Cond.isEmpty(v);
		}
		if (pCond.path("empty").asBoolean(false))
		{
			return Cond.isEmpty(v);
		}
		for (final String op : new String[] { "gt", "lt", "ge", "le", "eq", "ne" })
		{
			if (pCond.has(op))
			{
				return Cond.compare(op, v, pCond.get(op));
			}
		}
		return true;
	}

	/**
	 * {@code {"field": "/d", "days": {"from": 0, "to": 90}}}: a date from today + {@code from} days (included) to today +
	 * {@code to} days (excluded); either bound may be left out ({@code {"to": 0}} = before today). Rewritten as the same
	 * condition with {@code ge} / {@code lt} on yyyy-MM-dd dates, as of today on the server.
	 */
	public static JsonNode days(final JsonNode pCond)
	{
		final com.fasterxml.jackson.databind.node.ObjectNode out = com.on.ebx.layout.runtime.LayoutStore.json().createObjectNode();
		final com.fasterxml.jackson.databind.node.ArrayNode all = out.putArray("all");
		final JsonNode d = pCond.path("days");
		final java.time.LocalDate today = java.time.LocalDate.now();
		for (final String[] bound : new String[][] { { "from", "ge" }, { "to", "lt" } })
		{
			if (d.path(bound[0]).isNumber() || d.path(bound[0]).isTextual() && Cond.number(d.path(bound[0]).asText()) != null)
			{
				final com.fasterxml.jackson.databind.node.ObjectNode leaf = all.addObject();
				if (pCond.has("derived"))
				{
					leaf.set("derived", pCond.get("derived"));
				}
				else
				{
					leaf.set("field", pCond.path("field"));
				}
				leaf.put(bound[1], today.plusDays(Math.round(d.path(bound[0]).asDouble())).toString());
			}
		}
		if (all.size() == 0)
		{
			// no bound: the date is filled
			final com.fasterxml.jackson.databind.node.ObjectNode leaf = all.addObject();
			leaf.setAll((com.fasterxml.jackson.databind.node.ObjectNode) pCond.deepCopy());
			leaf.remove("days");
			leaf.put("filled", true);
		}
		return out;
	}

	public static boolean isEmpty(final Object pValue)
	{
		return pValue == null || pValue instanceof String && ((String) pValue).isEmpty() || pValue instanceof Collection && ((Collection<?>) pValue).isEmpty();
	}

	/** The value as a key: enumeration code, formatted foreign key, "true"/"false", a plain number, a yyyy-MM-dd date. */
	public static String key(final Object pValue)
	{
		if (pValue == null)
		{
			return null;
		}
		if (pValue instanceof BigDecimal)
		{
			return ((BigDecimal) pValue).stripTrailingZeros().toPlainString();
		}
		if (pValue instanceof Double || pValue instanceof Float)
		{
			final double d = ((Number) pValue).doubleValue();
			return d == Math.rint(d) && !Double.isInfinite(d) ? String.valueOf((long) d) : String.valueOf(d);
		}
		if (pValue instanceof Date)
		{
			return new SimpleDateFormat("yyyy-MM-dd").format((Date) pValue);
		}
		return String.valueOf(pValue);
	}

	private static boolean inList(final Object pValue, final JsonNode pList)
	{
		if (pValue instanceof Collection)
		{
			for (final Object o : (Collection<?>) pValue)
			{
				if (Cond.inList(o, pList))
				{
					return true;
				}
			}
			return false;
		}
		final String k = Cond.key(pValue);
		if (k == null)
		{
			return false;
		}
		for (final JsonNode x : pList)
		{
			if (k.equals(x.isTextual() ? x.asText() : x.toString()))
			{
				return true;
			}
		}
		return false;
	}

	public static Double number(final Object pValue)
	{
		if (pValue instanceof Number)
		{
			return Double.valueOf(((Number) pValue).doubleValue());
		}
		if (pValue instanceof Collection)
		{
			return Double.valueOf(((Collection<?>) pValue).size());
		}
		if (pValue instanceof Boolean)
		{
			return null;
		}
		if (pValue instanceof String)
		{
			try
			{
				return Double.valueOf(((String) pValue).trim());
			}
			catch (final NumberFormatException ex)
			{
				return null;
			}
		}
		return null;
	}

	private static boolean compare(final String pOp, final Object pValue, final JsonNode pRef)
	{
		final Double a = Cond.number(pValue);
		final Double b = pRef.isNumber() ? Double.valueOf(pRef.asDouble()) : Cond.number(pRef.asText());
		if (a != null && b != null)
		{
			final int c = Double.compare(a.doubleValue(), b.doubleValue());
			switch (pOp)
			{
			case "gt": return c > 0;
			case "lt": return c < 0;
			case "ge": return c >= 0;
			case "le": return c <= 0;
			case "eq": return c == 0;
			default: return c != 0;
			}
		}
		final String k = Cond.key(pValue);
		final String r = pRef.isTextual() ? pRef.asText() : pRef.toString();
		if ("eq".equals(pOp))
		{
			return r.equals(k);
		}
		if ("ne".equals(pOp))
		{
			return !r.equals(k);
		}
		if (k == null)
		{
			// a missing value is neither greater nor smaller
			return false;
		}
		final int c = k.compareTo(r);
		switch (pOp)
		{
		case "gt": return c > 0;
		case "lt": return c < 0;
		case "ge": return c >= 0;
		default: return c <= 0;
		}
	}

	/** The field paths and computed values a condition reads, into the given sets. */
	public static void reads(final JsonNode pCond, final Set<String> pFields, final Set<String> pDerived)
	{
		if (pCond == null || !pCond.isObject())
		{
			return;
		}
		for (final String k : new String[] { "all", "any" })
		{
			if (pCond.has(k))
			{
				for (final JsonNode c : pCond.get(k))
				{
					Cond.reads(c, pFields, pDerived);
				}
			}
		}
		if (pCond.has("not"))
		{
			Cond.reads(pCond.get("not"), pFields, pDerived);
		}
		if (pCond.hasNonNull("field"))
		{
			pFields.add(pCond.get("field").asText());
		}
		if (pCond.hasNonNull("derived"))
		{
			pDerived.add(pCond.get("derived").asText());
		}
	}
}
