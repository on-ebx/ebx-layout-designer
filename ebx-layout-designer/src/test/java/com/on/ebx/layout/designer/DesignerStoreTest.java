package com.on.ebx.layout.designer;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import com.orchestranetworks.schema.Path;

/**
 * L2-13, L2-14, L2-19 and L2-12, without a server: the designer refuses a layout the MCP would refuse, the activation never
 * leaves a malformed XSD nor works without the lock, the cache-busting version follows the build's, and a failure shows a code.
 * Run by tools/test.sh with -Dlayout.root=<repository root>.
 */
public final class DesignerStoreTest
{
	private static int failures;

	private static final String XSD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
		+ "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:osd=\"urn:ebx-schemas:common_1.0\">\n"
		+ "<xs:element name=\"root\"><xs:complexType><xs:sequence>\n"
		+ "<xs:element name=\"Companies\" minOccurs=\"0\" maxOccurs=\"unbounded\">\n"
		+ "\t<xs:annotation><xs:appinfo>\n"
		+ "\t\t<osd:table>\n"
		+ "\t\t\t<primaryKeys>/id</primaryKeys>\n"
		+ "\t\t\t<!-- <recordForm osd:class=\"x\"/> -->\n"
		+ "\t\t</osd:table>\n"
		+ "\t</xs:appinfo></xs:annotation>\n"
		+ "\t<xs:complexType><xs:sequence><xs:element name=\"id\" type=\"xs:int\"/></xs:sequence></xs:complexType>\n"
		+ "</xs:element>\n"
		+ "</xs:sequence></xs:complexType></xs:element>\n</xs:schema>\n";

	public static void main(final String[] pArgs) throws Exception
	{
		DesignerStoreTest.refusal();
		DesignerStoreTest.activation();
		DesignerStoreTest.assetVersion();
		DesignerStoreTest.coded();
		if (DesignerStoreTest.failures > 0)
		{
			System.out.println(DesignerStoreTest.failures + " designer store check(s) failed");
			System.exit(1);
		}
		System.out.println("Designer store checks passed");
	}

	private static void refusal()
	{
		final Locale en = Locale.ENGLISH;
		final String empty = DesignerStoreTest.refused("{\"tabs\":[]}");
		DesignerStoreTest.check("a layout without tabs is refused, coded", empty != null && empty.contains("LD-INVALID") && empty.contains("tabs"));
		DesignerStoreTest.check("not JSON is refused, coded", String.valueOf(DesignerStoreTest.refused("{oops")).contains("LD-INVALID-JSON"));
		DesignerStoreTest.check("nothing is refused, coded", String.valueOf(DesignerStoreTest.refused("")).contains("LD-EMPTY"));
		final StringBuilder deep = new StringBuilder();
		for (int i = 0; i < 500; i++)
		{
			deep.append("{\"a\":");
		}
		deep.append("1");
		for (int i = 0; i < 500; i++)
		{
			deep.append("}");
		}
		DesignerStoreTest.check("a layout nested too deep is refused", String.valueOf(DesignerStoreTest.refused(deep.toString())).contains("LD-TOO-DEEP"));
		DesignerStoreTest.check("a layout too large is refused",
			String.valueOf(DesignerStoreTest.refused("{\"x\":\"" + new String(new char[LayoutMcpEndpoint.maxBodyBytes() + 1]).replace('\0', 'x') + "\"}")).contains("LD-TOO-LARGE"));
		final String noId = DesignerStoreTest.refused("{\"format\":\"ebx-form-layout/1\",\"tabs\":[{\"label\":\"A\",\"rows\":[{\"columns\":1,\"blocks\":[[{\"type\":\"panel\",\"fields\":[]}]]}]}]}");
		DesignerStoreTest.check("a block without id is refused", noId != null && noId.contains("\"id\""));
		DesignerStoreTest.check("the refusal is localized", String.valueOf(DesignerStoreTest.refusal(Locale.FRENCH, "{\"tabs\":[]}")).contains("Non enregistr"));
		DesignerStoreTest.check("a sound layout passes the shape check", DesignerStoreTest.refused(
			"{\"format\":\"ebx-form-layout/1\",\"tabs\":[{\"label\":\"A\",\"rows\":[{\"columns\":1,\"blocks\":[[{\"type\":\"panel\",\"id\":\"p1\",\"fields\":[]}]]}]}]}") == null);
		if (en == null)
		{
			throw new IllegalStateException();
		}
	}

	private static String refused(final String pText)
	{
		return DesignerStoreTest.refusal(Locale.ENGLISH, pText);
	}

	private static String refusal(final Locale pLocale, final String pText)
	{
		return DesignerUserService.refusal(null, pLocale, null, pText);
	}

	private static void activation() throws Exception
	{
		final File dir = Files.createTempDirectory("ld-activation").toFile();
		try
		{
			final Path table = Path.parse("/root/Companies");
			// 1.30.0: by default the server keeps its modules as delivered; the designer describes, it does not write
			System.clearProperty(XsdActivator.WRITE_PROPERTY);
			DesignerStoreTest.check("writing a module's XSD is off by default", !XsdActivator.writeEnabled());
			final File kept = new File(dir, "Kept.xsd");
			Files.write(kept.toPath(), DesignerStoreTest.XSD.getBytes(StandardCharsets.UTF_8));
			final XsdActivator.State keptState = new XsdActivator.State();
			keptState.file = kept;
			String off = null;
			try
			{
				XsdActivator.write(keptState, table, "com.on.ebx.layout.runtime.ConfigurableForm");
			}
			catch (final XsdActivator.Refusal ex)
			{
				off = ex.text(Locale.ENGLISH);
			}
			DesignerStoreTest.check("an activation is refused while the property is off, coded, naming the property",
				off != null && off.contains("LD-WRITE-OFF") && off.contains(XsdActivator.WRITE_PROPERTY));
			DesignerStoreTest.check("the refusal exists in French and Italian",
				new XsdActivator.Refusal("disabled", "p").text(Locale.FRENCH).contains("LD-WRITE-OFF") && new XsdActivator.Refusal("disabled", "p").text(Locale.ITALIAN).contains("LD-WRITE-OFF"));
			DesignerStoreTest.check("the XSD is left byte for byte as it was", DesignerStoreTest.XSD.equals(new String(Files.readAllBytes(kept.toPath()), StandardCharsets.UTF_8)));
			DesignerStoreTest.check("no backup, lock or temporary file is written next to it", dir.list().length == 1);
			System.setProperty(XsdActivator.WRITE_PROPERTY, "false");
			DesignerStoreTest.check("'false' keeps it off", !XsdActivator.writeEnabled());
			System.setProperty(XsdActivator.WRITE_PROPERTY, " TRUE ");
			DesignerStoreTest.check("'true' (any case, spaces around) turns it on, with no restart", XsdActivator.writeEnabled());
			DesignerStoreTest.check("the line to declare names the form class", XsdActivator.declarationLine().equals("<recordForm osd:class=\"com.on.ebx.layout.runtime.ConfigurableForm\"/>"));
			Files.delete(kept.toPath());

			// well-formed: the form is declared, atomically, with a backup and no leftover
			final File ok = new File(dir, "Good.xsd");
			Files.write(ok.toPath(), DesignerStoreTest.XSD.getBytes(StandardCharsets.UTF_8));
			final XsdActivator.State state = new XsdActivator.State();
			state.file = ok;
			XsdActivator.write(state, table, "com.on.ebx.layout.runtime.ConfigurableForm");
			final String after = new String(Files.readAllBytes(ok.toPath()), StandardCharsets.UTF_8);
			DesignerStoreTest.check("activation writes the class", after.contains("com.on.ebx.layout.runtime.ConfigurableForm"));
			DesignerStoreTest.check("the XSD is still well-formed (a comment holding recordForm included)", XsdActivator.notWellFormed(after) == null);
			DesignerStoreTest.check("a backup is kept", DesignerStoreTest.count(dir, ".bak-") == 1);
			DesignerStoreTest.check("no temporary file is left", DesignerStoreTest.count(dir, ".tmp-") == 0);

			// not well-formed: refused, file untouched, backup kept
			final File bad = new File(dir, "Bad.xsd");
			final String broken = DesignerStoreTest.XSD.replace("</xs:schema>", "");
			Files.write(bad.toPath(), broken.getBytes(StandardCharsets.UTF_8));
			final XsdActivator.State badState = new XsdActivator.State();
			badState.file = bad;
			String refusal = null;
			try
			{
				XsdActivator.write(badState, table, "com.on.ebx.layout.runtime.ConfigurableForm");
			}
			catch (final XsdActivator.Refusal ex)
			{
				refusal = ex.text(Locale.ENGLISH);
			}
			DesignerStoreTest.check("a malformed XSD is refused, coded", refusal != null && refusal.contains("LD-XSD-INVALID"));
			DesignerStoreTest.check("the malformed XSD is left byte for byte as it was", broken.equals(new String(Files.readAllBytes(bad.toPath()), StandardCharsets.UTF_8)));
			DesignerStoreTest.check("its backup is kept", DesignerStoreTest.count(dir, "Bad.xsd.bak-") == 1);

			// a lock held elsewhere: refused, nothing changed
			final File busy = new File(dir, "Busy.xsd");
			Files.write(busy.toPath(), DesignerStoreTest.XSD.getBytes(StandardCharsets.UTF_8));
			final XsdActivator.State busyState = new XsdActivator.State();
			busyState.file = busy;
			final File lockFile = new File(dir, "Busy.xsd.layout-designer.lock");
			String busyRefusal = null;
			try (FileChannel channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock held = channel.lock())
			{
				try
				{
					XsdActivator.write(busyState, table, "com.on.ebx.layout.runtime.ConfigurableForm");
				}
				catch (final XsdActivator.Refusal ex)
				{
					busyRefusal = ex.text(Locale.ENGLISH);
				}
				DesignerStoreTest.check("the lock is held", held.isValid());
			}
			DesignerStoreTest.check("a change under a held lock is refused, coded", busyRefusal != null && busyRefusal.contains("LD-BUSY"));
			DesignerStoreTest.check("nothing was written under the lock", DesignerStoreTest.XSD.equals(new String(Files.readAllBytes(busy.toPath()), StandardCharsets.UTF_8)));
		}
		finally
		{
			System.clearProperty(XsdActivator.WRITE_PROPERTY);
			final File[] files = dir.listFiles();
			if (files != null)
			{
				for (final File f : files)
				{
					f.delete();
				}
			}
			dir.delete();
		}
	}

	private static int count(final File pDir, final String pPart)
	{
		int n = 0;
		for (final String name : pDir.list())
		{
			if (name.contains(pPart))
			{
				n++;
			}
		}
		return n;
	}

	private static void assetVersion() throws Exception
	{
		DesignerStoreTest.check("the cache-busting version is the build's", "1.23.3".equals(DesignerUserService.assetVersion("1.23.3")));
		DesignerStoreTest.check("a development version is made URL-safe", !DesignerUserService.assetVersion("1.23.3-dev+abc 1").contains("+")
			&& !DesignerUserService.assetVersion("1.23.3-dev+abc 1").contains(" "));
		DesignerStoreTest.check("an unstamped build never gets a constant", DesignerUserService.assetVersion("unknown").startsWith("t"));
		final String root = System.getProperty("layout.root");
		if (root != null)
		{
			final String version = new String(Files.readAllBytes(new File(root, "VERSION").toPath()), StandardCharsets.UTF_8).trim();
			DesignerStoreTest.check("the version the build stamps starts with VERSION (" + version + ")", DesignerUserService.assetVersion(version).equals(version));
			final String source = new String(Files.readAllBytes(new File(root, "ebx-layout-designer/src/main/java/com/on/ebx/layout/designer/DesignerUserService.java").toPath()),
				StandardCharsets.UTF_8);
			DesignerStoreTest.check("no literal ASSET_VERSION in the source", !source.matches("(?s).*ASSET_VERSION\\s*=\\s*\"[0-9].*"));
		}
	}

	private static void coded()
	{
		final String text = DesignerUserService.fail(Locale.ENGLISH, "test", new IllegalStateException("SELECT secret FROM x at com.foo.Bar"));
		DesignerStoreTest.check("a failure shows a code, not the exception", text.contains("LDD-") && !text.contains("SELECT") && !text.contains("com.foo"));
		DesignerStoreTest.check("the code is localized", DesignerUserService.fail(Locale.FRENCH, "test", new RuntimeException("x")).contains("inattendue"));
		DesignerStoreTest.check("and Italian", DesignerUserService.fail(Locale.ITALIAN, "test", new RuntimeException("x")).contains("imprevisto"));
	}

	private static void check(final String pWhat, final boolean pOk)
	{
		System.out.println((pOk ? "ok   " : "FAIL ") + pWhat);
		if (!pOk)
		{
			DesignerStoreTest.failures++;
		}
	}
}
