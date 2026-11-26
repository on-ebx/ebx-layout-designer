package com.on.ebx.layout.designer;

import java.io.File;
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
	}

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

	private static String write(final State pState, final Path pTable, final String pClass) throws Exception
	{
		if (pState.file == null)
		{
			throw new IllegalStateException("no XSD file for this model");
		}
		final String xsd = new String(Files.readAllBytes(pState.file.toPath()), StandardCharsets.UTF_8);
		final int[] range = XsdActivator.osdTable(xsd, pTable);
		if (range == null)
		{
			throw new IllegalStateException("table " + pTable.format() + " not found in " + pState.file.getName());
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
		final String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		final File backup = new File(pState.file.getParentFile(), pState.file.getName() + ".bak-" + stamp + "-layout-designer");
		Files.copy(pState.file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
		final String updated = xsd.substring(0, range[0]) + newBlock + xsd.substring(range[1]);
		Files.write(pState.file.toPath(), updated.getBytes(StandardCharsets.UTF_8));
		return previous;
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

	/** The folder of the web application whose WEB-INF/ebx/module.xml declares this module name. */
	static File moduleDirectory(final String pModuleName)
	{
		final String base = System.getProperty("catalina.base");
		if (base == null)
		{
			return null;
		}
		final List<File> candidates = new ArrayList<File>();
		final File contexts = new File(base, "conf/Catalina/localhost");
		final File[] xmls = contexts.listFiles();
		if (xmls != null)
		{
			for (final File xml : xmls)
			{
				if (!xml.getName().endsWith(".xml"))
				{
					continue;
				}
				try
				{
					final String text = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8);
					final Matcher m = Pattern.compile("docBase=\"([^\"]+)\"").matcher(text);
					if (m.find())
					{
						candidates.add(new File(m.group(1).replace("${catalina.base}", base).replace("${catalina.home}", System.getProperty("catalina.home", base))));
					}
				}
				catch (final Exception ex)
				{
					// unreadable context file
				}
			}
		}
		final File[] webapps = new File(base, "webapps").listFiles();
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
					return dir;
				}
			}
			catch (final Exception ex)
			{
				// unreadable module.xml
			}
		}
		return null;
	}
}
