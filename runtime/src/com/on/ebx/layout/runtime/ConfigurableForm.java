package com.on.ebx.layout.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.base.text.UserMessage;
import com.orchestranetworks.instance.Repository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.ui.form.UIForm;
import com.orchestranetworks.ui.form.UIFormBody;
import com.orchestranetworks.ui.form.UIFormContext;
import com.orchestranetworks.ui.form.UIFormPaneWithTabs;

/**
 * The record form of every table whose layout is drawn in the Layout Designer. A data model declares it once
 * ({@code <recordForm osd:class="com.on.ebx.layout.runtime.ConfigurableForm"/>} in the table's {@code osd:table});
 * from then on the layout is read at each display, so a saved change shows at the next opening of a record, with no
 * restart and no compilation. Without an active layout, the standard EBX form is shown.
 */
public class ConfigurableForm extends UIForm
{
	@Override
	public void defineBody(final UIFormBody pBody, final UIFormContext pContext)
	{
		JsonNode layout = null;
		try
		{
			final AdaptationTable table = pContext.getCurrentTable();
			FormSightings.seen(pContext.getCurrentDataSet().getSchemaLocation(), table.getTablePath(),
				pContext.isCreatingRecord() || pContext.getCurrentRecord() == null ? "" : pContext.getCurrentRecord().getLabel(pContext.getLocale()));
			layout = LayoutStore.find(Repository.getDefault(), pContext.getCurrentDataSet().getSchemaLocation(), table.getTablePath());
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] the layout could not be read: " + ex, ex);
		}
		if (layout == null || !layout.path("tabs").isArray() || layout.path("tabs").size() == 0)
		{
			super.defineBody(pBody, pContext);
			return;
		}
		layout = ConfigurableForm.forUser(layout, pContext);
		Access.begin(pContext.getSession(), pContext.isCreatingRecord() ? null : pContext.getCurrentRecord(), pContext.getCurrentTable());
		try
		{
			ConfigurableForm.body(pBody, pContext, layout);
		}
		finally
		{
			Access.end();
		}
	}

	private static void body(final UIFormBody pBody, final UIFormContext pContext, final JsonNode layout)
	{
		final RenderContext rc = new RenderContext(pContext, layout);
		final JsonNode tabs = layout.path("tabs");
		if (tabs.size() == 1)
		{
			pBody.setContent(new LayoutPane(rc, tabs.get(0), 0));
			return;
		}
		final UIFormPaneWithTabs pane = new UIFormPaneWithTabs();
		for (int i = 0; i < tabs.size(); i++)
		{
			final JsonNode tab = tabs.get(i);
			pane.addTab(UserMessage.createInfo(rc.tabLabel(tab)), new LayoutPane(rc, tab, i));
		}
		pBody.setContent(pane);
	}

	/** The layout as the current user sees it: their role's variant and restrictions, then their own arrangement. */
	static JsonNode forUser(final JsonNode pLayout, final UIFormContext pContext)
	{
		try
		{
			final Session session = pContext.getSession();
			final ObjectNode resolved = Audience.resolve(pLayout, roles -> Behaviors.inAnyRole(session, roles), ConfigurableForm.seesAll(pLayout, session));
			if (Personal.allowed(resolved) && session.getUserReference() != null)
			{
				final JsonNode prefs = LayoutStore.findPrefs(Repository.getDefault(), session.getUserReference().getUserId(),
					pContext.getCurrentDataSet().getSchemaLocation(), pContext.getCurrentTable().getTablePath(), resolved.path("variant").asText());
				Personal.apply(resolved, prefs, pContext.isCreatingRecord());
			}
			return resolved;
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] the layout could not be adapted to the user: " + ex, ex);
			return pLayout;
		}
	}

	/** Administrators see every tab, block and field unless the layout says {@code "adminSeesAll": false}. */
	static boolean seesAll(final JsonNode pLayout, final Session pSession)
	{
		return pLayout.path("adminSeesAll").asBoolean(true) && pSession.isUserInRole(Profile.ADMINISTRATOR);
	}
}
