package com.on.ebx.layout.explorer;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

/**
 * What keeps a hostile value from becoming markup, script or a query (audit L2-22), on one hostile set: the HTML escaping
 * of the runtime ({@code LayoutPane.esc} and the private copies of the other blocks), that of the module's pages (the
 * {@code esc} of each script), and the SQL / XPath literals and identifiers of the explorers. Run by tools/test.sh with
 * -Dlayout.root=&lt;repository&gt;.
 */
public final class EscapingTest
{
	private static final String[] HOSTILE = { "<script>alert(1)</script>", "\"><img src=x onerror=alert(1)>", "' onmouseover='x", "a&b &amp; &#39;", "</script><script>",
		"  ", "`${x}`", "\\\"; alert(1); //" };
	private static final Pattern SAFE = Pattern.compile("[^<>\"']*");
	private static final Pattern BARE_AMP = Pattern.compile("&(?!(amp|lt|gt|quot|#39);)");
	private static int failures;

	public static void main(final String[] pArgs) throws Exception
	{
		EscapingTest.html();
		EscapingTest.pages(new File(System.getProperty("layout.root", ".")));
		EscapingTest.sql();
		EscapingTest.xpath();
		EscapingTest.identifier();
		if (EscapingTest.failures > 0)
		{
			System.exit(1);
		}
	}

	private static void html() throws Exception
	{
		for (final String cls : new String[] { "LayoutPane", "ActionsBlock", "DisplayWidgets", "MoreBlocks", "MdmBlocks", "ChartBlocks" })
		{
			final Method esc = Class.forName("com.on.ebx.layout.runtime." + cls).getDeclaredMethod("esc", String.class);
			esc.setAccessible(true);
			EscapingTest.check(cls + ".esc(null)", "", esc.invoke(null, (Object) null));
			for (final String h : EscapingTest.HOSTILE)
			{
				final String out = (String) esc.invoke(null, h);
				EscapingTest.check(cls + ".esc of " + h + " leaves no tag or quote (got " + out + ")", true, EscapingTest.SAFE.matcher(out).matches() && !EscapingTest.BARE_AMP.matcher(out).find());
			}
			EscapingTest.check(cls + ".esc entities", "&lt;a href=&quot;x&quot;&gt;&#39;&amp;", esc.invoke(null, "<a href=\"x\">'&"));
		}
	}

	/** Each page script draws user data through its own esc: it must map all five characters (read from the source, no JS engine here). */
	private static void pages(final File pRoot) throws Exception
	{
		final File www = new File(pRoot, "ebx-layout-designer/src/main/webapp/www");
		for (final String name : new String[] { "explorer.js", "home.js", "designer.js", "explorer-designer.js", "personalize.js" })
		{
			final String js = new String(Files.readAllBytes(new File(www, name).toPath()), StandardCharsets.UTF_8);
			final java.util.regex.Matcher m = Pattern.compile("(?:const|var|function) esc\\b[^\\n]*").matcher(js);
			final boolean found = m.find();
			final String def = found ? m.group() : "";
			EscapingTest.check(name + " defines esc", true, found);
			for (final String entity : new String[] { "&amp;", "&lt;", "&gt;", "&quot;", "&#39;" })
			{
				EscapingTest.check(name + " esc maps " + entity, true, !found || def.contains(entity));
			}
		}
	}

	private static void sql()
	{
		EscapingTest.check("Sql.lit doubles quotes", "'a''b'", Sql.lit("a'b"));
		EscapingTest.check("Sql.lit of an injection stays one literal", "'x''; DROP TABLE t; --'", Sql.lit("x'; DROP TABLE t; --"));
		EscapingTest.check("Sql.lit null", "NULL", Sql.lit(null));
		EscapingTest.check("Sql.like escapes % and the quote", "LOWER(t.x) LIKE '%a\\%b''c%' ESCAPE '\\'", Sql.like("t.x", true, "a%b'c"));
		EscapingTest.check("Sql.like escapes _ and the backslash", "LOWER(t.x) LIKE '%a\\_b\\\\c%' ESCAPE '\\'", Sql.like("t.x", true, "a_b\\c"));
		EscapingTest.check("Sql.like plain text: no ESCAPE, no CAST", "LOWER(t.x) LIKE '%abc%'", Sql.like("t.x", true, "abc"));
		EscapingTest.check("Sql.like non text is cast", "LOWER(CAST(t.n AS VARCHAR)) LIKE '%1%'", Sql.like("t.n", false, "1"));
		EscapingTest.check("Sql.number plain notation", "1000000", Sql.number(1e6));
		EscapingTest.check("Sql.number decimal", "0.5", Sql.number(0.5));
	}

	private static void xpath()
	{
		EscapingTest.check("XPath.lit doubles quotes", "'it''s'", XPath.lit("it's"));
		EscapingTest.check("XPath.lit of a predicate break-out stays one literal", "'x'' or ''1''=''1'", XPath.lit("x' or '1'='1"));
		EscapingTest.check("XPath.lit null", "'null'", XPath.lit(null));
	}

	private static void identifier()
	{
		EscapingTest.check("Model.quote doubles double quotes", "\"a\"\"b\"", Model.quote("a\"b"));
		EscapingTest.check("Model.quote of a break-out stays one identifier", "\"x\"\" FROM secret --\"", Model.quote("x\" FROM secret --"));
	}

	private static void check(final String pWhat, final Object pWant, final Object pGot)
	{
		final boolean ok = pWant.equals(pGot);
		System.out.println((ok ? "ok   " : "FAIL ") + pWhat + (ok ? "" : " (got " + pGot + ")"));
		if (!ok)
		{
			EscapingTest.failures++;
		}
	}
}
