package com.on.ebx.layout.designer;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.on.ebx.layout.runtime.Audience;
import com.on.ebx.layout.runtime.BlockTitles;
import com.on.ebx.layout.runtime.LText;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.on.ebx.layout.runtime.Personal;
import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.ui.UIButtonSpecNavigation;
import com.orchestranetworks.ui.UIButtonSpecSubmit;
import com.orchestranetworks.ui.selection.RecordEntitySelection;
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
 * "Arrange my form": the tabs and blocks the user sees on this table's form (their role's variant, what their role may
 * not see left out), to reorder, hide, fold or pin. The page is {@code www/personalize.js}; it posts the arrangement
 * through EBX's own form, and the row written is always the session's own user.
 */
public final class PersonalizeUserService implements UserService<RecordEntitySelection>
{
	static final String SUBMIT_ID = "ldpSubmit";

	private Adaptation record;
	private String notice;
	private boolean noticeError;
	private boolean saved;

	@Override
	public void setupObjectContext(final UserServiceSetupObjectContext<RecordEntitySelection> pContext, final UserServiceObjectContextBuilder pBuilder)
	{
		if (this.record == null)
		{
			this.record = pContext.getEntitySelection().getRecord();
		}
	}

	@Override
	public void setupDisplay(final UserServiceSetupDisplayContext<RecordEntitySelection> pContext, final UserServiceDisplayConfigurator pConfigurator)
	{
		final Session session = pContext.getSession();
		final Locale locale = session.getLocale();
		pConfigurator.setTitle(Messages.get(locale, "personal.title", this.record.getContainerTable().getTableNode().getLabel(locale)));
		pConfigurator.setDetectionOfLostModificationDisabled(true);
		final UIButtonSpecNavigation close = pConfigurator.newCloseButton();
		close.setAskBeforeLeavingModifiedForm(false);
		pConfigurator.setRightButtons(close);
		final UIButtonSpecSubmit submit = pConfigurator.newSubmitButton(Messages.get(locale, "button.apply"), new UserServiceEvent()
		{
			@Override
			public UserServiceEventOutcome processEvent(final UserServiceEventContext pEventContext)
			{
				PersonalizeUserService.this.handle(pEventContext);
				return null;
			}
		});
		submit.setId(PersonalizeUserService.SUBMIT_ID);
		pConfigurator.setLeftButtons(submit);
		pConfigurator.setContent(new UserServicePane()
		{
			@Override
			public void writePane(final UserServicePaneContext pPaneContext, final UserServicePaneWriter pWriter)
			{
				PersonalizeUserService.this.write(pWriter, session);
			}
		});
	}

	/** What this user's form holds, before their own arrangement. */
	private ObjectNode resolved(final Session pSession)
	{
		final JsonNode layout = LayoutStore.find(this.repository(), this.model(), this.table());
		if (layout == null || !Personal.allowed(layout))
		{
			return null;
		}
		final boolean seesAll = layout.path("adminSeesAll").asBoolean(true) && pSession.isUserInRole(Profile.ADMINISTRATOR);
		return Audience.resolve(layout, roles -> PersonalizeUserService.inAnyRole(pSession, roles), seesAll);
	}

	private void write(final UserServicePaneWriter pWriter, final Session pSession)
	{
		final Locale locale = pSession.getLocale();
		final ObjectNode boot = LayoutStore.json().createObjectNode();
		try
		{
			final ObjectNode resolved = this.resolved(pSession);
			if (resolved == null)
			{
				pWriter.add("<p style=\"padding:16px\">").add(DesignerUserService.esc(Messages.get(locale, "personal.off"))).add("</p>");
				return;
			}
			final String variant = resolved.path("variant").asText();
			final Locale def = this.repository().getDefaultLocale();
			final ArrayNode tabs = boot.putArray("tabs");
			final Map<String, List<JsonNode>> byTab = Audience.blocksByTab(resolved);
			for (final JsonNode t : resolved.path("tabs"))
			{
				final ObjectNode tab = tabs.addObject();
				final String id = t.path("id").asText();
				tab.put("id", id);
				tab.put("label", LText.pick(t.get("label"), locale, def));
				tab.put("locked", t.path("locked").asBoolean(false));
				final ArrayNode blocks = tab.putArray("blocks");
				for (final JsonNode b : byTab.get(id))
				{
					if (b.path("id").asText("").isEmpty())
					{
						continue;
					}
					final ObjectNode o = blocks.addObject();
					o.put("id", b.path("id").asText());
					String title = BlockTitles.of(b, locale, def, this.record.getContainerTable().getTableOccurrenceRootNode());
					if (title.isEmpty() && "panel".equals(b.path("type").asText()))
					{
						title = PersonalizeUserService.firstFields(b, locale, this.record);
					}
					if (title.isEmpty())
					{
						final String key = "personal.block." + b.path("type").asText();
						final String byType = Messages.get(locale, key);
						title = key.equals(byType) ? Messages.get(locale, "personal.block", b.path("type").asText()) : byType;
					}
					o.put("title", title);
					o.put("type", b.path("type").asText());
					o.put("locked", b.path("locked").asBoolean(false));
				}
			}
			final JsonNode prefs = LayoutStore.findPrefs(this.repository(), pSession.getUserReference().getUserId(), this.model(), this.table(), variant);
			boot.set("prefs", prefs == null ? LayoutStore.json().createObjectNode() : prefs);
			boot.put("variant", variant);
			boot.put("hasPrefs", prefs != null);
			boot.put("saved", this.saved);
			if (this.notice != null)
			{
				boot.put("notice", this.notice);
				boot.put("noticeError", this.noticeError);
			}
			final ObjectNode t = boot.putObject("t");
			for (final String k : new String[] { "intro", "tab", "first", "hideTab", "hide", "fold", "pin", "up", "down", "locked", "save", "reset", "resetConfirm",
				"roleNote", "mine", "noBlock", "savedReload" })
			{
				t.put(k, Messages.get(locale, "personal." + k));
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] personalization page not built: " + ex, ex);
			pWriter.add("<p style=\"padding:16px;color:#C73200\">").add(DesignerUserService.esc(ex.toString())).add("</p>");
			return;
		}
		this.notice = null;
		this.saved = false;
		final String base = "/" + LayoutPaths.MODULE_NAME + "/www/";
		pWriter.add("<style>#").add(PersonalizeUserService.SUBMIT_ID).add("{display:none!important}</style>");
		pWriter.add("<input type=\"hidden\" name=\"ldpAction\" id=\"ldpAction\" value=\"\">");
		pWriter.add("<input type=\"hidden\" name=\"ldpPrefs\" id=\"ldpPrefs\" value=\"\">");
		pWriter.add("<div id=\"ldp-app\"></div>");
		final String safe = boot.toString().replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
		pWriter.addJS_cr("window.LDP_BOOT=" + safe + ";");
		pWriter.addJS_cr("(function(){var s=document.createElement('script');s.src='" + base + "personalize.js?v=" + DesignerUserService.ASSET_VERSION
			+ "';document.body.appendChild(s);})();");
	}

	/** A panel without a title: named after its first fields. */
	private static String firstFields(final JsonNode pBlock, final Locale pLocale, final Adaptation pRecord)
	{
		final StringBuilder sb = new StringBuilder();
		int n = 0;
		for (final JsonNode f : pBlock.path("fields"))
		{
			final Path p = LayoutPaths.relative(f.path("p").asText());
			final com.orchestranetworks.schema.SchemaNode node = p == null ? null : pRecord.getContainerTable().getTableOccurrenceRootNode().getNode(p);
			if (node == null)
			{
				continue;
			}
			if (n == 2)
			{
				sb.append(", …");
				break;
			}
			sb.append(n == 0 ? "" : ", ").append(LText.hasText(f.get("label")) ? LText.pick(f.get("label"), pLocale, pLocale) : node.getLabel(pLocale));
			n++;
		}
		return sb.toString();
	}

	void handle(final UserServiceEventContext pContext)
	{
		final Session session = pContext.getSession();
		final Locale locale = session.getLocale();
		final String action = pContext.getParameter("ldpAction");
		String prefs = pContext.getParameter("ldpPrefs");
		try
		{
			final ObjectNode resolved = this.resolved(session);
			if (resolved == null)
			{
				this.notice(Messages.get(locale, "personal.off"), true);
				return;
			}
			if ("reset".equals(action))
			{
				prefs = null;
			}
			else
			{
				// only what this screen writes is kept
				final JsonNode in = LayoutStore.json().readTree(prefs == null ? "{}" : prefs);
				final ObjectNode clean = LayoutStore.json().createObjectNode();
				for (final String k : new String[] { "tabs", "hiddenTabs", "hidden", "collapsed", "pinned" })
				{
					final ArrayNode a = clean.putArray(k);
					for (final JsonNode x : in.path(k))
					{
						if (x.isTextual() && x.asText().length() < 100)
						{
							a.add(x.asText());
						}
					}
				}
				if (in.path("defaultTab").isTextual())
				{
					clean.put("defaultTab", in.path("defaultTab").asText());
				}
				prefs = clean.toString();
			}
			PrefsWriter.save(this.repository(), session, this.model(), this.table(), resolved.path("variant").asText(), prefs);
			this.saved = true;
			this.notice(Messages.get(locale, "reset".equals(action) ? "personal.resetDone" : "personal.saved"), false);
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] personal arrangement not saved: " + ex, ex);
			this.notice(Messages.get(locale, "personal.saveFailed", ex.getMessage()), true);
		}
	}

	static boolean inAnyRole(final Session pSession, final JsonNode pRoles)
	{
		for (final JsonNode r : pRoles)
		{
			final String name = r.asText("").trim();
			if (name.isEmpty())
			{
				continue;
			}
			try
			{
				final Profile p = "administrator".equalsIgnoreCase(name) || "Badministrator".equals(name) ? Profile.ADMINISTRATOR : Profile.forSpecificRole(name);
				if (pSession.isUserInRole(p))
				{
					return true;
				}
			}
			catch (final RuntimeException ex)
			{
				// an unknown role name
			}
		}
		return false;
	}

	private Repository repository()
	{
		return this.record.getHome().getRepository();
	}

	private SchemaLocation model()
	{
		return this.record.getContainer().getSchemaLocation();
	}

	private Path table()
	{
		return this.record.getContainerTable().getTablePath();
	}

	private void notice(final String pText, final boolean pError)
	{
		this.notice = pText;
		this.noticeError = pError;
	}

	@Override
	public void validate(final UserServiceValidateContext<RecordEntitySelection> pContext)
	{
		// handle() reports its own errors
	}

	@Override
	public UserServiceEventOutcome processEventOutcome(final UserServiceProcessEventOutcomeContext<RecordEntitySelection> pContext, final UserServiceEventOutcome pOutcome)
	{
		return pOutcome;
	}
}
