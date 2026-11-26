package com.on.ebx.layout.explorer;

/** Literals of EBX SQL (Calcite): strings in single quotes doubled, numbers in plain notation. */
final class Sql
{
	private Sql()
	{
	}

	static String lit(final String pText)
	{
		return pText == null ? "NULL" : "'" + pText.replace("'", "''") + "'";
	}

	static String number(final double pValue)
	{
		return pValue == Math.rint(pValue) && !Double.isInfinite(pValue) && Math.abs(pValue) < 1e15 ? String.valueOf((long) pValue)
			: java.math.BigDecimal.valueOf(pValue).toPlainString();
	}

	/** A LIKE pattern holding the text anywhere, with {@code \} as the escape character. */
	static String contains(final String pText)
	{
		return Sql.lit("%" + pText.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
	}

	/**
	 * {@code LOWER(<value>) LIKE '%text%'} on a text value: no CAST and no ESCAPE clause unless the text needs one, so
	 * EBX's index can serve it (with them EBX evaluates it record by record: 4 s against 0.1-0.4 s on 576,692 records,
	 * measured 2026-10-15, as the editor advised). A non-text value is cast.
	 */
	static String like(final String pValue, final boolean pText, final String pLowerText)
	{
		final String value = pText ? pValue : "CAST(" + pValue + " AS VARCHAR)";
		final boolean special = pLowerText.indexOf('%') >= 0 || pLowerText.indexOf('_') >= 0 || pLowerText.indexOf('\\') >= 0;
		return "LOWER(" + value + ") LIKE " + (special ? Sql.contains(pLowerText) + " ESCAPE '\\'" : Sql.lit("%" + pLowerText + "%"));
	}
}
