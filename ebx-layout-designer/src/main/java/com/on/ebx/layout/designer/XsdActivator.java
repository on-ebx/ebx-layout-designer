package com.on.ebx.layout.designer;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.on.ebx.layout.runtime.LayoutPaths;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;

/**
 * Declares the designed form in the XSD of a model packaged in a module: the {@code recordForm} line of the table's
 * {@code osd:table}. The module's folder is found from the Tomcat contexts ({@code conf/Catalina/localhost/*.xml}) and
 * the exploded web applications, by the {@code <name>} of each {@code WEB-INF/ebx/module.xml}. Every change keeps a
 * copy of the file ({@code .bak-<time>-layout-designer}); EBX reads the change at the next start.
 * <p>
 * Since 1.23.3 (audit L2-14) a change is made under a lock (this class's monitor, and a lock file next to the XSD, so that
 * another administrator, another module copy or another process waits or is refused), written to a temporary file that
 * replaces the XSD in one move, and only after the new text is checked to be well-formed XML; otherwise the XSD is left
 * untouched, the backup kept, and the refusal says what to do.
 */
final class XsdActivator
{
	static final class State
	{
		/** The XSD file, null when the model is not a module file this server can reach. */
		File file;
		/** Why there is no file (a DMA publication, a module not found). */
		String reason;
		/** The recordForm class the table declares now, null for none. */
		String currentForm;
		boolean declared;
		/** Other copies of the same XSD found under other folders that declare this module (L2-14): the first one is edited. */
		final List<String> others = new ArrayList<String>();
	}

	private static boolean sameFile(final File pA, final File pB)
	{
		try
		{
			return pA.getCanonicalFile().equals(pB.getCanonicalFile());
		}
		catch (final IOException ex)
		{
			return pA.equals(pB);
		}
	}

	/** The warning added to the confirmation when several folders declare the module (empty otherwise). */
	static String otherCandidates(final State pState, final Locale pLocale)
	{
		if (pState.others.isEmpty())
		{
			return "";
		}
		return " " + Messages.get(pLocale, "activatedMany", pState.others.size(), String.join(", ", pState.others));
	}

	/** A refusal written for the administrator: what is wrong and what to do (a bundle key {@code activation.<key>}). */
	static final class Refusal extends Exception
	{
		private static final long serialVersionUID = 1L;
		final String key;
		final Object[] args;

		Refusal(final String pKey, final Object... pArgs)
		{
			super(pKey);
			this.key = pKey;
			this.args = pArgs;
		}

		String text(final Locale pLocale)
		{
			return Messages.get(pLocale, "activation." + this.key, this.args);
		}
	}

	private static final Object LOCK = new Object();

	private static final Pattern RECORD_FORM = Pattern.compile("<recordForm\\s+osd:class=\"([^\"]*)\"\\s*/>");

	private XsdActivator()
	{
	}

	static State state(final SchemaLocation pModel, final Path pTable)
	{
		final State s = new State();
		if (pModel.isPublication() || !pModel.isPackagedInModule())
		{
			s.reason = "dma";
			return s;
		}
		final File dir = XsdActivator.moduleDirectory(pModel.getModuleName());
		if (dir == null)
		{
			s.reason = "module";
			return s;
		}
		final File file = new File(dir, pModel.getPathInModule().replaceFirst("^/", ""));
		if (!file.isFile())
		{
			s.reason = "file";
			return s;
		}
		s.file = file;
		for (final File other : XsdActivator.moduleDirectories(pModel.getModuleName()))
		{
			final File candidate = new File(other, pModel.getPathInModule().replaceFirst("^/", ""));
			if (candidate.isFile() && !XsdActivator.sameFile(candidate, file))
			{
				s.others.add(candidate.getAbsolutePath());
			}
		}
		try
		{
			final String xsd = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			final int[] range = XsdActivator.osdTable(xsd, pTable);
			if (range == null)
			{
				s.reason = "table";
				s.file = null;
				return s;
			}
			final Matcher m = XsdActivator.RECORD_FORM.matcher(xsd.substring(range[0], range[1]));
			if (m.find())
			{
				s.currentForm = m.group(1);
				s.declared = LayoutPaths.FORM_CLASS.equals(s.currentForm);
			}
		}
		catch (final Exception ex)
		{
			s.reason = "read";
			s.file = null;
		}
		return s;
	}

	/** Puts the designer's class in place; answers the class it replaces (empty when the table had none). */
	static String activate(final State pState, final Path pTable) throws Exception
	{
		return XsdActivator.write(pState, pTable, LayoutPaths.FORM_CLASS);
	}

	/** Puts back the previous class, or removes the line when there was none. */
	static void deactivate(final State pState, final Path pTable, final String pPrevious) throws Exception
	{
		XsdActivator.write(pState, pTable, pPrevious == null || pPrevious.isEmpty() ? null : pPrevious);
	}

	/** Visible to the tests. */
	static String write(final State pState, final Path pTable, final String pClass) throws Exception
	{
		if (pState.file == null)
		{
			throw new Refusal("noFile");
		}
		synchronized (XsdActivator.LOCK)
		{
			final File lockFile = new File(pState.file.getParentFile(), pState.file.getName() + ".layout-designer.lock");
			try (FileChannel channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE))
			{
				final FileLock lock;
				try
				{
					lock = channel.tryLock();
				}
				catch (final OverlappingFileLockException ex)
				{
					throw new Refusal("busy", pState.file.getName());
				}
				if (lock == null)
				{
					throw new Refusal("busy", pState.file.getName());
				}
				try
				{
					return XsdActivator.writeLocked(pState, pTable, pClass);
				}
				finally
				{
					lock.release();
				}
			}
		}
	}

	private static String writeLocked(final State pState, final Path pTable, final String pClass) throws Exception
	{
		final String xsd = new String(Files.readAllBytes(pState.file.toPath()), StandardCharsets.UTF_8);
		final int[] range = XsdActivator.osdTable(xsd, pTable);
		if (range == null)
		{
			throw new Refusal("noTable", pTable.format(), pState.file.getName());
		}
		final String block = xsd.substring(range[0], range[1]);
		final Matcher m = XsdActivator.RECORD_FORM.matcher(block);
		String previous = "";
		String newBlock;
		if (m.find())
		{
			previous = m.group(1);
			if (pClass == null)
			{
				// drop the whole line
				final int lineStart = block.lastIndexOf('\n', m.start()) + 1;
				final int lineEnd = block.indexOf('\n', m.end());
				newBlock = block.substring(0, lineStart) + block.substring(lineEnd < 0 ? m.end() : lineEnd + 1);
			}
			else
			{
				newBlock = block.substring(0, m.start()) + "<recordForm osd:class=\"" + pClass + "\"/>" + block.substring(m.end());
			}
		}
		else
		{
			if (pClass == null)
			{
				return previous;
			}
			// after defaultLabel, else after primaryKeys, else first in osd:table; same indentation as that line
			int anchor;
			if (block.indexOf("</defaultLabel>") >= 0)
			{
				anchor = block.indexOf("</defaultLabel>") + "</defaultLabel>".length();
			}
			else if (block.indexOf("</primaryKeys>") >= 0)
			{
				anchor = block.indexOf("</primaryKeys>") + "</primaryKeys>".length();
			}
			else
			{
				anchor = "<osd:table>".length();
			}
			final int lineStart = block.lastIndexOf('\n', anchor - 1) + 1;
			final StringBuilder indent = new StringBuilder();
			for (int i = lineStart; i < block.length() && (block.charAt(i) == ' ' || block.charAt(i) == '\t'); i++)
			{
				indent.append(block.charAt(i));
			}
			newBlock = block.substring(0, anchor) + "\n" + indent + "<recordForm osd:class=\"" + pClass + "\"/>" + block.substring(anchor);
		}
		final String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
		final File backup = new File(pState.file.getParentFile(), pState.file.getName() + ".bak-" + stamp + "-layout-designer");
		Files.copy(pState.file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
		final String updated = xsd.substring(0, range[0]) + newBlock + xsd.substring(range[1]);
		// never replace the XSD with text EBX could not load: a model that does not load takes every table of it down at the next start
		final String problem = XsdActivator.notWellFormed(updated);
		if (problem != null)
		{
			throw new Refusal(XsdActivator.notWellFormed(xsd) == null ? "malformed" : "alreadyMalformed", pState.file.getName(), backup.getName());
		}
		final File temp = new File(pState.file.getParentFile(), pState.file.getName() + ".tmp-" + stamp + "-layout-designer");
		try
		{
			Files.copy(pState.file.toPath(), temp.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
			Files.write(temp.toPath(), updated.getBytes(StandardCharsets.UTF_8));
			try
			{
				Files.move(temp.toPath(), pState.file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (final AtomicMoveNotSupportedException ex)
			{
				Files.move(temp.toPath(), pState.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			Files.deleteIfExists(temp.toPath());
		}
		return previous;
	}

	/** Null when the text is well-formed XML (no DOCTYPE, no external entity), else the parser's complaint. */
	static String notWellFormed(final String pXml)
	{
		try
		{
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setXIncludeAware(false);
			final DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(null);
			builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(pXml)));
			return null;
		}
		catch (final Exception ex)
		{
			return String.valueOf(ex.getMessage());
		}
	}

	/**
	 * The {@code <osd:table>…</osd:table>} span of the table: the element at the path, found by its real nesting
	 * ({@code root}, then {@code Companies}…), then the first {@code osd:table} after it.
	 */
	static int[] osdTable(final String pXsd, final Path pTable)
	{
		final List<String> steps = new ArrayList<String>();
		for (final String step : pTable.format().split("/"))
		{
			if (!step.isEmpty())
			{
				steps.add(step);
			}
		}
		final int at = XsdActivator.elementStart(pXsd, steps);
		if (at < 0)
		{
			return null;
		}
		// the table's own osd:table sits in its annotation, before its complexType
		final int start = pXsd.indexOf("<osd:table>", at);
		final int complex = pXsd.indexOf("<xs:complexType", at);
		if (start < 0 || (complex >= 0 && complex < start))
		{
			return null;
		}
		final int end = pXsd.indexOf("</osd:table>", start);
		return end < 0 ? null : new int[] { start, end + "</osd:table>".length() };
	}

	private static final Pattern ELEMENT_TAG = Pattern.compile("<xs:element\\b[^>]*?(/?)>|</xs:element>");
	private static final Pattern NAME_ATTR = Pattern.compile("\\bname=\"([^\"]*)\"");

	/**
	 * Where the element at this path of nested xs:element names opens, or -1. Follows the real nesting: a group
	 * further up that happens to carry the same name as the table (a "Contact" group inside another table) is skipped.
	 */
	static int elementStart(final String pXsd, final List<String> pSteps)
	{
		final List<String> stack = new ArrayList<String>();
		final Matcher m = XsdActivator.ELEMENT_TAG.matcher(pXsd);
		while (m.find())
		{
			if (m.group().startsWith("</"))
			{
				if (!stack.isEmpty())
				{
					stack.remove(stack.size() - 1);
				}
				continue;
			}
			final Matcher n = XsdActivator.NAME_ATTR.matcher(m.group());
			final String name = n.find() ? n.group(1) : "";
			stack.add(name);
			if (stack.equals(pSteps))
			{
				return m.start();
			}
			if ("/".equals(m.group(1)))
			{
				stack.remove(stack.size() - 1);
			}
		}
		return -1;
	}

	/** The folder of the web application whose WEB-INF/ebx/module.xml declares this module name (the first one found). */
	static File moduleDirectory(final String pModuleName)
	{
		final List<File> all = XsdActivator.moduleDirectories(pModuleName);
		return all.isEmpty() ? null : all.get(0);
	}

	/** Every folder that declares this module name: Tomcat serves one of them, which is not always the first. */
	static List<File> moduleDirectories(final String pModuleName)
	{
		final List<File> found = new ArrayList<File>();
		final String base = System.getProperty("catalina.base");
		if (base == null)
		{
			return found;
		}
		final List<File> candidates = new ArrayList<File>();
		// contexts of conf/Catalina/localhost/*.xml, and those declared in conf/server.xml (a Tomcat run by Eclipse)
		final List<File> configs = new ArrayList<File>();
		final File[] xmls = new File(base, "conf/Catalina/localhost").listFiles();
		if (xmls != null)
		{
			for (final File xml : xmls)
			{
				if (xml.getName().endsWith(".xml"))
				{
					configs.add(xml);
				}
			}
		}
		configs.add(new File(base, "conf/server.xml"));
		final Pattern docBase = Pattern.compile("<Context\\b[^>]*?\\bdocBase=\"([^\"]+)\"");
		for (final File xml : configs)
		{
			if (!xml.isFile())
			{
				continue;
			}
			try
			{
				final Matcher m = docBase.matcher(new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8));
				while (m.find())
				{
					final String path = m.group(1).replace("${catalina.base}", base).replace("${catalina.home}", System.getProperty("catalina.home", base));
					final File dir = new File(path);
					// a relative docBase is read against the host's appBase: webapps, or wtpwebapps for a Tomcat run by Eclipse
					if (dir.isAbsolute())
					{
						candidates.add(dir);
					}
					else
					{
						candidates.add(new File(new File(base, "webapps"), path));
						candidates.add(new File(new File(base, "wtpwebapps"), path));
					}
				}
			}
			catch (final Exception ex)
			{
				// unreadable context file
			}
		}
		for (final String appBase : new String[] { "webapps", "wtpwebapps" })
		{
			final File[] webapps = new File(base, appBase).listFiles();
			if (webapps != null)
			{
				for (final File w : webapps)
				{
					if (w.isDirectory())
					{
						candidates.add(w);
					}
				}
			}
		}
		for (final File dir : candidates)
		{
			final File moduleXml = new File(dir, "WEB-INF/ebx/module.xml");
			if (!moduleXml.isFile())
			{
				continue;
			}
			try
			{
				final String text = new String(Files.readAllBytes(moduleXml.toPath()), StandardCharsets.UTF_8);
				final Matcher m = Pattern.compile("<name>([^<]*)</name>").matcher(text);
				if (m.find() && m.group(1).trim().equals(pModuleName))
				{
					boolean known = false;
					for (final File f : found)
					{
						known |= XsdActivator.sameFile(f, dir);
					}
					if (!known)
					{
						found.add(dir);
					}
				}
			}
			catch (final Exception ex)
			{
				// unreadable module.xml
			}
		}
		return found;
	}
}
