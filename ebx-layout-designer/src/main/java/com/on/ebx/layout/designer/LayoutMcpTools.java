package com.on.ebx.layout.designer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.on.ebx.layout.explorer.ExplorerCheck;
import com.on.ebx.layout.explorer.ExplorerStore;
import com.on.ebx.layout.runtime.LayoutCheck;
import com.on.ebx.layout.runtime.LayoutComponent;
import com.on.ebx.layout.runtime.LayoutComponents;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationHome;
import com.onwbp.adaptation.AdaptationName;
import com.onwbp.adaptation.AdaptationTable;
import com.orchestranetworks.instance.HomeKey;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.workflow.PublishedProcessKey;
import com.orchestranetworks.workflow.WorkflowEngine;

/**
 * The tools of the Layout Designer's MCP endpoint (1.21.0): read a table as the designer sees it, start, check and save a
 * record form layout or an explorer. Every write goes through the same code as the designer pages (LayoutWriter,
 * ExplorerStore) and is refused when the check finds an error; reads and writes run under the caller's session, which must
 * be an administrator's, as for the designer pages.
 */
final class LayoutMcpTools
{
	static final String SERVER_NAME = "ebx-layout-designer";
	static final String SERVER_DESCRIPTION = "ON EBX Layout Designer: read a table as the designer sees it (fields, groups, associations, locales, "
		+ "registered components, published workflows), then start, check and save its record form layout or its explorers (faceted search pages). "
		+ "A layout or explorer is saved only when its check finds no error. Administrators only, like the designer pages.";

	/** One input of a tool. */
	static final class Param
	{
		final String name;
		final String type;
		final String description;
		final boolean required;

		Param(final String pName, final String pType, final String pDescription, final boolean pRequired)
		{
			this.name = pName;
			this.type = pType;
			this.description = pDescription;
			this.required = pRequired;
		}
	}

	/** One tool. */
	static final class Tool
	{
		final String name;
		final String title;
		final String description;
		final boolean readOnly;
		final List<Param> params;

		Tool(final String pName, final String pTitle, final boolean pReadOnly, final String pDescription, final Param... pParams)
		{
			this.name = pName;
			this.title = pTitle;
			this.readOnly = pReadOnly;
			this.description = pDescription;
			this.params = Arrays.asList(pParams);
		}
	}

	private static final Param DATASPACE = new Param("dataspace", "string", "Dataspace key, without the B prefix (Companies).", true);
	private static final Param DATASET = new Param("dataset", "string", "Dataset name (Companies).", true);
	private static final Param TABLE = new Param("table", "string", "Table path (/root/Companies).", true);

	static final List<Tool> CATALOG = Arrays.asList(
		new Tool("layout_help", "Guide", true,
			"How to use this server, and the full JSON formats. Call it first. topic: omit for the workflow, 'forms' for the record form format "
				+ "(ebx-form-layout/1), 'explorers' for the explorer format.",
			new Param("topic", "string", "'forms' or 'explorers'; omit for the overview.", false)),
		new Tool("layout_describe_table", "Describe a table", true,
			"Everything a layout or explorer may refer to: the table's fields (path, label, type, group, enumeration values, foreign key target), "
				+ "groups, associations (with their table and fields), the dataset's other tables, the instance locales, registered components, "
				+ "published workflows; technical elements (Match and Merge's Association_Reverse, Linked_Field_*) are flagged. Also the stored "
				+ "form layout (version) and the explorers of the table, and whether the model declares the designed form.",
			DATASPACE, DATASET, TABLE),
		new Tool("layout_get", "Read the record form layout", true,
			"The stored record form layout of the table (JSON, version, active, who saved it and when), or found: false.", DATASPACE, DATASET, TABLE),
		new Tool("layout_starter", "Starting record form layout", true,
			"The layout the designer starts from when nobody has drawn one: a General tab with one panel per group, one tab per association; "
				+ "technical associations left out. A point of departure to edit, then check and save.",
			DATASPACE, DATASET, TABLE),
		new Tool("layout_validate", "Check a record form layout", true,
			"Checks a record form layout against the table without saving: JSON shape, block types and ids, every field, association, table, "
				+ "workflow and component it names. Answers valid, errors[] and warnings[] ({where, text}).",
			DATASPACE, DATASET, TABLE, new Param("layout", "object", "The layout (ebx-form-layout/1), as an object or a JSON string.", true)),
		new Tool("layout_put", "Save a record form layout", false,
			"Checks then saves the table's record form layout (creates the row or replaces its layout, version + 1). Refused when the check "
				+ "finds an error, or when expected_version is given and differs from the stored version (someone saved in between). The form "
				+ "shows at the next opening of a record once the model declares com.on.ebx.layout.runtime.ConfigurableForm (see activation).",
			DATASPACE, DATASET, TABLE, new Param("layout", "object", "The layout (ebx-form-layout/1), as an object or a JSON string.", true),
			new Param("expected_version", "integer", "The version read before editing; 0 when creating. Omit to overwrite whatever is stored.", false),
			new Param("dry_run", "boolean", "true: check only, write nothing.", false)),
		new Tool("explorer_list", "List explorers", true,
			"The explorers of the instance (id, label, model, table, active, version), or of one table when dataspace, dataset and table are given.",
			new Param("dataspace", "string", "Optional: with dataset and table, only the explorers of that table.", false),
			new Param("dataset", "string", "Optional.", false), new Param("table", "string", "Optional.", false)),
		new Tool("explorer_get", "Read an explorer", true, "One explorer: its JSON, label, table, active flag and version.",
			new Param("id", "integer", "The explorer id (explorer_list).", true)),
		new Tool("explorer_validate", "Check an explorer", true,
			"Checks an explorer's JSON against the table without saving: every path, computed value, facet, flag, tile, sort, group, card "
				+ "part and action, and whether EBX can evaluate each filter (the page silently drops what it cannot). Answers valid, errors[], warnings[].",
			DATASPACE, DATASET, TABLE, new Param("explorer", "object", "The explorer (ebx-layout-designer/explorer), as an object or a JSON string.", true)),
		new Tool("explorer_put", "Save an explorer", false,
			"Checks then saves an explorer: creates it (no id) or replaces the JSON of explorer id (version + 1). Refused when the check finds an "
				+ "error, or when expected_version differs from the stored version. Answers the id and the address that opens it.",
			DATASPACE, DATASET, TABLE, new Param("explorer", "object", "The explorer (ebx-layout-designer/explorer), as an object or a JSON string.", true),
			new Param("id", "integer", "The explorer to replace; omit to create one.", false),
			new Param("label", "string", "Name shown in the designer (default: the JSON's title, else the table's label).", false),
			new Param("active", "boolean", "false turns the explorer off.", false),
			new Param("expected_version", "integer", "The version read before editing. Omit to overwrite.", false),
			new Param("dry_run", "boolean", "true: check only, write nothing.", false)));

	/**
	 * Block types of ebx-form-layout/1 (LAYOUT-FORMAT.md). An unknown one is a warning: a newer runtime may know it. The
	 * blocks of effective-dated models (asOfTree, stateTimeline, delta, 1.24.0-1.26.0) are components of the ROSA module
	 * since 1.27.0.
	 */
	static final Set<String> BLOCK_TYPES = new HashSet<String>(Arrays.asList("panel", "hero", "facts", "validation", "grid", "map", "tree", "hours",
		"duplicates", "note", "custom", "timeline", "history", "completeness", "stepper", "checklist", "chart", "aggregates", "pivot", "linked",
		"contacts", "gallery", "documents", "links", "validity", "address", "hierarchy", "recordInfo", "changes", "usedBy", "orgChart",
		"valueHistory", "sitesMap", "sourceValues", "workflowTasks", "translations", "inheritance", "actions", "heatmap", "gantt", "compare", "coverage"));

	/** 1.27.0: what a component says of its settings ({@link LayoutComponent#designerSpec()}), null when it says nothing or not JSON. */
	static JsonNode designerSpec(final LayoutComponent pComponent)
	{
		try
		{
			final String spec = pComponent.designerSpec();
			if (spec == null || spec.trim().isEmpty())
			{
				return null;
			}
			final JsonNode n = LayoutStore.json().readTree(spec);
			return n != null && n.isObject() ? n : null;
		}
		catch (final Exception | LinkageError ex)
		{
			return null;
		}
	}

	/** Match and Merge's technical nodes: a layout should leave them out. */
	private static final Pattern TECHNICAL = Pattern.compile("^/(Association_Reverse|Linked_Field_[A-Za-z]+)$");

	private static final ObjectMapper JSON = LayoutStore.json();

	private final Session session;
	private final Repository repository;
	private final Locale locale;

	/** Scheme and authority the caller reached the server with ({@code http://host:8080}); empty when unknown (1.27.0). */
	private String serverBase = "";

	/** MCP Apps (1.27.0): the answers also carry absolute links, for a page drawn outside EBX. */
	void setServerBase(final String pBase)
	{
		this.serverBase = pBase == null ? "" : pBase.trim();
	}

	LayoutMcpTools(final Session pSession, final Repository pRepository, final Locale pLocale)
	{
		this.session = pSession;
		this.repository = pRepository;
		this.locale = pLocale;
	}

	static Tool tool(final String pName)
	{
		for (final Tool t : LayoutMcpTools.CATALOG)
		{
			if (t.name.equals(pName))
			{
				return t;
			}
		}
		return null;
	}

	/** The designer pages are for administrators; so is this server. */
	static boolean allowed(final Session pSession)
	{
		return pSession != null && pSession.isUserInRole(Profile.ADMINISTRATOR);
	}

	/** Runs a tool; the answer carries {@code error} (and {@code code}) when the call failed. */
	ObjectNode invoke(final String pName, final JsonNode pArguments)
	{
		final JsonNode a = pArguments == null || pArguments.isNull() ? LayoutMcpTools.JSON.createObjectNode() : pArguments;
		if (!LayoutMcpTools.allowed(this.session))
		{
			return LayoutMcpTools.fail("FORBIDDEN", "The Layout Designer is for administrators.");
		}
		try
		{
			switch (pName)
			{
			case "layout_help":
				return this.help(a.path("topic").asText(""));
			case "layout_describe_table":
				return this.describe(this.target(a));
			case "layout_get":
				return this.layoutGet(this.target(a));
			case "layout_starter":
				return this.starter(this.target(a));
			case "layout_validate":
				return this.layoutValidate(this.target(a), LayoutMcpTools.document(a, "layout"));
			case "layout_put":
				return this.layoutPut(this.target(a), LayoutMcpTools.document(a, "layout"), LayoutMcpTools.optInt(a, "expected_version"),
					a.path("dry_run").asBoolean(false));
			case "explorer_list":
				return this.explorerList(a);
			case "explorer_get":
				return this.explorerGet(a.path("id").asInt(-1));
			case "explorer_validate":
				return this.explorerValidate(this.target(a), LayoutMcpTools.document(a, "explorer"));
			case "explorer_put":
				return this.explorerPut(this.target(a), LayoutMcpTools.document(a, "explorer"), LayoutMcpTools.optInt(a, "id"), a.path("label").asText(""),
					a.has("active") && !a.get("active").isNull() ? Boolean.valueOf(a.get("active").asBoolean()) : null, LayoutMcpTools.optInt(a, "expected_version"),
					a.path("dry_run").asBoolean(false));
			default:
				return LayoutMcpTools.fail("UNKNOWN_TOOL", "Unknown tool: " + pName);
			}
		}
		catch (final Refusal r)
		{
			return LayoutMcpTools.fail(r.code, r.getMessage());
		}
		catch (final Exception ex)
		{
			com.orchestranetworks.service.LoggingCategory.getKernel().error("[layout-designer] mcp " + pName + " failed: " + ex, ex);
			return LayoutMcpTools.fail("FAILED", "The tool \"" + pName + "\" failed on the server. Check the arguments and retry; if it fails again ask an administrator to read kernel.log (lines starting \"[layout-designer] mcp " + pName + " failed\").");
		}
	}

	// ----- tools ------------------------------------------------------------------------------------------------------

	private ObjectNode help(final String pTopic)
	{
		final ObjectNode out = LayoutMcpTools.JSON.createObjectNode();
		if ("forms".equals(pTopic) || "explorers".equals(pTopic))
		{
			final String file = "forms".equals(pTopic) ? "LAYOUT-FORMAT.md" : "EXPLORER-FORMAT.md";
			out.put("topic", pTopic);
			out.put("markdown", LayoutMcpTools.resource("mcp/" + file));
			return out;
		}
		out.put("version", ModuleVersion.version());
		out.put("markdown", LayoutMcpTools.resource("mcp/MCP-GUIDE.md"));
		return out;
	}

	private ObjectNode describe(final Target pTarget)
	{
		final List<Locale> locales = this.repository.getLocales();
		final ObjectNode out = this.head(pTarget);
		final ArrayNode loc = out.putArray("locales");
		for (final Locale l : locales)
		{
			loc.add(l.toLanguageTag());
		}
		out.put("defaultLocale", this.repository.getDefaultLocale().toLanguageTag());
		final ArrayNode fields = LayoutMcpTools.JSON.createArrayNode();
		final ArrayNode groups = LayoutMcpTools.JSON.createArrayNode();
		final ArrayNode assocs = LayoutMcpTools.JSON.createArrayNode();
		ModelExport.walk(pTarget.table.getTableOccurrenceRootNode(), null, locales, this.locale, null, null, fields, groups, assocs,
			LayoutMcpTools.JSON.createObjectNode(), pTarget.dataset);
		final ArrayNode outFields = out.putArray("fields");
		for (final JsonNode f : fields)
		{
			final ObjectNode o = outFields.addObject();
			o.put("path", f.path("p").asText());
			o.put("label", this.pick(f.get("lab")));
			o.put("type", f.path("t").asText());
			LayoutMcpTools.copy(f, o, "g", "group");
			LayoutMcpTools.copy(f, o, "req", "required");
			LayoutMcpTools.copy(f, o, "fn", "computed");
			LayoutMcpTools.copy(f, o, "target", "target");
			if (f.has("vals"))
			{
				final ArrayNode vals = o.putArray("values");
				for (final JsonNode v : f.get("vals"))
				{
					vals.addObject().put("code", v.path("v").asText()).put("label", this.pick(v.get("lab")));
				}
			}
			this.technical(o);
		}
		final ArrayNode outGroups = out.putArray("groups");
		for (final JsonNode g : groups)
		{
			outGroups.addObject().put("path", g.path("p").asText()).put("label", this.pick(g.get("lab")));
		}
		final ArrayNode outAssocs = out.putArray("associations");
		for (final JsonNode a : assocs)
		{
			final ObjectNode o = outAssocs.addObject();
			o.put("path", a.path("p").asText());
			o.put("label", this.pick(a.get("lab")));
			final SchemaNode n = pTarget.table.getTableOccurrenceRootNode().getNode(LayoutPaths.relative(a.path("p").asText()));
			try
			{
				final AdaptationTable t = n.getAssociationLink().getTargetTable(pTarget.dataset);
				o.put("table", t.getTablePath().format());
				if (t.getContainerAdaptation() != pTarget.dataset)
				{
					o.put("dataset", t.getContainerAdaptation().getAdaptationName().getStringName());
				}
			}
			catch (final RuntimeException ex)
			{
				// target not reachable from here
			}
			final ArrayNode tf = o.putArray("fields");
			for (final JsonNode f : a.path("fields"))
			{
				tf.add(f.path("p").asText());
			}
			this.technical(o);
		}
		final ArrayNode tables = out.putArray("datasetTables");
		LayoutMcpTools.tables(pTarget.dataset.getSchemaNode(), tables, this.locale);
		final ArrayNode components = out.putArray("components");
		for (final java.util.Map.Entry<String, LayoutComponent> e : LayoutComponents.all().entrySet())
		{
			final ObjectNode c = components.addObject().put("name", e.getKey()).put("label", e.getValue().getLabel(this.locale));
			final JsonNode spec = LayoutMcpTools.designerSpec(e.getValue());
			if (spec != null)
			{
				c.set("settings", spec);
			}
		}
		final Set<String> published = this.published();
		if (published != null)
		{
			final ArrayNode wf = out.putArray("workflows");
			for (final String p : published)
			{
				wf.add(p);
			}
		}
		final ObjectNode form = out.putObject("formLayout");
		final Adaptation row = LayoutStore.findRow(this.repository, pTarget.model(), pTarget.table.getTablePath());
		form.put("exists", row != null);
		if (row != null)
		{
			form.put("version", LayoutMcpTools.version(row));
			form.put("active", Boolean.TRUE.equals(row.get(LayoutPaths.ACTIVE)));
		}
		out.set("activation", this.activation(pTarget));
		final ArrayNode explorers = out.putArray("explorers");
		for (final ExplorerStore.Row r : ExplorerStore.list(this.repository, pTarget.model(), pTarget.table.getTablePath()))
		{
			explorers.addObject().put("id", r.id).put("label", r.label).put("active", r.active).put("version", r.version);
		}
		return out;
	}

	private ObjectNode layoutGet(final Target pTarget)
	{
		final ObjectNode out = this.head(pTarget);
		final Adaptation row = LayoutStore.findRow(this.repository, pTarget.model(), pTarget.table.getTablePath());
		out.put("found", row != null);
		if (row != null)
		{
			out.put("version", LayoutMcpTools.version(row));
			out.put("active", Boolean.TRUE.equals(row.get(LayoutPaths.ACTIVE)));
			out.put("label", row.getString(LayoutPaths.LABEL));
			out.put("updatedBy", row.getString(LayoutPaths.UPDATED_BY));
			out.put("updatedOn", LayoutMcpTools.date(row.get(LayoutPaths.UPDATED_ON)));
			out.set("layout", LayoutMcpTools.parse(row.getString(LayoutPaths.LAYOUT)));
		}
		out.set("activation", this.activation(pTarget));
		out.put("designerUrl", this.url(pTarget, LayoutPaths.MODULE_NAME + "@LayoutDesigner", null));
		return out;
	}

	private ObjectNode starter(final Target pTarget)
	{
		final List<Locale> locales = this.repository.getLocales();
		final ArrayNode fields = LayoutMcpTools.JSON.createArrayNode();
		final ArrayNode groups = LayoutMcpTools.JSON.createArrayNode();
		final ArrayNode assocs = LayoutMcpTools.JSON.createArrayNode();
		ModelExport.walk(pTarget.table.getTableOccurrenceRootNode(), null, locales, this.locale, null, null, fields, groups, assocs,
			LayoutMcpTools.JSON.createObjectNode(), pTarget.dataset);
		LayoutMcpTools.removeTechnical(fields);
		LayoutMcpTools.removeTechnical(assocs);
		final ObjectNode layout = (ObjectNode) DefaultLayout.build(fields, groups, assocs, locales);
		layout.put("model", pTarget.model().format());
		layout.put("table", pTarget.table.getTablePath().format());
		final ObjectNode out = this.head(pTarget);
		out.set("layout", layout);
		out.put("note", "Technical associations and fields (Association_Reverse, Linked_Field_*) are left out. Check with layout_validate, save with layout_put.");
		return out;
	}

	private ObjectNode layoutValidate(final Target pTarget, final JsonNode pLayout)
	{
		final ObjectNode out = this.head(pTarget);
		LayoutMcpTools.report(out, this.checkLayout(pTarget, pLayout));
		return out;
	}

	private ObjectNode layoutPut(final Target pTarget, final JsonNode pLayout, final Integer pExpected, final boolean pDryRun) throws Exception
	{
		final ObjectNode out = this.head(pTarget);
		final List<Finding> findings = this.checkLayout(pTarget, pLayout);
		LayoutMcpTools.report(out, findings);
		final Adaptation row = LayoutStore.findRow(this.repository, pTarget.model(), pTarget.table.getTablePath());
		final int stored = row == null ? 0 : LayoutMcpTools.version(row);
		out.put("storedVersion", stored);
		if (LayoutMcpTools.hasError(findings))
		{
			return LayoutMcpTools.refused(out, "INVALID", "Not saved: the layout has errors (errors[]).");
		}
		if (pExpected != null && pExpected.intValue() != stored)
		{
			return LayoutMcpTools.refused(out, "VERSION_CONFLICT",
				"Not saved: version " + stored + " is stored, not " + pExpected + ". Read it again (layout_get) and merge.");
		}
		if (pDryRun)
		{
			out.put("saved", false);
			out.put("dryRun", true);
			return out;
		}
		final String label = pTarget.table.getTableNode().getLabel(Locale.ENGLISH);
		final int version = LayoutWriter.save(this.repository, this.session, pTarget.model(), pTarget.table.getTablePath(), label,
			LayoutMcpTools.JSON.writeValueAsString(pLayout), null);
		out.put("saved", true);
		out.put("version", version);
		out.set("activation", this.activation(pTarget));
		out.put("designerUrl", this.url(pTarget, LayoutPaths.MODULE_NAME + "@LayoutDesigner", null));
		return out;
	}

	private ObjectNode explorerList(final JsonNode pArgs) throws Refusal
	{
		final ObjectNode out = LayoutMcpTools.JSON.createObjectNode();
		final ArrayNode list = out.putArray("explorers");
		if (pArgs.hasNonNull("table"))
		{
			final Target t = this.target(pArgs);
			for (final ExplorerStore.Row r : ExplorerStore.list(this.repository, t.model(), t.table.getTablePath()))
			{
				this.row(list.addObject(), r, t);
			}
			return out;
		}
		for (final ExplorerStore.Row r : ExplorerStore.list(this.repository, null, null))
		{
			this.row(list.addObject(), r, null);
		}
		return out;
	}

	private ObjectNode explorerGet(final int pId) throws Refusal
	{
		final ExplorerStore.Row r = pId < 0 ? null : ExplorerStore.find(this.repository, pId);
		if (r == null)
		{
			throw new Refusal("NOT_FOUND", "No explorer " + pId + " (explorer_list).");
		}
		final ObjectNode out = LayoutMcpTools.JSON.createObjectNode();
		this.row(out, r, null);
		out.set("explorer", r.json);
		return out;
	}

	private ObjectNode explorerValidate(final Target pTarget, final JsonNode pExplorer)
	{
		final ObjectNode out = this.head(pTarget);
		LayoutMcpTools.report(out, this.checkExplorer(pTarget, pExplorer));
		return out;
	}

	private ObjectNode explorerPut(final Target pTarget, final JsonNode pExplorer, final Integer pId, final String pLabel, final Boolean pActive,
		final Integer pExpected, final boolean pDryRun) throws Exception
	{
		final ObjectNode out = this.head(pTarget);
		final List<Finding> findings = this.checkExplorer(pTarget, pExplorer);
		LayoutMcpTools.report(out, findings);
		ExplorerStore.Row existing = null;
		if (pId != null)
		{
			existing = ExplorerStore.find(this.repository, pId.intValue());
			if (existing == null)
			{
				throw new Refusal("NOT_FOUND", "No explorer " + pId + "; omit id to create one.");
			}
			if (!pTarget.model().format().equals(existing.model) || !pTarget.table.getTablePath().format().equals(existing.table))
			{
				throw new Refusal("WRONG_TABLE", "Explorer " + pId + " belongs to " + existing.model + " " + existing.table + ", not to this table.");
			}
			out.put("storedVersion", existing.version);
		}
		if (LayoutMcpTools.hasError(findings))
		{
			return LayoutMcpTools.refused(out, "INVALID", "Not saved: the explorer has errors (errors[]).");
		}
		if (pExpected != null && (existing == null ? 0 : existing.version) != pExpected.intValue())
		{
			return LayoutMcpTools.refused(out, "VERSION_CONFLICT", "Not saved: version " + (existing == null ? 0 : existing.version) + " is stored, not "
				+ pExpected + ". Read it again (explorer_get) and merge.");
		}
		if (pDryRun)
		{
			out.put("saved", false);
			out.put("dryRun", true);
			return out;
		}
		String label = pLabel;
		if (label.isEmpty())
		{
			label = existing != null ? existing.label : this.pick(pExplorer.get("title"));
		}
		if (label == null || label.isEmpty())
		{
			label = pTarget.table.getTableNode().getLabel(this.locale);
		}
		final int id = ExplorerStore.save(this.repository, this.session, existing == null ? -1 : existing.id, pTarget.model(), pTarget.table.getTablePath(),
			label, LayoutMcpTools.JSON.writeValueAsString(pExplorer));
		if (pActive != null)
		{
			ExplorerStore.setActive(this.repository, this.session, id, pActive.booleanValue());
		}
		final ExplorerStore.Row saved = ExplorerStore.find(this.repository, id);
		out.put("saved", true);
		this.row(out, saved, pTarget);
		return out;
	}

	// ----- checks -----------------------------------------------------------------------------------------------------

	/** A finding of a check. */
	static final class Finding
	{
		final boolean error;
		final String where;
		final String text;

		Finding(final boolean pError, final String pWhere, final String pText)
		{
			this.error = pError;
			this.where = pWhere;
			this.text = pText;
		}
	}

	List<Finding> checkLayout(final Target pTarget, final JsonNode pLayout)
	{
		final List<Finding> out = LayoutMcpTools.checkShape(pLayout);
		if (pLayout != null && pLayout.isObject())
		{
			out.addAll(this.checkReferences(pTarget, pLayout));
		}
		return out;
	}

	/** What needs no table: JSON shape, size, tabs, block ids and types. Shared by the MCP tools and the designer page (L2-13). */
	static List<Finding> checkShape(final JsonNode pLayout)
	{
		final List<Finding> out = new ArrayList<Finding>();
		if (pLayout == null || !pLayout.isObject())
		{
			out.add(new Finding(true, "", "The layout must be a JSON object."));
			return out;
		}
		if (!"ebx-form-layout/1".equals(pLayout.path("format").asText("")))
		{
			out.add(new Finding(false, "format", "\"format\" should be \"ebx-form-layout/1\"."));
		}
		final int size = pLayout.toString().length();
		if (size > LayoutMcpEndpoint.maxBodyBytes())
		{
			out.add(new Finding(true, "", "The layout is " + size + " characters, more than the " + LayoutMcpEndpoint.maxBodyBytes() + " allowed (-D"
				+ LayoutMcpEndpoint.PROP_MAX_BODY + "): split it or remove what is unused."));
		}
		if (!pLayout.path("tabs").isArray() || pLayout.path("tabs").size() == 0)
		{
			out.add(new Finding(true, "tabs", "A layout needs \"tabs\": [{\"label\", \"rows\": [{\"columns\", \"blocks\": [[…]]}]}]."));
		}
		final java.util.Map<String, String> ids = new java.util.HashMap<String, String>();
		LayoutMcpTools.blocks(pLayout.path("tabs"), ids, out);
		LayoutMcpTools.blocks(pLayout.path("variants"), ids, out);
		return out;
	}

	/** Every field, association, table, workflow and component the layout names, against the table. */
	List<Finding> checkReferences(final Target pTarget, final JsonNode pLayout)
	{
		final List<Finding> out = new ArrayList<Finding>();
		for (final LayoutCheck.Issue i : LayoutCheck.check(this.repository, pTarget.model().format(), pTarget.table.getTablePath().format(), pLayout, this.locale))
		{
			out.add(new Finding(i.error, i.where, i.text(this.locale).replaceFirst("^" + Pattern.quote(i.where) + " : ", "")));
		}
		return out;
	}

	/**
	 * Every block of the layout: an id and a known type. Variants repeat a block by its id, with the same type; the same id on
	 * two block types is reported.
	 */
	private static void blocks(final JsonNode pNode, final java.util.Map<String, String> pIds, final List<Finding> pOut)
	{
		if (pNode.isArray())
		{
			for (final JsonNode x : pNode)
			{
				LayoutMcpTools.blocks(x, pIds, pOut);
			}
			return;
		}
		if (!pNode.isObject())
		{
			return;
		}
		if (pNode.path("type").isTextual() && !pNode.has("rows"))
		{
			final String type = pNode.get("type").asText();
			final String id = pNode.path("id").asText("");
			final String where = "block " + (id.isEmpty() ? type : id);
			if (id.isEmpty())
			{
				pOut.add(new Finding(true, where, "A block needs an \"id\", unique in the layout."));
			}
			else
			{
				final String before = pIds.putIfAbsent(id, type);
				if (before != null && !before.equals(type))
				{
					pOut.add(new Finding(true, where, "Id \"" + id + "\" is used by a " + before + " block and a " + type + " block."));
				}
			}
			if (!LayoutMcpTools.BLOCK_TYPES.contains(type))
			{
				pOut.add(new Finding(false, where, "asOfTree".equals(type) || "stateTimeline".equals(type) || "delta".equals(type)
					? "Block type \"" + type + "\" left the Layout Designer in 1.27.0: it is the component {\"type\": \"custom\", \"cls\": \"rosa." + type + "\"} of the ROSA module (same settings)."
					: "Unknown block type \"" + type + "\" for this runtime: it would not be drawn."));
			}
			if ("panel".equals(type))
			{
				for (final JsonNode f : pNode.path("fields"))
				{
					if (!f.path("p").isTextual())
					{
						pOut.add(new Finding(true, where, "A field of a panel needs \"p\": its path (/Address/City)."));
					}
				}
			}
			return;
		}
		pNode.fields().forEachRemaining(e -> LayoutMcpTools.blocks(e.getValue(), pIds, pOut));
	}

	List<Finding> checkExplorer(final Target pTarget, final JsonNode pExplorer)
	{
		final List<Finding> out = new ArrayList<Finding>();
		if (pExplorer != null && pExplorer.toString().length() > LayoutMcpEndpoint.maxBodyBytes())
		{
			out.add(new Finding(true, "", "The explorer is larger than the " + LayoutMcpEndpoint.maxBodyBytes() + " characters allowed (-D"
				+ LayoutMcpEndpoint.PROP_MAX_BODY + "): remove what is unused."));
		}
		for (final ExplorerCheck.Issue i : ExplorerCheck.check(pTarget.dataset, pTarget.table, pExplorer, this.locale, this.published()))
		{
			out.add(new Finding(i.error, i.where, i.text));
		}
		return out;
	}

	// ----- helpers ----------------------------------------------------------------------------------------------------

	/** A dataset and one of its tables, resolved from the call's arguments. */
	static final class Target
	{
		final Adaptation dataset;
		final AdaptationTable table;

		Target(final Adaptation pDataset, final AdaptationTable pTable)
		{
			this.dataset = pDataset;
			this.table = pTable;
		}

		SchemaLocation model()
		{
			return this.dataset.getSchemaLocation();
		}
	}

	/** A call refused for a reason the caller can act on. */
	static final class Refusal extends Exception
	{
		private static final long serialVersionUID = 1L;
		final String code;

		Refusal(final String pCode, final String pMessage)
		{
			super(pMessage);
			this.code = pCode;
		}
	}

	private Target target(final JsonNode pArgs) throws Refusal
	{
		final String space = pArgs.path("dataspace").asText("").trim().replaceFirst("^B(?=[A-Z])", "");
		final String name = pArgs.path("dataset").asText("").trim();
		final String tablePath = pArgs.path("table").asText("").trim();
		if (space.isEmpty() || name.isEmpty() || tablePath.isEmpty())
		{
			throw new Refusal("MISSING_ARGUMENT", "dataspace, dataset and table are required.");
		}
		final AdaptationHome home = this.repository.lookupHome(HomeKey.forBranchName(space));
		if (home == null || !home.isOpenBranch())
		{
			throw new Refusal("NOT_FOUND", "No open dataspace \"" + space + "\".");
		}
		final Adaptation dataset = home.findAdaptationOrNull(AdaptationName.forName(name));
		if (dataset == null)
		{
			throw new Refusal("NOT_FOUND", "No dataset \"" + name + "\" in dataspace " + space + ".");
		}
		AdaptationTable table = null;
		try
		{
			table = dataset.getTable(Path.parse(tablePath));
		}
		catch (final RuntimeException ex)
		{
			table = null;
		}
		if (table == null)
		{
			throw new Refusal("NOT_FOUND", "No table \"" + tablePath + "\" in dataset " + name + ".");
		}
		if (this.session.getPermissions().getNodeAccessPermission(table.getTableNode(), dataset).isHidden())
		{
			throw new Refusal("FORBIDDEN", "You may not read table " + tablePath + ".");
		}
		return new Target(dataset, table);
	}

	private ObjectNode head(final Target pTarget)
	{
		final ObjectNode out = LayoutMcpTools.JSON.createObjectNode();
		out.put("dataspace", pTarget.dataset.getHome().getKey().getName());
		out.put("dataset", pTarget.dataset.getAdaptationName().getStringName());
		out.put("table", pTarget.table.getTablePath().format());
		out.put("tableLabel", pTarget.table.getTableNode().getLabel(this.locale));
		out.put("model", pTarget.model().format());
		// 1.27.0: the designer of the table as an absolute link (the form preview opens it from outside EBX)
		if (!this.serverBase.isEmpty())
		{
			out.put("designerLink", this.serverBase + this.url(pTarget, LayoutPaths.MODULE_NAME + "@LayoutDesigner", null));
		}
		return out;
	}

	/** Whether the model declares the designed form (module XSD: read from the file; DMA: what to do). */
	private ObjectNode activation(final Target pTarget)
	{
		final ObjectNode o = LayoutMcpTools.JSON.createObjectNode();
		o.put("formClass", LayoutPaths.FORM_CLASS);
		final XsdActivator.State s = XsdActivator.state(pTarget.model(), pTarget.table.getTablePath());
		if ("dma".equals(s.reason))
		{
			o.put("modelKind", "dma");
			o.put("howTo", "A DMA model: declare the class with the data-model MCP server, execute_operation(set_form_layout, {element_path: \""
				+ pTarget.table.getTablePath().format() + "\", class_name: \"" + LayoutPaths.FORM_CLASS + "\"}), then publish_data_model. No restart.");
			return o;
		}
		o.put("modelKind", "module");
		o.put("declared", s.declared);
		if (s.currentForm != null)
		{
			o.put("currentForm", s.currentForm);
		}
		final boolean designerWrites = XsdActivator.writeEnabled() && s.file != null;
		o.put("designerWritesXsd", designerWrites);
		if (!s.declared)
		{
			o.put("xsdLine", XsdActivator.declarationLine());
			o.put("howTo", designerWrites
				? "A module model: open the designer (designerUrl) and press Activation; EBX reads the XSD at its next start (one restart, once)."
				: "A module model: add xsdLine to the osd:table of " + pTarget.table.getTablePath().format() + " in the module's XSD" + (s.currentForm == null ? "" : " (it replaces the recordForm line there)")
					+ " and deliver the module; EBX reads the XSD at its next start. The designer does not write module files on this server"
					+ (s.file == null ? " (it cannot reach the XSD)." : " (JVM property " + XsdActivator.WRITE_PROPERTY + "=true lets its Activation button do it)."));
		}
		return o;
	}

	private void row(final ObjectNode pOut, final ExplorerStore.Row pRow, final Target pTarget)
	{
		pOut.put("id", pRow.id);
		pOut.put("label", pRow.label);
		pOut.put("model", pRow.model);
		pOut.put("table", pRow.table);
		pOut.put("active", pRow.active);
		pOut.put("version", pRow.version);
		if (pTarget != null)
		{
			pOut.put("url", this.url(pTarget, LayoutPaths.MODULE_NAME + "@" + LayoutPaths.EXPLORER_SERVICE, LayoutPaths.EXPLORER_PARAM + "=" + pRow.id));
		}
	}

	/** The web component that opens a service of this module on the table (relative to the server). */
	private String url(final Target pTarget, final String pService, final String pExtra)
	{
		return "/ebx/?branch=" + DesignerUserService.encode(pTarget.dataset.getHome().getKey().getName()) + "&instance="
			+ DesignerUserService.encode(pTarget.dataset.getAdaptationName().getStringName()) + "&xpath="
			+ DesignerUserService.encode(pTarget.table.getTablePath().format()) + "&service=" + DesignerUserService.encode(pService)
			+ (pExtra == null ? "" : "&" + pExtra);
	}

	private Set<String> published()
	{
		try
		{
			final Set<String> names = new java.util.TreeSet<String>();
			for (final PublishedProcessKey k : WorkflowEngine.getFromRepository(this.repository, null).getPublishedKeys())
			{
				names.add(k.getName());
			}
			return names;
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private String pick(final JsonNode pText)
	{
		if (pText == null || pText.isNull())
		{
			return null;
		}
		if (pText.isTextual())
		{
			return pText.asText();
		}
		final String tag = this.locale.toLanguageTag();
		if (pText.hasNonNull(tag))
		{
			return pText.get(tag).asText();
		}
		final java.util.Iterator<java.util.Map.Entry<String, JsonNode>> it = pText.fields();
		while (it.hasNext())
		{
			final java.util.Map.Entry<String, JsonNode> e = it.next();
			if (e.getKey().startsWith(this.locale.getLanguage()))
			{
				return e.getValue().asText();
			}
		}
		return pText.size() == 0 ? null : pText.elements().next().asText();
	}

	private void technical(final ObjectNode pElement)
	{
		if (LayoutMcpTools.TECHNICAL.matcher(pElement.path("path").asText()).matches())
		{
			pElement.put("technical", true);
		}
	}

	private static void removeTechnical(final ArrayNode pElements)
	{
		for (int i = pElements.size() - 1; i >= 0; i--)
		{
			if (LayoutMcpTools.TECHNICAL.matcher(pElements.get(i).path("p").asText()).matches())
			{
				pElements.remove(i);
			}
		}
	}

	private static void tables(final SchemaNode pNode, final ArrayNode pOut, final Locale pLocale)
	{
		for (final SchemaNode c : pNode.getNodeChildren())
		{
			if (c.isTableNode())
			{
				pOut.addObject().put("path", c.getPathInSchema().format()).put("label", c.getLabel(pLocale));
			}
			else if (!c.isTerminalValue())
			{
				LayoutMcpTools.tables(c, pOut, pLocale);
			}
		}
	}

	private static void copy(final JsonNode pFrom, final ObjectNode pTo, final String pKey, final String pAs)
	{
		if (pFrom.has(pKey))
		{
			pTo.set(pAs, pFrom.get(pKey));
		}
	}

	private static int version(final Adaptation pRow)
	{
		final Object v = pRow.get(LayoutPaths.VERSION);
		return v instanceof Integer ? ((Integer) v).intValue() : 0;
	}

	private static String date(final Object pValue)
	{
		return pValue instanceof Date ? new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format((Date) pValue) : null;
	}

	private static JsonNode parse(final String pText)
	{
		try
		{
			return pText == null ? null : LayoutMcpTools.JSON.readTree(pText);
		}
		catch (final Exception ex)
		{
			return LayoutMcpTools.JSON.getNodeFactory().textNode(pText);
		}
	}

	/** An object argument, also accepted as a JSON string (some clients stringify nested objects). */
	private static JsonNode document(final JsonNode pArgs, final String pName) throws Refusal
	{
		final JsonNode v = pArgs.get(pName);
		if (v == null || v.isNull())
		{
			throw new Refusal("MISSING_ARGUMENT", pName + " is required.");
		}
		if (v.isTextual())
		{
			try
			{
				LayoutMcpEndpoint.checkDepth(v.asText(), LayoutMcpEndpoint.maxDepth());
				return LayoutMcpTools.JSON.readTree(v.asText());
			}
			catch (final LayoutMcpEndpoint.BodyRefused ex)
			{
				throw new Refusal("TOO_DEEP", pName + ": " + ex.getMessage());
			}
			catch (final Exception ex)
			{
				throw new Refusal("INVALID_JSON", pName + " is not valid JSON: fix its syntax and send it again.");
			}
		}
		return v;
	}

	private static Integer optInt(final JsonNode pArgs, final String pName)
	{
		final JsonNode v = pArgs.get(pName);
		return v == null || v.isNull() || v.asText("").isEmpty() ? null : Integer.valueOf(v.asInt());
	}

	static boolean hasError(final List<Finding> pFindings)
	{
		for (final Finding f : pFindings)
		{
			if (f.error)
			{
				return true;
			}
		}
		return false;
	}

	private static void report(final ObjectNode pOut, final List<Finding> pFindings)
	{
		pOut.put("valid", !LayoutMcpTools.hasError(pFindings));
		final ArrayNode errors = pOut.putArray("errors");
		final ArrayNode warnings = pOut.putArray("warnings");
		for (final Finding f : pFindings)
		{
			(f.error ? errors : warnings).addObject().put("where", f.where).put("text", f.text);
		}
	}

	private static ObjectNode refused(final ObjectNode pOut, final String pCode, final String pMessage)
	{
		pOut.put("saved", false);
		pOut.put("code", pCode);
		pOut.put("error", pMessage);
		return pOut;
	}

	static ObjectNode fail(final String pCode, final String pMessage)
	{
		final ObjectNode o = LayoutMcpTools.JSON.createObjectNode();
		o.put("code", pCode);
		o.put("error", pMessage);
		return o;
	}

	private static String resource(final String pName)
	{
		try (InputStream in = LayoutMcpTools.class.getResourceAsStream(pName))
		{
			return in == null ? "(missing: " + pName + ")" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (final java.io.IOException ex)
		{
			return "(unreadable: " + pName + ")";
		}
	}
}
