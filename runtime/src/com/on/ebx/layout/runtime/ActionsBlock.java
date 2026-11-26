package com.on.ebx.layout.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.ServiceKey;
import com.orchestranetworks.ui.UIHttpManagerComponent;
import com.orchestranetworks.ui.form.UIFormPaneWriter;
import com.orchestranetworks.workflow.PublishedProcessKey;
import com.orchestranetworks.workflow.WorkflowEngine;

/**
 * Buttons on the record (1.14.0): launch a workflow with the record as input, or open a service on it.
 *
 * <pre>
 * {"type": "actions", "items": [
 *   {"label": {...}, "workflow": "Publication", "var": "recordXPath", "tone": "accent", "cond": "XPath"},
 *   {"label": {...}, "service": "module@Service"}]}
 * </pre>
 *
 * A workflow button shows only to a user who may launch the publication; it opens, in EBX's pop-up, the module service
 * {@link LayoutPaths#ACTION_SERVICE}, which asks for confirmation, launches as the user and opens the first task. The
 * button is identified by its position: the service reads the publication and the variable again from the layout, never
 * from the address. A service button opens that service on the record in EBX's pop-up. {@code cond} (an XPath predicate
 * on the record) hides a button when false. Nothing is shown while a record is created; a block with no button the user
 * may use is not drawn.
 */
public final class ActionsBlock
{
	private ActionsBlock()
	{
	}

	/**
	 * 1.23.1: whether a button of an Actions block is still allowed on this record for this session: the three tests that decide
	 * its drawing, in the same order (cond on the record, wf, session). The service that launches the button asks it again, as the
	 * address that opens the service can be typed by anyone.
	 */
	public static boolean allowed(final com.orchestranetworks.service.Session pSession, final com.onwbp.adaptation.Adaptation pRecord, final JsonNode pItem)
	{
		return ActionsBlock.allowed(pSession, pRecord, pItem, new java.util.function.Predicate<String>()
		{
			@Override
			public boolean test(final String pCond)
			{
				return RenderContext.xpath(pRecord, pCond, true);
			}
		});
	}

	/** The same, with the test of the XPath condition given (what a record answers is not reachable without a server). */
	public static boolean allowed(final com.orchestranetworks.service.Session pSession, final com.onwbp.adaptation.Adaptation pRecord, final JsonNode pItem,
		final java.util.function.Predicate<String> pCond)
	{
		final String cond = pItem.path("cond").asText("");
		return (cond.trim().isEmpty() || pCond.test(cond))
			&& WorkflowState.allows(pSession, pRecord, false, new java.util.HashMap<String, Object>(), pItem.get("wf"))
			&& SessionState.allows(pSession, pItem.get("session"));
	}

	static String render(final RenderContext rc, final JsonNode b, final UIFormPaneWriter w)
	{
		if (rc.creating || rc.record == null)
		{
			return null;
		}
		final String perspective = ActionsBlock.perspective(rc);
		WorkflowEngine engine = null;
		final StringBuilder h = new StringBuilder("<div class=\"ldf-links\">");
		int i = -1;
		int n = 0;
		for (final JsonNode a : b.path("items"))
		{
			i++;
			if (!LText.hasText(a.get("label")) || !rc.xpath(a.path("cond").asText(""), true) || !WorkflowState.allows(rc, a.get("wf")) || !SessionState.allows(rc, a.get("session")))
			{
				continue;
			}
			final String workflow = a.path("workflow").asText("").trim();
			final String service = a.path("service").asText("").trim();
			String js;
			try
			{
				final UIHttpManagerComponent wc = rc.context.createWebComponentForSubSession();
				wc.selectInstanceOrOccurrence(rc.record);
				if (!workflow.isEmpty())
				{
					if (engine == null)
					{
						engine = WorkflowEngine.getFromRepository(rc.record.getHome().getRepository(), rc.context.getSession());
					}
					if (!engine.isUserAuthorizedToLaunch(PublishedProcessKey.forName(workflow)))
					{
						continue;
					}
					wc.setService(ServiceKey.forModuleServiceName(LayoutPaths.MODULE_NAME, LayoutPaths.ACTION_SERVICE));
					wc.setAdditionalParameter(LayoutPaths.GRID_BLOCK_PARAM, b.path("id").asText(""));
					wc.setAdditionalParameter(LayoutPaths.ACTION_PARAM, String.valueOf(i));
					if (perspective != null)
					{
						wc.setAdditionalParameter(LayoutPaths.PERSPECTIVE_PARAM, perspective);
					}
				}
				else if (!service.isEmpty())
				{
					final ServiceKey key = ServiceKey.parse(service);
					// 1.17.1: a service the user may not run on this record, or one EBX does not know, gives no button
					final com.orchestranetworks.service.ActionPermission permission = rc.context.getSession().getPermissions()
						.getNodeActionPermissionForService(key, rc.record.getSchemaNode(), rc.record);
					if (permission != null && permission.isHidden())
					{
						continue;
					}
					wc.setService(key);
				}
				else
				{
					continue;
				}
				js = w.buildButtonPreview(wc).getJavaScriptCommand();
			}
			catch (final RuntimeException ex)
			{
				// a publication or a service absent on this instance: no button
				LoggingCategory.getKernel().warn("[layout-designer] action button " + i + " not drawn: " + ex.getMessage());
				continue;
			}
			n++;
			h.append("<button type=\"button\" class=\"ldf-btn").append(n == 1 && b.path("firstSolid").asBoolean(true) ? " ldf-solid" : "")
				.append(Values.toneClass(a.path("tone").asText(""))).append("\" onclick=\"").append(ActionsBlock.esc(js)).append(";return false;\">")
				.append(ActionsBlock.esc(rc.text(a.get("label")))).append("</button>");
		}
		return n == 0 ? null : h.append("</div>").toString();
	}

	private static String perspective(final RenderContext rc)
	{
		try
		{
			final com.orchestranetworks.ui.perspective.Perspective p = rc.context.getCurrentPerspective();
			return p == null || p.isAdvanced() ? null : p.getName();
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private static String esc(final String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}
}
