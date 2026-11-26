package com.on.ebx.layout.designer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.on.ebx.layout.runtime.LayoutComponent;
import com.on.ebx.layout.runtime.LayoutComponents;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.on.ebx.layout.runtime.ReportItems;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.instance.ValueContext;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.schema.SchemaTypeName;

/**
 * Everything the designer page needs, as one JSON object: the instance locales, the table's fields, groups and
 * associations with their labels in every locale, a record to preview with its values and its validation report,
 * and the stored layout (or a starting one). Read through the EBX Java API only, so it works without any MCP server.
 */
final class ModelExport
{
	private static final ObjectMapper JSON = LayoutStore.json();

	private ModelExport()
	{
	}

	static ObjectNode export(final Repository pRepository, final AdaptationTable pTable, final Adaptation pRecord, final Locale pUserLocale,
		final String pDraft, final XsdActivator.State pActivation, final String pNotice, final boolean pNoticeIsError)
	{
		final List<Locale> locales = pRepository.getLocales();
		final ObjectNode boot = ModelExport.JSON.createObjectNode();
		final ArrayNode loc = boot.putArray("locales");
		for (final Locale l : locales)
		{
			loc.add(l.toLanguageTag());
		}
		boot.put("defaultLocale", pRepository.getDefaultLocale().toLanguageTag());
		boot.put("userLocale", ModelExport.closest(pUserLocale, locales).toLanguageTag());

		final Adaptation dataset = pTable.getContainerAdaptation();
		final SchemaNode tableNode = pTable.getTableNode();
		final ObjectNode model = boot.putObject("model");
		model.put("location", dataset.getSchemaLocation().format());
		model.put("table", pTable.getTablePath().format());
		model.put("dataspace", dataset.getHome().getKey().getName());
		model.put("dataset", dataset.getAdaptationName().getStringName());
		model.set("tableLabel", ModelExport.labels(tableNode, locales));

		final ArrayNode fields = boot.putArray("fields");
		final ArrayNode groups = boot.putArray("groups");
		final ArrayNode assocs = boot.putArray("assocs");
		final ObjectNode values = boot.putObject("values");
		final ValueContext vc = pRecord == null ? null : pRecord.createValueContext();
		ModelExport.walk(pTable.getTableOccurrenceRootNode(), null, locales, pUserLocale, pRecord, vc, fields, groups, assocs, values);

		// the record shown in the preview, and the first records to choose another one
		final ObjectNode record = boot.putObject("record");
		if (pRecord != null)
		{
			record.put("key", pRecord.getOccurrencePrimaryKey().format());
			record.put("label", pRecord.getLabel(pUserLocale));
		}
		final ArrayNode records = boot.putArray("records");
		final RequestResult rows = pTable.createRequest().execute();
		try
		{
			int n = 0;
			for (Adaptation r = rows.nextAdaptation(); r != null && n < 40; r = rows.nextAdaptation(), n++)
			{
				final ObjectNode o = records.addObject();
				o.put("key", r.getOccurrencePrimaryKey().format());
				o.put("label", r.getLabel(pUserLocale));
			}
			boot.put("recordCount", rows.getSize());
		}
		finally
		{
			rows.close();
		}

		// the validation report, in every locale (EBX translates each message)
		final ArrayNode report = boot.putArray("report");
		if (pRecord != null)
		{
			final List<List<ReportItems.Item>> perLocale = new ArrayList<List<ReportItems.Item>>();
			for (final Locale l : locales)
			{
				perLocale.add(ReportItems.read(pRecord, l));
			}
			final List<ReportItems.Item> first = perLocale.isEmpty() ? new ArrayList<ReportItems.Item>() : perLocale.get(0);
			for (int i = 0; i < first.size(); i++)
			{
				final ObjectNode o = report.addObject();
				o.put("sev", first.get(i).severity);
				if (first.get(i).node != null)
				{
					o.put("node", first.get(i).node);
				}
				final ObjectNode t = o.putObject("t");
				for (int k = 0; k < locales.size(); k++)
				{
					final List<ReportItems.Item> items = perLocale.get(k);
					if (i < items.size())
					{
						t.put(locales.get(k).toLanguageTag(), items.get(i).message);
					}
				}
			}
		}

		// the stored layout, else the draft being edited, else a starting layout
		final Adaptation row = LayoutStore.findRow(pRepository, dataset.getSchemaLocation(), pTable.getTablePath());
		final ObjectNode stored = boot.putObject("stored");
		JsonNode layout = null;
		if (row != null)
		{
			stored.put("exists", true);
			stored.put("active", Boolean.TRUE.equals(row.get(LayoutPaths.ACTIVE)));
			stored.put("version", row.get(LayoutPaths.VERSION) == null ? 0 : ((Integer) row.get(LayoutPaths.VERSION)).intValue());
			stored.put("updatedBy", row.getString(LayoutPaths.UPDATED_BY));
			final Object on = row.get(LayoutPaths.UPDATED_ON);
			stored.put("updatedOn", on instanceof Date ? new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format((Date) on) : "");
			stored.put("previousForm", row.getString(LayoutPaths.PREVIOUS_FORM));
			layout = LayoutStore.parse("designer|" + dataset.getSchemaLocation().format() + "|" + pTable.getTablePath().format(), row.getString(LayoutPaths.LAYOUT));
		}
		else
		{
			stored.put("exists", false);
		}
		if (pDraft != null && !pDraft.isEmpty())
		{
			try
			{
				layout = ModelExport.JSON.readTree(pDraft);
				boot.put("draft", true);
			}
			catch (final Exception ex)
			{
				// keep the stored one
			}
		}
		if (layout == null)
		{
			layout = DefaultLayout.build(fields, groups, assocs, locales);
			boot.put("generated", true);
		}
		boot.set("layout", layout);

		final ObjectNode activation = boot.putObject("activation");
		activation.put("formClass", LayoutPaths.FORM_CLASS);
		activation.put("declared", pActivation.declared);
		activation.put("currentForm", pActivation.currentForm == null ? "" : pActivation.currentForm);
		activation.put("file", pActivation.file == null ? "" : pActivation.file.getAbsolutePath());
		activation.put("reason", pActivation.reason == null ? "" : pActivation.reason);

		final ArrayNode components = boot.putArray("components");
		for (final java.util.Map.Entry<String, LayoutComponent> e : LayoutComponents.all().entrySet())
		{
			final ObjectNode c = components.addObject();
			c.put("name", e.getKey());
			final ObjectNode labels = c.putObject("lab");
			for (final Locale l : locales)
			{
				labels.put(l.toLanguageTag(), e.getValue().getLabel(l));
			}
		}
		if (pNotice != null)
		{
			boot.put("notice", pNotice);
			boot.put("noticeError", pNoticeIsError);
		}
		return boot;
	}

	private static void walk(final SchemaNode pNode, final String pGroup, final List<Locale> pLocales, final Locale pUserLocale, final Adaptation pRecord,
		final ValueContext pValues, final ArrayNode pFields, final ArrayNode pGroups, final ArrayNode pAssocs, final ObjectNode pValuesOut)
	{
		for (final SchemaNode child : pNode.getNodeChildren())
		{
			if (ModelExport.hidden(child))
			{
				continue;
			}
			final String path = LayoutPaths.stored(child.getPathInAdaptation());
			if (child.isAssociationNode())
			{
				final ObjectNode a = pAssocs.addObject();
				a.put("p", path);
				a.set("lab", ModelExport.labels(child, pLocales));
				ModelExport.associationPreview(child, pRecord, pUserLocale, pLocales, a);
				continue;
			}
			final String kind = ModelExport.kind(child);
			if (kind == null && child.isComplex() && child.getMaxOccurs() == 1)
			{
				final ObjectNode g = pGroups.addObject();
				g.put("p", path);
				g.set("lab", ModelExport.labels(child, pLocales));
				ModelExport.walk(child, path, pLocales, pUserLocale, pRecord, pValues, pFields, pGroups, pAssocs, pValuesOut);
				continue;
			}
			final ObjectNode f = pFields.addObject();
			f.put("p", path);
			f.put("t", kind == null ? "list" : kind);
			if (pGroup != null)
			{
				f.put("g", pGroup);
			}
			f.set("lab", ModelExport.labels(child, pLocales));
			if (child.getMinOccurs() > 0 && child.isTerminalValue())
			{
				f.put("req", true);
			}
			if (child.isValueFunction())
			{
				f.put("fn", true);
			}
			if (pRecord != null)
			{
				try
				{
					final Object v = pRecord.get(child.getPathInAdaptation());
					if (v != null)
					{
						final String shown = "media".equals(kind) ? "" : child.displayOccurrence(v, true, pValues, pUserLocale);
						pValuesOut.put(path, shown == null ? String.valueOf(v) : shown);
						if (v instanceof Number || v instanceof BigDecimal)
						{
							pValuesOut.put(path + "#n", ((Number) v).doubleValue());
						}
					}
				}
				catch (final RuntimeException ex)
				{
					// a value that cannot be shown
				}
			}
		}
	}

	private static void associationPreview(final SchemaNode pAssoc, final Adaptation pRecord, final Locale pUserLocale, final List<Locale> pLocales, final ObjectNode pOut)
	{
		final ArrayNode rows = pOut.putArray("rows");
		final ArrayNode targetFields = pOut.putArray("fields");
		if (pRecord == null || pAssoc.getAssociationLink() == null)
		{
			pOut.put("n", 0);
			return;
		}
		try
		{
			final RequestResult result = pAssoc.getAssociationLink().getAssociationResult(pRecord);
			try
			{
				pOut.put("n", result.getSize());
				int n = 0;
				for (Adaptation r = result.nextAdaptation(); r != null && n < 6; r = result.nextAdaptation(), n++)
				{
					rows.add(r.getLabel(pUserLocale));
				}
			}
			finally
			{
				result.close();
			}
			final AdaptationTable target = pAssoc.getAssociationLink().getTargetTable(pRecord);
			if (target != null)
			{
				for (final SchemaNode f : target.getTableOccurrenceRootNode().getNodeChildren())
				{
					if (f.isTerminalValue() && !f.isAssociationNode() && !ModelExport.hidden(f))
					{
						final ObjectNode o = targetFields.addObject();
						o.put("p", LayoutPaths.stored(f.getPathInAdaptation()));
						o.set("lab", ModelExport.labels(f, pLocales));
					}
				}
			}
		}
		catch (final RuntimeException ex)
		{
			pOut.put("n", 0);
		}
	}

	/** The kind of a terminal field, as the designer offers widgets for it; null for a group. */
	static String kind(final SchemaNode pNode)
	{
		final SchemaTypeName type = pNode.getXsTypeName();
		if (type != null && type.getNameWithoutPrefix() != null && type.getNameWithoutPrefix().toLowerCase(Locale.ROOT).contains("media"))
		{
			return "media";
		}
		if (!pNode.isTerminalValue())
		{
			return null;
		}
		if (pNode.getMaxOccurs() > 1)
		{
			return "list";
		}
		if (pNode.getFacetOnTableReference() != null)
		{
			return "fk";
		}
		if (pNode.getFacetEnumeration() != null)
		{
			return "enum";
		}
		if (SchemaTypeName.XS_BOOLEAN.equals(type))
		{
			return "bool";
		}
		if (SchemaTypeName.XS_INT.equals(type) || SchemaTypeName.XS_INTEGER.equals(type))
		{
			return "int";
		}
		if (SchemaTypeName.XS_DECIMAL.equals(type))
		{
			return "dec";
		}
		if (SchemaTypeName.XS_DATE.equals(type))
		{
			return "date";
		}
		if (SchemaTypeName.XS_DATETIME.equals(type))
		{
			return "dt";
		}
		if (SchemaTypeName.OSD_TEXT.equals(type))
		{
			return "text";
		}
		// a field with its own restriction has no type name (local Javadoc): fall back on the Java class
		final Class<?> c = pNode.getOccurrencesJavaClass();
		if (c == Boolean.class)
		{
			return "bool";
		}
		if (c == Integer.class)
		{
			return "int";
		}
		if (c == BigDecimal.class)
		{
			return "dec";
		}
		if (c == Date.class)
		{
			return "date";
		}
		return "str";
	}

	static boolean hidden(final SchemaNode pNode)
	{
		try
		{
			return pNode.getDefaultViewProperties() != null && pNode.getDefaultViewProperties().isHiddenInAllViews();
		}
		catch (final RuntimeException ex)
		{
			return false;
		}
	}

	static ObjectNode labels(final SchemaNode pNode, final List<Locale> pLocales)
	{
		final ObjectNode o = ModelExport.JSON.createObjectNode();
		for (final Locale l : pLocales)
		{
			o.put(l.toLanguageTag(), pNode.getLabel(l));
		}
		return o;
	}

	private static Locale closest(final Locale pLocale, final List<Locale> pLocales)
	{
		if (pLocale != null)
		{
			for (final Locale l : pLocales)
			{
				if (l.equals(pLocale))
				{
					return l;
				}
			}
			for (final Locale l : pLocales)
			{
				if (l.getLanguage().equals(pLocale.getLanguage()))
				{
					return l;
				}
			}
		}
		return pLocales.isEmpty() ? Locale.US : pLocales.get(0);
	}
}
