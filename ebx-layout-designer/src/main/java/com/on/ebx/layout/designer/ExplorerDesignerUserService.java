package com.on.ebx.layout.designer;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.on.ebx.layout.explorer.ExplorerStore;
import com.on.ebx.layout.explorer.Query;
import com.on.ebx.layout.explorer.View;
import com.on.ebx.layout.explorer.Views;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.ui.UIButtonSpecNavigation;
import com.orchestranetworks.ui.UIButtonSpecSubmit;
import com.orchestranetworks.ui.selection.TableViewEntitySelection;
import com.orchestranetworks.userservice.UserService;
import com.orchestranetworks.userservice.UserServiceAjaxRequest;
import com.orchestranetworks.userservice.UserServiceDisplayConfigurator;
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
 * The explorer designer ({@code www/explorer-designer.js}, fed with {@link ExplorerExport}). Its buttons post through
 * EBX's own form (hidden fields + a hidden submit button), like the form designer: save, new, switch, activate,
 * deactivate, delete. Administrators only. The AJAX request "values" answers one page of a field's distinct values over
 * the whole table, so that a long list is never sent whole.
 */
public final class ExplorerDesignerUserService implements UserService<TableViewEntitySelection>
{
	static final String SUBMIT_ID = "ldxSubmit";

	private AdaptationTable table;
	private int current = -1;
	private String draft;
	private String notice;
	private boolean noticeError;

	@Override
	public void setupObjectContext(final UserServiceSetupObjectContext<TableViewEntitySelection> pContext, final UserServiceObjectContextBuilder pBuilder)
	{
		if (this.table == null)
		{
			this.table = pContext.getEntitySelection().getTable();
			try
			{
				this.current = Integer.parseInt(String.valueOf(pContext.getParameter(LayoutPaths.EXPLORER_PARAM)).trim());
			}
			catch (final RuntimeException ex)
			{
				this.current = -1;
			}
		}
	}

	@Override
	public void setupDisplay(final UserServiceSetupDisplayContext<TableViewEntitySelection> pContext, final UserServiceDisplayConfigurator pConfigurator)
	{
		final Session session = pContext.getSession();
		final Locale locale = session.getLocale();
		pConfigurator.setTitle(Messages.get(locale, "explorerDesigner.title", this.table.getTableNode().getLabel(locale)));
		pConfigurator.setDetectionOfLostModificationDisabled(true);
		final UIButtonSpecNavigation close = pConfigurator.newCloseButton();
		close.setAskBeforeLeavingModifiedForm(false);
		pConfigurator.setRightButtons(close);
		if (!session.isUserInRole(Profile.ADMINISTRATOR))
		{
			pConfigurator.setContent(new UserServicePane()
			{
				@Override
				public void writePane(final UserServicePaneContext pPane, final UserServicePaneWriter pWriter)
				{
					pWriter.add("<p style=\"padding:16px\">").add(DesignerUserService.esc(Messages.get(locale, "explorerDesigner.denied"))).add("</p>");
				}
			});
			return;
		}
		final UIButtonSpecSubmit submit = pConfigurator.newSubmitButton(Messages.get(locale, "button.apply"), new UserServiceEvent()
		{
			@Override
			public UserServiceEventOutcome processEvent(final UserServiceEventContext pEvent)
			{
				ExplorerDesignerUserService.this.handle(pEvent);
				return null;
			}
		});
		submit.setId(ExplorerDesignerUserService.SUBMIT_ID);
		pConfigurator.setLeftButtons(submit);
		pConfigurator.setContent(new UserServicePane()
		{
			@Override
			public void writePane(final UserServicePaneContext pPane, final UserServicePaneWriter pWriter)
			{
				ExplorerDesignerUserService.this.write(pWriter, session);
			}
		});
	}

	private void write(final UserServicePaneWriter pWriter, final Session pSession)
	{
		final Repository repository = this.table.getContainerAdaptation().getHome().getRepository();
		final Adaptation dataset = this.table.getContainerAdaptation();
		String boot;
		try
		{
			final ObjectNode data = ExplorerExport.export(repository, pSession, this.table, this.current, this.draft);
			if (data.path("current").hasNonNull("id"))
			{
				this.current = data.path("current").path("id").asInt();
			}
			data.with("ajax").put("values", pWriter.getURLForAjaxRequest(this.values));
			final String base = "/ebx/?branch=" + ExplorerDesignerUserService.encode(dataset.getHome().getKey().getName()) + "&instance="
				+ ExplorerDesignerUserService.encode(dataset.getAdaptationName().getStringName()) + "&xpath=" + ExplorerDesignerUserService.encode(this.table.getTablePath().format())
				+ "&service=" + ExplorerDesignerUserService.encode(LayoutPaths.MODULE_NAME + "@");
			if (this.current >= 0)
			{
				data.put("openUrl", base + LayoutPaths.EXPLORER_SERVICE + "&" + LayoutPaths.EXPLORER_PARAM + "=" + this.current);
			}
			data.put("formDesignerUrl", base + DesignerServiceDeclaration.SERVICE_NAME);
			if (this.notice != null)
			{
				data.put("notice", this.notice);
				data.put("noticeError", this.noticeError);
			}
			if (!ExplorerStore.favoritesAvailable(repository))
			{
				data.put("modelOutdated", true);
			}
			boot = LayoutStore.json().writeValueAsString(data);
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] explorer designer data not built: " + ex, ex);
			pWriter.add("<p style=\"padding:16px;color:#C73200\">").add(DesignerUserService.esc(ex.toString())).add("</p>");
			return;
		}
		this.notice = null;
		final String base = "/" + LayoutPaths.MODULE_NAME + "/www/";
		pWriter.add("<link rel=\"stylesheet\" href=\"").add(base).add("designer.css?v=").add(DesignerUserService.ASSET_VERSION).add("\">");
		pWriter.add("<link rel=\"stylesheet\" href=\"").add(base).add("explorer-designer.css?v=").add(DesignerUserService.ASSET_VERSION).add("\">");
		pWriter.add("<style>#").add(ExplorerDesignerUserService.SUBMIT_ID).add("{display:none!important}</style>");
		for (final String f : new String[] { "ldxAction", "ldxId", "ldxLabel", "ldxLayout" })
		{
			pWriter.add("<input type=\"hidden\" name=\"").add(f).add("\" id=\"").add(f).add("\" value=\"\">");
		}
		pWriter.add("<div id=\"ldx-app\" class=\"ldx-app\"></div>");
		final String safe = boot.replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
		pWriter.addJS_cr("window.LDX_BOOT=" + safe + ";");
		pWriter.addJS_cr("(function(){var s=document.createElement('script');s.src='" + base + "explorer-designer.js?v=" + DesignerUserService.ASSET_VERSION
			+ "';document.body.appendChild(s);})();");
	}

	void handle(final UserServiceEventContext pContext)
	{
		final Session session = pContext.getSession();
		final Locale locale = session.getLocale();
		if (!session.isUserInRole(Profile.ADMINISTRATOR))
		{
			this.notice(Messages.get(locale, "explorerDesigner.denied"), true);
			return;
		}
		final String action = ExplorerDesignerUserService.param(pContext, "ldxAction");
		final String layout = ExplorerDesignerUserService.param(pContext, "ldxLayout");
		final String label = ExplorerDesignerUserService.param(pContext, "ldxLabel");
		int id;
		try
		{
			id = Integer.parseInt(ExplorerDesignerUserService.param(pContext, "ldxId").trim());
		}
		catch (final RuntimeException ex)
		{
			id = this.current;
		}
		final Repository repository = this.table.getContainerAdaptation().getHome().getRepository();
		final Adaptation dataset = this.table.getContainerAdaptation();
		try
		{
			switch (action)
			{
			case "save":
			{
				final JsonNode parsed = LayoutStore.json().readTree(layout);
				if (!parsed.isObject())
				{
					throw new IllegalArgumentException("not a JSON object");
				}
				this.current = ExplorerStore.save(repository, session, id, dataset.getSchemaLocation(), this.table.getTablePath(),
					label.isEmpty() ? this.table.getTableNode().getLabel(Locale.ENGLISH) : label, layout);
				this.draft = null;
				this.notice(Messages.get(locale, "explorerDesigner.saved"), false);
				break;
			}
			case "new":
				this.current = ExplorerStore.save(repository, session, -1, dataset.getSchemaLocation(), this.table.getTablePath(),
					label.isEmpty() ? this.table.getTableNode().getLabel(locale) : label, null);
				this.draft = null;
				this.notice(Messages.get(locale, "explorerDesigner.created"), false);
				break;
			case "switch":
				this.current = id;
				this.draft = null;
				break;
			case "activate":
			case "deactivate":
				ExplorerStore.setActive(repository, session, id, "activate".equals(action));
				this.draft = layout.isEmpty() ? null : layout;
				this.notice(Messages.get(locale, "activate".equals(action) ? "explorerDesigner.activated" : "explorerDesigner.deactivated"), false);
				break;
			case "delete":
				ExplorerStore.delete(repository, session, id);
				this.current = -1;
				this.draft = null;
				this.notice(Messages.get(locale, "explorerDesigner.deleted"), false);
				break;
			default:
				break;
			}
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] explorer designer action " + action + " failed: " + ex, ex);
			this.draft = layout.isEmpty() ? this.draft : layout;
			this.notice(Messages.get(locale, "explorerDesigner.failed", String.valueOf(ex.getMessage())), true);
		}
	}

	/**
	 * One page of a field's distinct values over the whole table (parameters f path, q text, off, n), most frequent
	 * first: {"total", "items": [{"v", "l", "n"}]}. Read through a small index of that field alone, kept like the others.
	 */
	private final UserServiceAjaxRequest values = (pContext, pResponse) -> {
		final ObjectNode out = LayoutStore.json().createObjectNode();
		try
		{
			final Session session = pContext.getSession();
			final String field = pContext.getParameter("f");
			if (!session.isUserInRole(Profile.ADMINISTRATOR) || field == null || !field.startsWith("/"))
			{
				out.put("total", 0);
				out.putArray("items");
			}
			else
			{
				final ObjectNode root = LayoutStore.json().createObjectNode();
				final ObjectNode facet = root.putArray("variants").addObject().putObject("layout").putArray("facets").addObject();
				facet.put("id", "f");
				facet.put("kind", "field");
				facet.put("field", field);
				// a negative id per field: these small indexes never collide with an explorer's
				final int id = -1 - Math.abs(field.hashCode() % 1_000_000);
				final View view = Views.view(session, this.table.getContainerAdaptation(), this.table, id, root);
				final Query.FacetPage page = Query.values(view, field, pContext.getParameter("q"), ExplorerDesignerUserService.integer(pContext.getParameter("off"), 0),
					ExplorerDesignerUserService.integer(pContext.getParameter("n"), 10));
				out.setAll(ExplorerUserService.page(page));
			}
		}
		catch (final RuntimeException ex)
		{
			out.put("error", String.valueOf(ex.getMessage()));
		}
		pResponse.getWriter().add(out.toString());
	};

	private void notice(final String pText, final boolean pError)
	{
		this.notice = pText;
		this.noticeError = pError;
	}

	private static String param(final UserServiceEventContext pContext, final String pName)
	{
		final String v = pContext.getParameter(pName);
		return v == null ? "" : v;
	}

	private static int integer(final String pValue, final int pDefault)
	{
		try
		{
			return Math.max(0, Integer.parseInt(pValue.trim()));
		}
		catch (final RuntimeException ex)
		{
			return pDefault;
		}
	}

	private static String encode(final String pValue)
	{
		try
		{
			return java.net.URLEncoder.encode(pValue, "UTF-8").replace("+", "%20");
		}
		catch (final java.io.UnsupportedEncodingException ex)
		{
			return pValue;
		}
	}

	@Override
	public void validate(final UserServiceValidateContext<TableViewEntitySelection> pContext)
	{
		// handle() reports its own errors
	}

	@Override
	public UserServiceEventOutcome processEventOutcome(final UserServiceProcessEventOutcomeContext<TableViewEntitySelection> pContext, final UserServiceEventOutcome pOutcome)
	{
		return pOutcome;
	}
}
