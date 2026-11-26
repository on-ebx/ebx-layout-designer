package com.on.ebx.layout.explorer;

/** Literals of EBX's XPath predicates: single quotes, an inner quote doubled. */
final class XPath
{
	private XPath()
	{
	}

	static String lit(final String pText)
	{
		return "'" + String.valueOf(pText).replace("'", "''") + "'";
	}
}
