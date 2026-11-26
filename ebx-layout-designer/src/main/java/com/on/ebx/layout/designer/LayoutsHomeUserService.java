package com.on.ebx.layout.designer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationHome;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.instance.ValueContext;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaTypeName;
import com.orchestranetworks.schema.dynamic.BeanDefinition;
import com.orchestranetworks.schema.dynamic.BeanElement;
import com.orchestranetworks.schema.types.dataspace.DataspaceSet;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.ui.UIButtonSpecNavigation;
import com.orchestranetworks.ui.UIButtonSpecSubmit;
import com.orchestranetworks.ui.selection.TableViewEntitySelection;
import com.orchestranetworks.userservice.UserService;
import com.orchestranetworks.userservice.UserServiceDisplayConfigurator;
import com.orchestranetworks.userservice.ObjectKey;
import com.orchestranetworks.userservice.UserServiceEvent;
import com.orchestranetworks.userservice.UserServiceEventContext;
import com.orchestranetworks.userservice.UserServiceEventOutcome;
import com.orchestranetworks.userservice.UserServiceObjectContextBuilder;
import com.orchestranetworks.userservice.UserServicePane;
import com.orchestranetworks.userservice.UserServicePaneContext;
import com.orchestranetworks.userservice.UserServicePaneWriter;
import com.orchestranetworks.userservice.UserServiceProcessEventOutcomeContext;
import com.orchestranetworks.userservice.UserServiceSetupDisplayContext;
import com.orchestranetworks.userservice.UserServiceSetupObjectContext;
import com.orchestranetworks.userservice.UserServiceValidateContext;

/**
 * The page behind "Record form layouts": the saved layouts (each opens the designer on a dataset of its model) and a
 * choice of dataspace, dataset and table for a new one. The page is {@code www/home.js}, fed with one JSON object;
 * opening the designer is a navigation of the top window, kept inside the current perspective.
 */
public final class LayoutsHomeUserService implements UserService<TableViewEntitySelection>
{
	/** Keeps the page quick on an instance with many dataspaces. */
	private static final int MAX_DATASPACES = 400;

	/** The "new layout" form: EBX's own dataspace and dataset selectors (with their search), then the table. */
	private static final ObjectKey CHOICE = ObjectKey.forName("ldChoice");
	private static final Path DATASPACE = Path.parse("./dataspace");
	private static final Path DATASET = Path.parse("./dataset");
	private static final Path TABLE = Path.parse("./table");

	private String dataspace;
	private String dataset;
	/** Set by "Continue" once a table is chosen: the next display navigates to the designer. */
	private String[] target;
	private String problem;

	@Override
	public void setupObjectContext(final UserServiceSetupObjectContext<TableViewEntitySelection> pContext, final UserServiceObjectContextBuilder pBuilder)
	{
		final Locale locale = pContext.getSession().getLocale();
		final BeanDefinition def = pBuilder.createBeanDefinition();
		final BeanElement ds = def.createElement(LayoutsHomeUserService.DATASPACE, SchemaTypeName.OSD_DATASPACE_KEY);
		ds.setLabel(Messages.get(locale, "home.dataspace"));
		ds.setMinOccurs(1);
		ds.addFacetDataspaceSet().includeAllDataspaces(DataspaceSet.DataspaceType.BRANCH);
		if (this.dataspace != null)
		{
			ds.setDefaultValue(this.dataspace);
		}
		final BeanElement set = def.createElement(LayoutsHomeUserService.DATASET, SchemaTypeName.OSD_DATASET_NAME);
		set.setLabel(Messages.get(locale, "home.dataset"));
		set.setMinOccurs(1);
		set.addFacetDatasetSet(Path.parse("../dataspace")).includeAllDatasets();
		if (this.dataset != null)
		{
			set.setDefaultValue(this.dataset);
		}
		final BeanElement table = def.createElement(LayoutsHomeUserService.TABLE, SchemaTypeName.XS_STRING);
		table.setLabel(Messages.get(locale, "home.table"));
		table.setDescription(Messages.get(locale, "home.tableHint"));
		table.addFacetConstraint(TableEnumeration.class);
		pBuilder.registerBean(LayoutsHomeUserService.CHOICE, def);
	}

	void handle(final UserServiceEventContext pContext)
	{
		final Locale locale = pContext.getSession().getLocale();
		final ValueContext values = pContext.getValueContext(LayoutsHomeUserService.CHOICE);
		final Object ds = values.getValue(LayoutsHomeUserService.DATASPACE);
		final Object set = values.getValue(LayoutsHomeUserService.DATASET);
		final Object table = values.getValue(LayoutsHomeUserService.TABLE);
		this.dataspace = ds == null ? null : String.valueOf(ds);
		this.dataset = set == null ? null : String.valueOf(set);
		this.problem = null;
		if (this.dataspace == null || this.dataset == null || TableEnumeration.dataset(ds, set) == null)
		{
			this.problem = Messages.get(locale, "home.pickDataset");
			return;
		}
		if (table == null)
		{
			this.problem = Messages.get(locale, "home.pickTable");
			return;
		}
		this.target = new String[] { TableEnumeration.homeKey(this.dataspace).getName(), this.dataset, String.valueOf(table) };
	}

	@Override
	public void setupDisplay(final UserServiceSetupDisplayContext<TableViewEntitySelection> pContext, final UserServiceDisplayConfigurator pConfigurator)
	{
		final Session session = pContext.getSession();
		final Locale locale = session.getLocale();
		pConfigurator.setTitle(Messages.get(locale, "home.label"));
		final UIButtonSpecNavigation close = pConfigurator.newCloseButton();
		close.setAskBeforeLeavingModifiedForm(false);
		pConfigurator.setRightButtons(close);
		if (session.isUserInRole(Profile.ADMINISTRATOR))
		{
			final UIButtonSpecSubmit go = pConfigurator.newSubmitButton(Messages.get(locale, "home.continue"), new UserServiceEvent()
			{
				@Override
				public UserServiceEventOutcome processEvent(final UserServiceEventContext pEventContext)
				{
					LayoutsHomeUserService.this.handle(pEventContext);
					return null;
				}
			});
			pConfigurator.setLeftButtons(go);
		}
		final Repository repository = pContext.getEntitySelection().getDataset().getHome().getRepository();
		pConfigurator.setContent(new UserServicePane()
		{
			@Override
			public void writePane(final UserServicePaneContext pPaneContext, final UserServicePaneWriter pWriter)
			{
				if (!session.isUserInRole(Profile.ADMINISTRATOR))
				{
					pWriter.add("<p style=\"padding:16px\">").add(DesignerUserService.esc(Messages.get(locale, "denied"))).add("</p>");
					return;
				}
				String boot;
				try
				{
					final ObjectNode data = LayoutsHomeUserService.export(repository, session);
					if (LayoutsHomeUserService.this.target != null)
					{
						final ObjectNode go = data.putObject("go");
						go.put("dataspace", LayoutsHomeUserService.this.target[0]);
						go.put("dataset", LayoutsHomeUserService.this.target[1]);
						go.put("table", LayoutsHomeUserService.this.target[2]);
						LayoutsHomeUserService.this.target = null;
					}
					if (LayoutsHomeUserService.this.problem != null)
					{
						data.put("problem", LayoutsHomeUserService.this.problem);
					}
					boot = LayoutStore.json().writeValueAsString(data);
				}
				catch (final Exception ex)
				{
					LoggingCategory.getKernel().error("[layout-designer] layouts page not built: " + ex, ex);
					pWriter.add("<p style=\"padding:16px;color:#C73200\">").add(DesignerUserService.esc(ex.toString())).add("</p>");
					return;
				}
				final String base = "/" + LayoutPaths.MODULE_NAME + "/www/";
				pWriter.add("<link rel=\"stylesheet\" href=\"").add(base).add("designer.css?v=").add(DesignerUserService.ASSET_VERSION).add("\">");
				// the "new layout" form: EBX's own widgets, posted by the Continue button
				pWriter.add("<div class=\"ld-app\"><div class=\"wrap\" style=\"max-width:1200px;padding-bottom:0\"><section class=\"out\"><div class=\"out-hd\"><h2>")
					.add(DesignerUserService.esc(Messages.get(locale, "home.new"))).add("</h2></div><div style=\"padding:12px 16px\">");
				pWriter.setCurrentObject(LayoutsHomeUserService.CHOICE);
				pWriter.startTableFormRow();
				pWriter.addFormRow(LayoutsHomeUserService.DATASPACE);
				pWriter.addFormRow(LayoutsHomeUserService.DATASET);
				pWriter.addFormRow(LayoutsHomeUserService.TABLE);
				pWriter.endTableFormRow();
				pWriter.add("<p class=\"hint\" id=\"ld-h-note\">").add(DesignerUserService.esc(Messages.get(locale, "home.howto"))).add("</p></div></section></div></div>");
				pWriter.add("<div id=\"ld-home\" class=\"ld-app\"></div>");
				final String safe = boot.replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
				pWriter.addJS_cr("window.LD_HOME=" + safe + ";");
				pWriter.addJS_cr("(function(){var s=document.createElement('script');s.src='" + base + "home.js?v=" + DesignerUserService.ASSET_VERSION
					+ "';document.body.appendChild(s);})();");
			}
		});
	}

	static ObjectNode export(final Repository pRepository, final Session pSession)
	{
		final Locale locale = pSession.getLocale();
		final ObjectNode out = LayoutStore.json().createObjectNode();
		out.put("userLocale", locale.toLanguageTag());
		out.put("service", LayoutPaths.MODULE_NAME + "@" + DesignerServiceDeclaration.SERVICE_NAME);

		// open, non-technical dataspaces, their datasets and tables
		final ArrayNode spaces = out.putArray("dataspaces");
		final List<AdaptationHome> homes = new ArrayList<AdaptationHome>();
		LayoutsHomeUserService.collect(pRepository.getReferenceBranch(), homes);
		for (final AdaptationHome home : homes)
		{
			final ArrayNode datasets = LayoutStore.json().createArrayNode();
			for (final Adaptation dataset : home.findAllRoots())
			{
				try
				{
					final ObjectNode d = datasets.addObject();
					d.put("name", dataset.getAdaptationName().getStringName());
					d.put("label", dataset.getLabelOrName(locale));
					d.put("model", dataset.getSchemaLocation().format());
					final ArrayNode tables = d.putArray("tables");
					LayoutsHomeUserService.tables(dataset.getSchemaNode(), locale, tables);
					if (tables.size() == 0)
					{
						datasets.remove(datasets.size() - 1);
					}
				}
				catch (final RuntimeException ex)
				{
					// a dataset whose model does not compile: not offered
				}
			}
			if (datasets.size() > 0)
			{
				final ObjectNode s = spaces.addObject();
				s.put("key", home.getKey().getName());
				s.put("label", home.getLabelOrName(locale));
				s.set("datasets", datasets);
			}
		}

		// the saved layouts
		final ArrayNode layouts = out.putArray("layouts");
		final Adaptation dataset = LayoutStore.dataset(pRepository);
		if (dataset != null)
		{
			final AdaptationTable table = dataset.getTable(LayoutPaths.FORM_LAYOUT);
			final RequestResult rows = table.createRequest().execute();
			try
			{
				for (Adaptation row = rows.nextAdaptation(); row != null; row = rows.nextAdaptation())
				{
					final ObjectNode l = layouts.addObject();
					l.put("label", row.getString(LayoutPaths.LABEL));
					l.put("model", row.getString(LayoutPaths.MODEL));
					l.put("table", row.getString(LayoutPaths.TABLE));
					l.put("active", Boolean.TRUE.equals(row.get(LayoutPaths.ACTIVE)));
					l.put("version", row.get(LayoutPaths.VERSION) == null ? 0 : ((Integer) row.get(LayoutPaths.VERSION)).intValue());
					l.put("updatedBy", row.getString(LayoutPaths.UPDATED_BY));
					final Object on = row.get(LayoutPaths.UPDATED_ON);
					l.put("updatedOn", on instanceof Date ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format((Date) on) : "");
				}
			}
			finally
			{
				rows.close();
			}
		}
		return out;
	}

	/** Open branches only, technical ones left out; a child branch hangs under a snapshot of its parent. */
	private static void collect(final AdaptationHome pHome, final List<AdaptationHome> pOut)
	{
		if (pHome == null || pOut.size() >= LayoutsHomeUserService.MAX_DATASPACES || pOut.contains(pHome))
		{
			return;
		}
		if (pHome.isBranch() && pHome.isOpenBranch() && !pHome.isTechnicalBranch())
		{
			pOut.add(pHome);
		}
		if (pHome.isBranch() && pHome.isTechnicalBranch())
		{
			return;
		}
		for (final AdaptationHome version : pHome.getVersionChildren())
		{
			for (final AdaptationHome child : version.getBranchChildren())
			{
				LayoutsHomeUserService.collect(child, pOut);
			}
		}
		for (final AdaptationHome child : pHome.getBranchChildren())
		{
			LayoutsHomeUserService.collect(child, pOut);
		}
	}

	private static void tables(final SchemaNode pNode, final Locale pLocale, final ArrayNode pOut)
	{
		for (final SchemaNode child : pNode.getNodeChildren())
		{
			if (child.isTableNode())
			{
				final ObjectNode t = pOut.addObject();
				t.put("path", child.getPathInSchema().format());
				t.put("label", child.getLabel(pLocale));
			}
			else if (child.isComplex() && !child.isTerminalValue())
			{
				LayoutsHomeUserService.tables(child, pLocale, pOut);
			}
		}
	}

	@Override
	public void validate(final UserServiceValidateContext<TableViewEntitySelection> pContext)
	{
		// read-only page
	}

	@Override
	public UserServiceEventOutcome processEventOutcome(final UserServiceProcessEventOutcomeContext<TableViewEntitySelection> pContext, final UserServiceEventOutcome pOutcome)
	{
		return pOutcome;
	}
}
