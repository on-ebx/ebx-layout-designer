package com.on.ebx.layout.runtime;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;

/** Reading and formatting the values of any row (a linked record, a row of an association) by a path of a layout. */
final class Values
{
	private Values()
	{
	}

	static SchemaNode node(final Adaptation pRow, final String pStored)
	{
		final Path p = LayoutPaths.relative(pStored);
		if (pRow == null || p == null)
		{
			return null;
		}
		try
		{
			return pRow.getSchemaNode().getNode(p);
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** The stored value, or null when the row has no such field. */
	static Object raw(final Adaptation pRow, final String pStored)
	{
		final SchemaNode n = Values.node(pRow, pStored);
		if (n == null)
		{
			return null;
		}
		try
		{
			return pRow.get(LayoutPaths.relative(pStored));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** The value as EBX shows it (foreign-key label, enumeration label, formatted date), "" when empty or absent. */
	static String display(final Adaptation pRow, final String pStored, final Locale pLocale)
	{
		final SchemaNode n = Values.node(pRow, pStored);
		if (n == null)
		{
			return "";
		}
		try
		{
			final Object v = pRow.get(LayoutPaths.relative(pStored));
			if (v == null)
			{
				return "";
			}
			if (v instanceof List)
			{
				final StringBuilder b = new StringBuilder();
				for (final Object o : (List<?>) v)
				{
					final String s = o == null ? "" : n.displayOccurrence(o, true, pRow.createValueContext(), pLocale);
					if (s != null && !s.isEmpty())
					{
						b.append(b.length() == 0 ? "" : ", ").append(s);
					}
				}
				return b.toString();
			}
			final String s = n.displayOccurrence(v, true, pRow.createValueContext(), pLocale);
			return s == null ? String.valueOf(v) : s;
		}
		catch (final RuntimeException ex)
		{
			return "";
		}
	}

	/** One value of a field as a comparable text: a foreign key's formatted key, an enumeration's code, "true"/"false". */
	static String key(final Object pValue)
	{
		if (pValue == null)
		{
			return "";
		}
		if (pValue instanceof Date)
		{
			return LocalDate.ofInstant(Instant.ofEpochMilli(((Date) pValue).getTime()), ZoneId.systemDefault()).toString();
		}
		if (pValue instanceof BigDecimal)
		{
			return ((BigDecimal) pValue).stripTrailingZeros().toPlainString();
		}
		return String.valueOf(pValue);
	}

	static Double number(final Object pValue)
	{
		if (pValue instanceof Number)
		{
			return Double.valueOf(((Number) pValue).doubleValue());
		}
		if (pValue instanceof String)
		{
			try
			{
				return Double.valueOf(Double.parseDouble(((String) pValue).trim().replace(',', '.')));
			}
			catch (final RuntimeException ex)
			{
				return null;
			}
		}
		return null;
	}

	static LocalDate date(final Object pValue)
	{
		if (pValue instanceof Date)
		{
			return LocalDate.ofInstant(Instant.ofEpochMilli(((Date) pValue).getTime()), ZoneId.systemDefault());
		}
		if (pValue instanceof String && ((String) pValue).length() >= 10)
		{
			try
			{
				return LocalDate.parse(((String) pValue).substring(0, 10));
			}
			catch (final RuntimeException ex)
			{
				return null;
			}
		}
		return null;
	}

	static boolean filled(final Object pValue)
	{
		if (pValue == null)
		{
			return false;
		}
		if (pValue instanceof String)
		{
			return !((String) pValue).trim().isEmpty();
		}
		if (pValue instanceof List)
		{
			return !((List<?>) pValue).isEmpty();
		}
		return true;
	}

	static String number(final double pValue, final int pDecimals, final Locale pLocale)
	{
		final NumberFormat f = NumberFormat.getNumberInstance(pLocale);
		f.setMinimumFractionDigits(Math.max(0, pDecimals));
		f.setMaximumFractionDigits(Math.max(0, pDecimals));
		return f.format(pValue);
	}

	/** A number with as many decimals as it needs, up to 2. */
	static String number(final double pValue, final Locale pLocale)
	{
		final NumberFormat f = NumberFormat.getNumberInstance(pLocale);
		f.setMaximumFractionDigits(2);
		return f.format(pValue);
	}

	static String money(final double pValue, final String pCurrency, final int pDecimals, final Locale pLocale)
	{
		try
		{
			final NumberFormat f = NumberFormat.getCurrencyInstance(pLocale);
			if (pCurrency != null && !pCurrency.trim().isEmpty())
			{
				f.setCurrency(java.util.Currency.getInstance(pCurrency.trim().toUpperCase(Locale.ROOT)));
			}
			if (pDecimals >= 0)
			{
				f.setMinimumFractionDigits(pDecimals);
				f.setMaximumFractionDigits(pDecimals);
			}
			return f.format(pValue);
		}
		catch (final RuntimeException ex)
		{
			return Values.number(pValue, pDecimals < 0 ? 2 : pDecimals, pLocale) + (pCurrency == null ? "" : " " + pCurrency);
		}
	}

	/** The colour tone a "colors" map of a layout gives a value; "" when none applies. */
	static String tone(final JsonNode pColors, final String pKey, final String pDisplayed)
	{
		if (pColors == null || !pColors.isObject())
		{
			return "";
		}
		JsonNode t = pColors.get(pKey);
		if (t == null && pDisplayed != null)
		{
			t = pColors.get(pDisplayed);
		}
		if (t == null)
		{
			final java.util.Iterator<java.util.Map.Entry<String, JsonNode>> it = pColors.fields();
			while (it.hasNext())
			{
				final java.util.Map.Entry<String, JsonNode> e = it.next();
				if (e.getKey().equalsIgnoreCase(pKey) || (pDisplayed != null && e.getKey().equalsIgnoreCase(pDisplayed)))
				{
					t = e.getValue();
					break;
				}
			}
		}
		return t == null ? "" : Values.toneClass(t.asText(""));
	}

	static String toneClass(final String pTone)
	{
		switch (pTone)
		{
		case "ok":
		case "warn":
		case "bad":
		case "accent":
		case "info":
		case "muted":
			return " ldf-t-" + pTone;
		default:
			return "";
		}
	}
}
