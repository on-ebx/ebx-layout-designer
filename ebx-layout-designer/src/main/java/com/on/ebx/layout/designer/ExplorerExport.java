package com.on.ebx.layout.designer;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.on.ebx.layout.explorer.Cond;
import com.on.ebx.layout.explorer.ExplorerStore;
import com.on.ebx.layout.explorer.Linked;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.on.ebx.layout.runtime.Media;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.instance.ValueContext;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.workflow.PublishedProcessKey;
import com.orchestranetworks.workflow.WorkflowEngine;

/**
 * Everything the explorer designer page needs, as one JSON object ({@code window.LDX_BOOT}): the locales, the table's
 * fields, groups and associations (as the form designer reads them), the computed values it can offer (one count per
 * association, the Match and Merge suspects), the roles and the workflows, up to {@link #SAMPLE} records with every
 * value for the preview, and the table's explorers. EBX Java API only.
 */
final class ExplorerExport
{
	static final int SAMPLE = 200;
	private static final ObjectMapper JSON = LayoutStore.json();

	private ExplorerExport()
	{
	}

	static ObjectNode export(final Repository pRepository, final Session pSession, final AdaptationTable pTable, final int pCurrent, final String pDraft)
	{
		final Locale userLocale = pSession.getLocale();
		final List<Locale> locales = pRepository.getLocales();
		final ObjectNode boot = ExplorerExport.JSON.createObjectNode();
		final ArrayNode loc = boot.putArray("locales");
		for (final Locale l : locales)
		{
			loc.add(l.toLanguageTag());
		}
		boot.put("defaultLocale", pRepository.getDefaultLocale().toLanguageTag());
		boot.put("userLocale", userLocale.toLanguageTag());
		boot.put("helpUrl", AboutServiceDeclaration.guideUrl(userLocale));
		final Adaptation dataset = pTable.getContainerAdaptation();
		final ObjectNode model = boot.putObject("model");
		model.put("location", dataset.getSchemaLocation().format());
		model.put("table", pTable.getTablePath().format());
		model.put("dataspace", dataset.getHome().getKey().getName());
		model.put("dataset", dataset.getAdaptationName().getStringName());
		model.set("tableLabel", ModelExport.labels(pTable.getTableNode(), locales));

		final ArrayNode fields = boot.putArray("fields");
		final ArrayNode groups = boot.putArray("groups");
		final ArrayNode assocs = boot.putArray("assocs");
		ModelExport.walk(pTable.getTableOccurrenceRootNode(), null, locales, userLocale, null, null, fields, groups, assocs, ExplorerExport.JSON.createObjectNode(),
			dataset);
		// the Match and Merge add-on's technical association and linked fields are not offered (its suspects are)
		for (int i = assocs.size() - 1; i >= 0; i--)
		{
			if (assocs.get(i).path("p").asText().contains("Association_Reverse"))
			{
				assocs.remove(i);
			}
		}
		for (int i = fields.size() - 1; i >= 0; i--)
		{
			if (fields.get(i).path("p").asText().startsWith("/Linked_Field_"))
			{
				fields.remove(i);
			}
		}
		for (final JsonNode a : assocs)
		{
			// the preview rows and fields of an association are for forms; an explorer counts its rows
			((ObjectNode) a).remove("rows");
			((ObjectNode) a).remove("fields");
		}

		// the sample records, and the distinct values of each field among them
		final Set<String> keys = new LinkedHashSet<String>();
		final ArrayNode rows = ExplorerExport.JSON.createArrayNode();
		final SchemaNode root = pTable.getTableOccurrenceRootNode();
		final RequestResult result = pTable.createRequest().execute();
		int total;
		try
		{
			total = result.getSize();
			for (Adaptation r = result.nextAdaptation(); r != null && rows.size() < ExplorerExport.SAMPLE; r = result.nextAdaptation())
			{
				final ObjectNode o = rows.addObject();
				final String key = r.getOccurrencePrimaryKey().format();
				keys.add(key);
				o.put("key", key);
				o.put("label", r.getLabel(userLocale));
				final ObjectNode v = o.putObject("v");
				final ValueContext vc = r.createValueContext();
				for (final JsonNode f : fields)
				{
					final String path = f.path("p").asText();
					final SchemaNode n = root.getNode(LayoutPaths.relative(path));
					if (n == null)
					{
						continue;
					}
					Object raw;
					try
					{
						raw = r.get(n.getPathInAdaptation());
					}
					catch (final RuntimeException ex)
					{
						raw = null;
					}
					if (raw == null || raw instanceof String && ((String) raw).isEmpty())
					{
						continue;
					}
					if ("media".equals(f.path("t").asText()))
					{
						final String[] asset = Media.urlAndName(r, raw, userLocale);
						if (asset != null)
						{
							final ObjectNode img = v.putObject(path);
							img.put("url", asset[0]);
							img.put("name", asset[1]);
							if (!o.has("img"))
							{
								o.set("img", img);
							}
						}
						continue;
					}
					final ObjectNode x = v.putObject(path);
					if (raw instanceof Collection)
					{
						final StringBuilder l = new StringBuilder();
						final ArrayNode ks = x.putArray("ks");
						for (final Object e : (Collection<?>) raw)
						{
							ks.add(Cond.key(e));
							l.append(l.length() == 0 ? "" : ", ").append(ExplorerExport.display(n, e, vc, userLocale));
						}
						x.put("k", ks.size() == 0 ? "" : ks.get(0).asText());
						x.put("l", l.toString());
						continue;
					}
					x.put("k", Cond.key(raw));
					x.put("l", ExplorerExport.display(n, raw, vc, userLocale));
					if (raw instanceof Number)
					{
						x.put("n", ((Number) raw).doubleValue());
					}
					if (raw instanceof Date)
					{
						x.put("d", new SimpleDateFormat("yyyy-MM-dd").format((Date) raw));
					}
				}
			}
		}
		finally
		{
			result.close();
		}

		// computed values offered: one count per association that a foreign key makes, the suspects when Match and Merge runs
		final ArrayNode suggestions = boot.putArray("derivedSuggestions");
		for (final JsonNode a : assocs)
		{
			final ObjectNode def = ExplorerExport.JSON.createObjectNode();
			def.put("type", "count");
			def.put("association", a.path("p").asText());
			final Map<String, Integer> counts = Linked.counts(dataset, pTable, def, keys);
			if (counts == null)
			{
				continue;
			}
			final String name = a.path("p").asText().replaceAll("^.*/", "");
			final ObjectNode s = suggestions.addObject();
			s.put("name", name);
			s.set("lab", a.get("lab"));
			s.set("def", def);
			for (final JsonNode row : rows)
			{
				((ObjectNode) row).with("d").put(name, counts.getOrDefault(row.path("key").asText(), Integer.valueOf(0)).intValue());
			}
		}
		if (Linked.hasSuspects(dataset, pTable))
		{
			final ObjectNode s = suggestions.addObject();
			s.put("name", "suspects");
			final ObjectNode lab = s.putObject("lab");
			for (final Locale l : locales)
			{
				lab.put(l.toLanguageTag(), Messages.get(l, "explorer.suspects"));
			}
			s.putObject("def").put("type", "suspects");
			final Map<String, List<String>> pairs = Linked.suspects(dataset, pTable, keys);
			for (final JsonNode row : rows)
			{
				final ArrayNode list = ((ObjectNode) row).with("d").putArray("suspects");
				for (final String k : pairs.getOrDefault(row.path("key").asText(), java.util.Collections.<String> emptyList()))
				{
					list.add(k);
				}
			}
		}
		final ObjectNode sample = boot.putObject("sample");
		sample.put("total", total);
		sample.set("rows", rows);

		final ObjectNode directory = DirectoryRoles.export(pRepository, pSession, userLocale);
		boot.set("roles", directory.path("roles"));
		final ArrayNode workflows = boot.putArray("workflows");
		try
		{
			final WorkflowEngine engine = WorkflowEngine.getFromRepository(pRepository, pSession);
			for (final PublishedProcessKey k : engine.getPublishedKeys())
			{
				final ObjectNode w = workflows.addObject();
				w.put("pub", k.getName());
				final ObjectNode lab = w.putObject("lab");
				try
				{
					final com.onwbp.base.text.UserMessage label = engine.getPublishedProcess(k).getLabel();
					for (final Locale l : locales)
					{
						lab.put(l.toLanguageTag(), label == null ? k.getName() : label.formatMessage(l));
					}
				}
				catch (final RuntimeException ex)
				{
					lab.put(pRepository.getDefaultLocale().toLanguageTag(), k.getName());
				}
			}
		}
		catch (final RuntimeException ex)
		{
			// no workflow engine for this session
		}

		final ArrayNode explorers = boot.putArray("explorers");
		final ObjectNode current = boot.putObject("current");
		current.putNull("id");
		for (final ExplorerStore.Row r : ExplorerStore.list(pRepository, dataset.getSchemaLocation(), pTable.getTablePath()))
		{
			final ObjectNode e = explorers.addObject();
			e.put("id", r.id);
			e.put("label", r.label);
			e.put("active", r.active);
			if (r.id == pCurrent || pCurrent < 0 && !current.hasNonNull("id"))
			{
				current.put("id", r.id);
				current.put("label", r.label);
				current.put("active", r.active);
				current.put("version", r.version);
				if (r.json != null)
				{
					current.set("layout", r.json);
				}
			}
		}
		if (pDraft != null && !pDraft.isEmpty())
		{
			try
			{
				current.set("layout", ExplorerExport.JSON.readTree(pDraft));
				current.put("draft", true);
			}
			catch (final Exception ex)
			{
				// a draft that does not parse is dropped
			}
		}
		return boot;
	}

	private static String display(final SchemaNode pNode, final Object pValue, final ValueContext pContext, final Locale pLocale)
	{
		try
		{
			final String s = pNode.displayOccurrence(pValue, true, pContext, pLocale);
			return s == null ? Cond.key(pValue) : s;
		}
		catch (final RuntimeException ex)
		{
			return Cond.key(pValue);
		}
	}
}
