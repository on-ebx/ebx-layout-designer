package com.on.ebx.layout.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words the runtime writes (audit L2-21): they come from the bundle RuntimeText (English, French, Italian; any other
 * language reads English), the three files carry the same keys, every key the code asks for exists, and no new visible
 * literal slips into the code outside the bundle. Run by tools/test.sh with -Dlayout.root=&lt;repository&gt;.
 */
public final class RuntimeTextTest
{
	private static int failures;

	public static void main(final String[] pArgs) throws Exception
	{
		final File root = new File(System.getProperty("layout.root", "."));
		RuntimeTextTest.languages();
		RuntimeTextTest.sameKeys(root);
		RuntimeTextTest.keysUsedExist(root);
		RuntimeTextTest.noVisibleLiteral(root);
		if (RuntimeTextTest.failures > 0)
		{
			System.exit(1);
		}
	}

	private static void languages()
	{
		RuntimeTextTest.check("italian", RuntimeText.get("usedByTitle", Locale.ITALY), "Usato da");
		RuntimeTextTest.check("french", RuntimeText.get("usedByTitle", Locale.FRANCE), "Utilisé par");
		RuntimeTextTest.check("english", RuntimeText.get("usedByTitle", Locale.US), "Used by");
		RuntimeTextTest.check("spanish reads english", RuntimeText.get("usedByTitle", new Locale("es", "ES")), "Used by");
		RuntimeTextTest.check("null locale reads english", RuntimeText.get("usedByTitle", null), "Used by");
		RuntimeTextTest.check("unknown key is its own name", RuntimeText.get("noSuchKey", Locale.ITALY), "noSuchKey");
		RuntimeTextTest.check("placeholder", RuntimeText.get("hoursUntil", Locale.ITALY, "18:00"), "fino alle 18:00");
	}

	private static Properties load(final File pFile) throws Exception
	{
		final Properties p = new Properties();
		try (InputStream in = new FileInputStream(pFile))
		{
			p.load(in);
		}
		return p;
	}

	private static File bundleFile(final File pRoot, final String pSuffix)
	{
		return new File(pRoot, "runtime/resources/com/on/ebx/layout/runtime/RuntimeText" + pSuffix + ".properties");
	}

	private static void sameKeys(final File pRoot) throws Exception
	{
		final TreeSet<String> en = new TreeSet<String>(RuntimeTextTest.load(RuntimeTextTest.bundleFile(pRoot, "")).stringPropertyNames());
		for (final String s : new String[] { "_fr", "_it" })
		{
			final Properties p = RuntimeTextTest.load(RuntimeTextTest.bundleFile(pRoot, s));
			final TreeSet<String> missing = new TreeSet<String>(en);
			missing.removeAll(p.stringPropertyNames());
			final TreeSet<String> extra = new TreeSet<String>(p.stringPropertyNames());
			extra.removeAll(en);
			RuntimeTextTest.check("keys of RuntimeText" + s + " = English keys (missing " + missing + ", extra " + extra + ")", missing.isEmpty() && extra.isEmpty(), true);
			for (final String k : p.stringPropertyNames())
			{
				if (p.getProperty(k).trim().isEmpty())
				{
					RuntimeTextTest.check("empty text " + k + s, false, true);
				}
			}
		}
	}

	private static List<File> sources(final File pRoot)
	{
		final List<File> l = new ArrayList<File>();
		final File[] files = new File(pRoot, "runtime/src/com/on/ebx/layout/runtime").listFiles();
		for (final File f : files)
		{
			if (f.getName().endsWith(".java") && !f.getName().equals("RuntimeText.java"))
			{
				l.add(f);
			}
		}
		return l;
	}

	private static void keysUsedExist(final File pRoot) throws Exception
	{
		final Properties en = RuntimeTextTest.load(RuntimeTextTest.bundleFile(pRoot, ""));
		final Pattern use = Pattern.compile("(?:RuntimeText\\.get|\\bt)\\(\"([^\"]+)\"");
		int n = 0;
		final List<String> missing = new ArrayList<String>();
		for (final File f : RuntimeTextTest.sources(pRoot))
		{
			final Matcher m = use.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
			while (m.find())
			{
				n++;
				final String key = m.group(1);
				// a key written in two parts ("sm." + name): some key must start with it
				final boolean prefix = key.endsWith(".") || key.endsWith("_");
				boolean known = en.getProperty(key) != null;
				for (final String k : en.stringPropertyNames())
				{
					known |= prefix && k.startsWith(key);
				}
				if (!known)
				{
					missing.add(f.getName() + ":" + m.group(1));
				}
			}
		}
		RuntimeTextTest.check("keys asked for exist in the bundle (" + n + " uses, missing " + missing + ")", n > 50 && missing.isEmpty(), true);
	}

	/** A text a user reads, written in the code: tag content, title/aria-label/placeholder/alt attributes, accented letters in a literal. */
	private static void noVisibleLiteral(final File pRoot) throws Exception
	{
		final Pattern tagText = Pattern.compile("\">\\s*[A-Za-zÀ-ÿ][^<\"]*[a-z]{2}[^<\"]*<|>[A-Z][a-z]+( [A-Za-z]+)+<");
		final Pattern attr = Pattern.compile("(title|aria-label|placeholder|alt)=\\\\\"[A-Za-z]");
		final Pattern accent = Pattern.compile("\"[^\"]*[À-ÿ][^\"]*\"");
		final List<String> found = new ArrayList<String>();
		for (final File f : RuntimeTextTest.sources(pRoot))
		{
			int line = 0;
			for (final String s : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
			{
				line++;
				final String code = s.trim();
				// comments, and the day names OpeningHours understands in the data (upper case, input not output)
				if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*") || code.startsWith("{ \"MON\"") || code.matches("\\{ \"[A-Z]{3}\".*"))
				{
					continue;
				}
				// the one tag the module cannot move yet: the technical title of the map's iframe (LayoutPane, outside lot G)
				final String cleaned = s.replace("title=\\\"map\\\"", "");
				if (tagText.matcher(cleaned).find() || attr.matcher(cleaned).find() || accent.matcher(cleaned).find())
				{
					found.add(f.getName() + ":" + line);
				}
			}
		}
		RuntimeTextTest.check("no visible literal outside the bundle " + found, found.isEmpty(), true);
	}

	private static void check(final String pWhat, final Object pGot, final Object pWant)
	{
		final boolean ok = pWant.equals(pGot);
		System.out.println((ok ? "ok   " : "FAIL ") + pWhat + (ok ? "" : " (got " + pGot + ")"));
		if (!ok)
		{
			RuntimeTextTest.failures++;
		}
	}
}
