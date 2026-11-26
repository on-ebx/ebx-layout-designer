package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.service.ValidationReport;
import com.orchestranetworks.service.ValidationReportItem;
import com.orchestranetworks.service.ValidationReportItemIterator;
import com.orchestranetworks.service.ValidationReportItemSubjectForAdaptation;

/**
 * The validation report of a record, as the "validation report" block shows it: severity, message in the asked
 * locale (EBX translates it), and the node it is about ({@code /Group/field}, null for the record as a whole).
 */
public final class ReportItems
{
	public static final String ERROR = "error";
	public static final String WARNING = "warning";
	public static final String INFO = "info";

	public static final class Item
	{
		public final String severity;
		public final String message;
		/** As stored in a layout, {@code /Group/field}; null when the message is about the record. */
		public final String node;

		Item(final String pSeverity, final String pMessage, final String pNode)
		{
			this.severity = pSeverity;
			this.message = pMessage;
			this.node = pNode;
		}
	}

	private ReportItems()
	{
	}

	public static List<Item> read(final Adaptation pRecord, final Locale pLocale)
	{
		final List<Item> items = new ArrayList<Item>();
		if (pRecord == null)
		{
			return items;
		}
		final ValidationReport report = pRecord.getValidationReport();
		if (report == null || !report.hasItems())
		{
			return items;
		}
		final ValidationReportItemIterator it = report.getItemsOfSeverityOrMore(com.onwbp.base.text.Severity.INFO);
		try
		{
			while (it.hasNext())
			{
				final ValidationReportItem item = it.nextItem();
				final String severity = item.isFatal() || item.isError() ? ReportItems.ERROR : item.isWarning() ? ReportItems.WARNING : ReportItems.INFO;
				String node = null;
				final ValidationReportItemSubjectForAdaptation subject = item.getSubjectForAdaptation();
				if (subject != null && subject.getPathInAdaptation() != null)
				{
					final Path p = subject.getPathInAdaptation();
					if (!p.isRoot())
					{
						node = LayoutPaths.stored(p);
					}
				}
				items.add(new Item(severity, ReportItems.text(item.getMessage(), pLocale), node));
			}
		}
		finally
		{
			it.close();
		}
		Collections.sort(items, new Comparator<Item>()
		{
			@Override
			public int compare(final Item a, final Item b)
			{
				return ReportItems.rank(a.severity) - ReportItems.rank(b.severity);
			}
		});
		return items;
	}

	/**
	 * The message in the asked locale; a message written in one locale only (a DMA validation rule) formats to an
	 * empty text in the others, so fall back on the instance's default locale, then English, then its raw form.
	 */
	static String text(final com.onwbp.base.text.UserMessage pMessage, final Locale pLocale)
	{
		if (pMessage == null)
		{
			return "";
		}
		Locale def = Locale.ENGLISH;
		try
		{
			def = com.orchestranetworks.instance.Repository.getDefault().getDefaultLocale();
		}
		catch (final RuntimeException ex)
		{
			// no repository yet
		}
		for (final Locale l : new Locale[] { pLocale, def, Locale.US, Locale.ENGLISH })
		{
			if (l == null)
			{
				continue;
			}
			final String s = pMessage.formatMessage(l);
			if (s != null && !s.trim().isEmpty())
			{
				return s;
			}
		}
		final String raw = pMessage.toString();
		return raw == null ? "" : raw;
	}

	public static int rank(final String pSeverity)
	{
		return ReportItems.ERROR.equals(pSeverity) ? 0 : ReportItems.WARNING.equals(pSeverity) ? 1 : 2;
	}
}
