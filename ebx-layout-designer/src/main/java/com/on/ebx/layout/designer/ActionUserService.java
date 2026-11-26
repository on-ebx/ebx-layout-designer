package com.on.ebx.layout.designer;

import com.fasterxml.jackson.databind.JsonNode;
import com.on.ebx.layout.runtime.Audience;
import com.on.ebx.layout.runtime.BlockAccess;
import com.on.ebx.layout.runtime.LText;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.base.text.UserMessage;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.OperationException;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.ui.selection.RecordEntitySelection;
import com.orchestranetworks.userservice.ObjectKey;
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
import com.orchestranetworks.workflow.ProcessInstance;
import com.orchestranetworks.workflow.ProcessLauncher;
import com.orchestranetworks.workflow.ProcessLauncherResult;
import com.orchestranetworks.workflow.PublishedProcessKey;
import com.orchestranetworks.workflow.UserTask;
import com.orchestranetworks.workflow.WorkItemKey;
import com.orchestranetworks.workflow.WorkflowEngine;

/**
 * A workflow button of an "actions" block: asks for confirmation, launches the publication as the user with the record's
 * XPath in the button's variable, then opens the first task (inside the perspective the form was in). The button is read
 * again from the layout as this user sees it, never from the address; a user who may not launch the publication, or who
 * may not see the block, gets a message and nothing is launched.
 */
public final class ActionUserService implements UserService<RecordEntitySelection>
{
	private static final ObjectKey RECORD = ObjectKey.forName("record");
	private static final long WAIT_MS = 8000;

	private Adaptation record;
	private String blockId;
	private int index = -1;
	private String perspective;
	/** Set once launched: where the user goes next. */
	private String next;
	private String error;

	@Override
	public void setupObjectContext(final UserServiceSetupObjectContext<RecordEntitySelection> pContext, final UserServiceObjectContextBuilder pBuilder)
	{
		if (this.record == null)
		{
			this.record = pContext.getEntitySelection().getRecord();
			this.blockId = pContext.getParameter(LayoutPaths.GRID_BLOCK_PARAM);
			try
			{
				this.index = Integer.parseInt(pContext.getParameter(LayoutPaths.ACTION_PARAM));
			}
			catch (final RuntimeException ex)
			{
				this.index = -1;
			}
			final String p = pContext.getParameter(LayoutPaths.PERSPECTIVE_PARAM);
			this.perspective = p == null || !p.matches("[A-Za-z0-9_.-]+") ? null : p;
		}
		if (this.record != null)
		{
			pBuilder.registerRecordOrDataSet(ActionUserService.RECORD, this.record);
		}
	}

	@Override
	public void setupDisplay(final UserServiceSetupDisplayContext<RecordEntitySelection> pContext, final UserServiceDisplayConfigurator pConfigurator)
	{
		final Session session = pContext.getSession();
		final JsonNode button = this.button(session);
		final String label = button == null ? "" : LText.pick(button.get("label"), session.getLocale(), java.util.Locale.US);
		pConfigurator.setTitle(label.isEmpty() ? Messages.get(session.getLocale(), "action.label") : label);
		pConfigurator.setDetectionOfLostModificationDisabled(true);
		pConfigurator.setLeftButtons(pConfigurator.newCloseButton());
		final ProcessInstance already = button == null || this.next != null ? null : this.running(session, button);
		if (already != null)
		{
			final String link = this.runningLink(already);
			pConfigurator.setRightButtons(pConfigurator.newSubmitButton(Messages.get(session.getLocale(), "action.openRunning"), new UserServiceEvent()
			{
				@Override
				public UserServiceEventOutcome processEvent(final UserServiceEventContext pEvent)
				{
					ActionUserService.this.next = link;
					return null;
				}
			}));
		}
		else if (button != null && this.next == null && this.mayLaunch(session, button))
		{
			pConfigurator.setRightButtons(pConfigurator.newSubmitButton(Messages.get(session.getLocale(), "action.launch"), new UserServiceEvent()
			{
				@Override
				public UserServiceEventOutcome processEvent(final UserServiceEventContext pEvent)
				{
					ActionUserService.this.launch(pEvent.getSession());
					return null;
				}
			}));
		}
		pConfigurator.setContent(new UserServicePane()
		{
			@Override
			public void writePane(final UserServicePaneContext pPane, final UserServicePaneWriter pWriter)
			{
				ActionUserService.this.write(pWriter, session, button, label, already);
			}
		});
	}

	private void write(final UserServicePaneWriter pWriter, final Session pSession, final JsonNode pButton, final String pLabel, final ProcessInstance pRunning)
	{
		final java.util.Locale locale = pSession.getLocale();
		pWriter.add("<div style=\"padding:16px 18px;font-size:14px;line-height:1.5;max-width:560px\">");
		if (this.next != null)
		{
			pWriter.add("<p>").add(ActionUserService.esc(Messages.get(locale, "action.started"))).add("</p></div>");
			pWriter.addJS_cr("window.top.location.href=" + ActionUserService.js(this.next) + ";");
			return;
		}
		if (pButton == null || !this.mayLaunch(pSession, pButton))
		{
			pWriter.add("<p>").add(ActionUserService.esc(Messages.get(locale, "action.notAllowed"))).add("</p></div>");
			return;
		}
		if (pRunning != null)
		{
			pWriter.add("<p>").add(ActionUserService.esc(Messages.get(locale, "action.running", pLabel, this.record.getLabel(locale)))).add("</p></div>");
			return;
		}
		if (this.error != null)
		{
			pWriter.add("<p style=\"color:#C73200\">").add(ActionUserService.esc(this.error)).add("</p>");
		}
		pWriter.add("<p>").add(ActionUserService.esc(Messages.get(locale, "action.confirm", pLabel, this.record.getLabel(locale))))
			.add("</p></div>");
	}

	@Override
	public void validate(final UserServiceValidateContext<RecordEntitySelection> pContext)
	{
		// nothing posted
	}

	@Override
	public UserServiceEventOutcome processEventOutcome(final UserServiceProcessEventOutcomeContext<RecordEntitySelection> pContext, final UserServiceEventOutcome pOutcome)
	{
		return pOutcome;
	}

	private boolean mayLaunch(final Session pSession, final JsonNode pButton)
	{
		try
		{
			return WorkflowEngine.getFromRepository(this.record.getHome().getRepository(), pSession)
				.isUserAuthorizedToLaunch(PublishedProcessKey.forName(pButton.path("workflow").asText("")));
		}
		catch (final RuntimeException ex)
		{
			return false;
		}
	}

	/**
	 * With {@code "single": true} on the button: the instance of the publication still running for this record (its
	 * XPath in the button's variable), or null.
	 */
	private ProcessInstance running(final Session pSession, final JsonNode pButton)
	{
		final String var = pButton.path("var").asText("").trim();
		if (!pButton.path("single").asBoolean(false) || var.isEmpty())
		{
			return null;
		}
		try
		{
			final WorkflowEngine engine = WorkflowEngine.getFromRepository(this.record.getHome().getRepository(), pSession);
			for (final com.orchestranetworks.workflow.ProcessInstanceKey k : engine.getProcessInstanceKeysForDataContextVariable(
				PublishedProcessKey.forName(pButton.path("workflow").asText()), var, this.record.toXPathExpression()))
			{
				final ProcessInstance instance = engine.getProcessInstance(k);
				if (instance != null && !instance.isCompleted())
				{
					return instance;
				}
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] action " + this.blockId + "/" + this.index + ": running instance not checked: " + ex.getMessage());
		}
		return null;
	}

	/** The task of a running instance, shown without being taken (startWorkItem=false); the inbox when it has none. */
	private String runningLink(final ProcessInstance pInstance)
	{
		final java.util.List<UserTask.WorkItem> items = pInstance.getWorkItems();
		return ActionUserService.workItemLink(items == null || items.isEmpty() ? null : items.get(0).getWorkItemKey(), this.perspective)
			.replace("&startWorkItem=true", "&startWorkItem=false");
	}

	/** Launches as the user; the first work item when it comes up in time, else the inbox. */
	void launch(final Session pSession)
	{
		final JsonNode button = this.button(pSession);
		if (button == null || !this.mayLaunch(pSession, button))
		{
			return;
		}
		final ProcessInstance already = this.running(pSession, button);
		if (already != null)
		{
			this.next = this.runningLink(already);
			return;
		}
		try
		{
			final WorkflowEngine engine = WorkflowEngine.getFromRepository(this.record.getHome().getRepository(), pSession);
			final ProcessLauncher launcher = engine.getProcessLauncher(PublishedProcessKey.forName(button.path("workflow").asText()));
			final String var = button.path("var").asText("").trim();
			if (!var.isEmpty() && launcher.isDefinedAsInputParameter(var))
			{
				launcher.setInputParameter(var, this.record.toXPathExpression());
			}
			launcher.setLabel(UserMessage.createInfo(LText.pick(button.get("label"), pSession.getLocale(), java.util.Locale.US) + " - " + this.record.getLabel(pSession.getLocale())));
			final ProcessLauncherResult result = launcher.launchProcessWithResult();
			WorkItemKey item = result.getWorkItemKey();
			if (item == null)
			{
				item = ActionUserService.firstWorkItem(engine, result);
			}
			final ProcessInstance instance = item == null ? engine.getProcessInstance(result.getProcessInstanceKey()) : null;
			if (instance != null && instance.isInError())
			{
				// launched, but its first steps failed (a missing input, a script error): say so instead of opening the inbox
				this.error = Messages.get(pSession.getLocale(), "action.inError");
				LoggingCategory.getKernel().warn("[layout-designer] action " + this.blockId + "/" + this.index + ": instance " + result.getProcessInstanceKey()
					+ " is in error right after its launch (see workflow.log)");
				return;
			}
			this.next = ActionUserService.workItemLink(item, this.perspective);
		}
		catch (final OperationException | RuntimeException ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] action " + this.blockId + "/" + this.index + " not launched: " + ex, ex);
			this.error = String.valueOf(ex.getMessage());
		}
	}

	/** The button of the block, as this user sees the layout; null when there is none. */
	private JsonNode button(final Session pSession)
	{
		if (this.record == null || this.blockId == null || this.blockId.isEmpty() || this.index < 0)
		{
			return null;
		}
		try
		{
			final JsonNode layout = LayoutStore.find(this.record.getHome().getRepository(), this.record.getContainer().getSchemaLocation(),
				this.record.getContainerTable().getTablePath());
			if (layout == null)
			{
				return null;
			}
			final boolean seesAll = layout.path("adminSeesAll").asBoolean(true) && pSession.isUserInRole(Profile.ADMINISTRATOR);
			final JsonNode resolved = Audience.resolve(layout, roles -> PersonalizeUserService.inAnyRole(pSession, roles), seesAll);
			for (final java.util.List<JsonNode> blocks : Audience.blocksByTab(resolved).values())
			{
				for (final JsonNode b : blocks)
				{
					if (this.blockId.equals(b.path("id").asText()) && "actions".equals(b.path("type").asText()) && BlockAccess.readable(b, pSession, this.record))
					{
						final JsonNode a = b.path("items").path(this.index);
						return a.isObject() && !a.path("workflow").asText("").trim().isEmpty() ? a : null;
					}
				}
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] action " + this.blockId + "/" + this.index + ": " + ex, ex);
		}
		return null;
	}

	private static WorkItemKey firstWorkItem(final WorkflowEngine pEngine, final ProcessLauncherResult pResult)
	{
		final long end = System.currentTimeMillis() + ActionUserService.WAIT_MS;
		while (System.currentTimeMillis() < end)
		{
			final ProcessInstance instance = pEngine.getProcessInstance(pResult.getProcessInstanceKey());
			if (instance != null && (instance.isInError() || instance.isCompleted()))
			{
				return null;
			}
			final java.util.List<UserTask.WorkItem> items = instance == null ? null : instance.getWorkItems();
			if (items != null && !items.isEmpty())
			{
				return items.get(0).getWorkItemKey();
			}
			try
			{
				Thread.sleep(250);
			}
			catch (final InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	/** The inbox, on the work item when there is one, inside the perspective when one is given (documented web component). */
	static String workItemLink(final WorkItemKey pItem, final String pPerspective)
	{
		final String inPerspective = pPerspective == null ? "" : "&perspectiveName=" + ActionUserService.encode(pPerspective) + "&scope=full";
		if (pItem == null)
		{
			return "/ebx/?service=%40workflow&workflowView=inbox" + inPerspective;
		}
		final String predicate = pItem.getXPathPredicate();
		return "/ebx/?service=%40workflow&workflowView=inbox&startWorkItem=true" + inPerspective + "&xpath="
			+ ActionUserService.encode(predicate.startsWith("[") ? predicate : "[" + predicate + "]");
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

	private static String js(final String s)
	{
		return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\x3C") + "'";
	}

	private static String esc(final String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
