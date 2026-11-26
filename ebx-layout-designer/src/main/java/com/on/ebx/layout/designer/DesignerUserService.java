package com.on.ebx.layout.designer;

import java.util.Locale;

import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.PrimaryKey;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.ui.UIButtonSpecNavigation;
import com.orchestranetworks.ui.UIButtonSpecSubmit;
import com.orchestranetworks.ui.selection.TableViewEntitySelection;
import com.orchestranetworks.userservice.UserService;
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
 * The designer screen. The page itself is {@code www/designer.js}, fed with one JSON object ({@link ModelExport}); its
 * buttons post the edited layout through EBX's own form (hidden fields + a hidden submit button), so a layout of any
 * size goes through, with the user's session and nothing else.
 */
public final class DesignerUserService implements UserService<TableViewEntitySelection>
{
	static final String SUBMIT_ID = "ldSubmit";
	static final String CLOSE_ID = "ldClose";
	/** Changed at each release, so that browsers fetch the new page script. */
	static final String ASSET_VERSION = "1.17.1";

	private AdaptationTable table;
	private String recordKey;
	private String draft;
	private String notice;
	private boolean noticeError;

	@Override
	public void setupObjectContext(final UserServiceSetupObjectContext<TableViewEntitySelection> pContext, final UserServiceObjectContextBuilder pBuilder)
	{
		if (this.table != null)
		{
			return;
		}
		final TableViewEntitySelection selection = pContext.getEntitySelection();
		this.table = selection.getTable();
		try
		{
			final RequestResult rows = selection.getSelectedRecords().execute();
			try
			{
				final Adaptation first = rows.nextAdaptation();
				if (first != null)
				{
					this.recordKey = first.getOccurrencePrimaryKey().format();
				}
			}
			finally
			{
				rows.close();
			}
		}
		catch (final RuntimeException ex)
		{
			// opened with nothing selected
		}
	}

	@Override
	public void setupDisplay(final UserServiceSetupDisplayContext<TableViewEntitySelection> pContext, final UserServiceDisplayConfigurator pConfigurator)
	{
		final Session session = pContext.getSession();
		final Locale locale = session.getLocale();
		pConfigurator.setTitle(Messages.get(locale, "service.title", this.table.getTableNode().getLabel(locale)));
		final UIButtonSpecNavigation close = pConfigurator.newCloseButton();
		close.setAskBeforeLeavingModifiedForm(false);
		close.setId(DesignerUserService.CLOSE_ID);
		pConfigurator.setRightButtons(close);
		if (!session.isUserInRole(Profile.ADMINISTRATOR))
		{
			pConfigurator.setContent(new UserServicePane()
			{
				@Override
				public void writePane(final UserServicePaneContext pPaneContext, final UserServicePaneWriter pWriter)
				{
					pWriter.add("<p style=\"padding:16px\">").add(DesignerUserService.esc(Messages.get(locale, "denied"))).add("</p>");
				}
			});
			return;
		}
		final UIButtonSpecSubmit submit = pConfigurator.newSubmitButton(Messages.get(locale, "button.apply"), new UserServiceEvent()
		{
			@Override
			public UserServiceEventOutcome processEvent(final UserServiceEventContext pEventContext)
			{
				DesignerUserService.this.handle(pEventContext);
				return null;
			}
		});
		submit.setId(DesignerUserService.SUBMIT_ID);
		pConfigurator.setLeftButtons(submit);
		pConfigurator.setContent(new UserServicePane()
		{
			@Override
			public void writePane(final UserServicePaneContext pPaneContext, final UserServicePaneWriter pWriter)
			{
				DesignerUserService.this.write(pWriter, session);
			}
		});
	}

	private void write(final UserServicePaneWriter pWriter, final Session pSession)
	{
		final Repository repository = this.table.getContainerAdaptation().getHome().getRepository();
		final SchemaLocation model = this.table.getContainerAdaptation().getSchemaLocation();
		final Path tablePath = this.table.getTablePath();
		String boot;
		try
		{
			final XsdActivator.State activation = XsdActivator.state(model, tablePath);
			final com.fasterxml.jackson.databind.node.ObjectNode data = ModelExport.export(repository, this.table, this.record(), pSession.getLocale(), this.draft,
				activation, this.notice, this.noticeError);
			data.set("directory", DirectoryRoles.export(repository, pSession, pSession.getLocale()));
			final com.onwbp.adaptation.Adaptation dataset = this.table.getContainerAdaptation();
			data.put("explorerDesignerUrl", "/ebx/?branch=" + DesignerUserService.encode(dataset.getHome().getKey().getName()) + "&instance="
				+ DesignerUserService.encode(dataset.getAdaptationName().getStringName()) + "&xpath=" + DesignerUserService.encode(tablePath.format()) + "&service="
				+ DesignerUserService.encode(LayoutPaths.MODULE_NAME + "@" + LayoutPaths.EXPLORER_DESIGNER_SERVICE));
			boot = LayoutStore.json().writeValueAsString(data);
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] designer data not built: " + ex, ex);
			pWriter.add("<p style=\"padding:16px;color:#C73200\">").add(DesignerUserService.esc(ex.toString())).add("</p>");
			return;
		}
		this.notice = null;
		final String base = "/" + LayoutPaths.MODULE_NAME + "/www/";
		pWriter.add("<link rel=\"stylesheet\" href=\"").add(base).add("designer.css?v=").add(DesignerUserService.ASSET_VERSION).add("\">");
		pWriter.add("<style>#").add(DesignerUserService.SUBMIT_ID).add("{display:none!important}</style>");
		pWriter.add("<input type=\"hidden\" name=\"ldAction\" id=\"ldAction\" value=\"\">");
		pWriter.add("<input type=\"hidden\" name=\"ldRecord\" id=\"ldRecord\" value=\"\">");
		pWriter.add("<input type=\"hidden\" name=\"ldLayout\" id=\"ldLayout\" value=\"\">");
		pWriter.add("<div id=\"ld-app\" class=\"ld-app\"></div>");
		// JSON inside a script: no "</", no line separators
		final String safe = boot.replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
		pWriter.addJS_cr("window.LD_BOOT=" + safe + ";");
		pWriter.addJS_cr("(function(){var s=document.createElement('script');s.src='" + base + "designer.js?v=" + DesignerUserService.ASSET_VERSION
			+ "';document.body.appendChild(s);})();");
	}

	private Adaptation record()
	{
		if (this.recordKey != null)
		{
			try
			{
				final Adaptation r = this.table.lookupAdaptationByPrimaryKey(PrimaryKey.parseString(this.recordKey));
				if (r != null)
				{
					return r;
				}
			}
			catch (final RuntimeException ex)
			{
				// a key that does not parse
			}
		}
		final RequestResult rows = this.table.createRequest().execute();
		try
		{
			return rows.nextAdaptation();
		}
		finally
		{
			rows.close();
		}
	}

	void handle(final UserServiceEventContext pContext)
	{
		final Session session = pContext.getSession();
		final Locale locale = session.getLocale();
		if (!session.isUserInRole(Profile.ADMINISTRATOR))
		{
			this.notice(Messages.get(locale, "denied"), true);
			return;
		}
		final String action = DesignerUserService.param(pContext, "ldAction");
		final String layout = DesignerUserService.param(pContext, "ldLayout");
		final String record = DesignerUserService.param(pContext, "ldRecord");
		final Repository repository = this.table.getContainerAdaptation().getHome().getRepository();
		final SchemaLocation model = this.table.getContainerAdaptation().getSchemaLocation();
		final Path tablePath = this.table.getTablePath();
		try
		{
			if ("record".equals(action))
			{
				this.draft = layout;
				this.recordKey = record.isEmpty() ? null : record;
				return;
			}
			if ("save".equals(action) || "activate".equals(action))
			{
				LayoutStore.json().readTree(layout);
				final int version = LayoutWriter.save(repository, session, model, tablePath, this.table.getTableNode().getLabel(Locale.ENGLISH), layout, null);
				this.draft = null;
				this.notice(Messages.get(locale, "saved", Integer.valueOf(version)), false);
			}
			if ("activate".equals(action))
			{
				final XsdActivator.State state = XsdActivator.state(model, tablePath);
				final String previous = XsdActivator.activate(state, tablePath);
				if (!LayoutPaths.FORM_CLASS.equals(previous))
				{
					LayoutWriter.setPreviousForm(repository, session, model, tablePath, previous);
				}
				this.notice(Messages.get(locale, "activated", state.file.getName()), false);
			}
			if ("deactivate".equals(action))
			{
				final Adaptation row = LayoutStore.findRow(repository, model, tablePath);
				final String previous = row == null ? null : row.getString(LayoutPaths.PREVIOUS_FORM);
				final XsdActivator.State state = XsdActivator.state(model, tablePath);
				XsdActivator.deactivate(state, tablePath, previous);
				this.draft = layout.isEmpty() ? null : layout;
				this.notice(Messages.get(locale, "deactivated", previous == null || previous.isEmpty() ? "—" : previous), false);
			}
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] action " + action + " failed: " + ex, ex);
			this.draft = layout.isEmpty() ? this.draft : layout;
			this.notice(Messages.get(locale, "activate".equals(action) || "deactivate".equals(action) ? "activationFailed" : "saveFailed", ex.getMessage()), true);
		}
	}

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

	static String encode(final String pValue)
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

	static String esc(final String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
