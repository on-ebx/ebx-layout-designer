package com.on.ebx.layout.runtime;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.AdaptationValue;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.UserReference;
import com.orchestranetworks.service.directory.DirectoryHandler;
import com.orchestranetworks.ui.UIHttpManagerComponent;
import com.orchestranetworks.ui.form.UIFormPaneWriter;
import com.orchestranetworks.workflow.ProcessInstance;
import com.orchestranetworks.workflow.ProcessInstanceKey;
import com.orchestranetworks.workflow.PublishedProcessKey;
import com.orchestranetworks.workflow.UserTask;
import com.orchestranetworks.workflow.WorkflowEngine;

/**
 * Blocks for master data work, all through documented APIs: the values each merged source gave a golden record (Match
 * and Merge add-on, called by reflection so that a form works without it), the workflows running on the record, the
 * translations of the record, and what a record of a child dataset inherits.
 */
final class MdmBlocks
{
	private MdmBlocks()
	{
	}

	private static String esc(final String s)
	{
		return LayoutPane.esc(s);
	}

	private static String t(final String pKey, final RenderContext rc)
	{
		return RuntimeText.get(pKey, rc.locale);
	}

	private static String note(final String pText)
	{
		return "<div class=\"ldf-more\">" + MdmBlocks.esc(pText) + "</div>";
	}

	private static List<String> paths(final RenderContext rc, final JsonNode pList)
	{
		final List<String> out = new ArrayList<String>();
		for (final JsonNode p : pList)
		{
			if (rc.node(p.asText()) != null)
			{
				out.add(p.asText());
			}
		}
		if (out.isEmpty())
		{
			final List<String> all = new ArrayList<String>();
			MoreBlocks.terminals(rc.root, all);
			for (final String p : all)
			{
				if (!p.contains("Linked_Field_"))
				{
					out.add(p);
				}
			}
		}
		out.removeIf(p -> !rc.canRead(p));
		return out;
	}

	// ------------------------------------------------------------------------------------------------ values by source

	/**
	 * The values each merged record gave this golden record, field by field, from the group of the Match and Merge add-on
	 * ({@code MetadataHelper.getState/getGroupId}, {@code GroupOperations.getGroup}, {@code Group.getMatchedRecords}). A source
	 * value equal to the golden's is marked as kept. {@code onlyDifferent} (true) leaves out the fields where every source agrees.
	 */
	static String sourceValues(final RenderContext rc, final JsonNode b)
	{
		if (rc.creating || rc.record == null)
		{
			return MdmBlocks.note(MdmBlocks.t("afterCreate", rc));
		}
		final Adaptation me = rc.record;
		final List<Adaptation> members = new ArrayList<Adaptation>();
		try
		{
			final Class<?> helper = Class.forName("com.orchestranetworks.addon.mame.common.util.metadata.MetadataHelper");
			final String state = String.valueOf(helper.getMethod("getState", Adaptation.class).invoke(null, me));
			if ("MERGED".equals(state))
			{
				final Adaptation golden = (Adaptation) helper.getMethod("getTargetRecord", Adaptation.class).invoke(null, me);
				return MdmBlocks.note(MdmBlocks.t("sv.merged", rc)) + (golden == null ? ""
					: "<div><a class=\"ldf-btn\" href=\"" + MdmBlocks.esc(MoreBlocks.recordUrl(rc, golden)) + "\" target=\"_blank\" rel=\"noopener\">" + MdmBlocks.esc(golden.getLabel(rc.locale)) + "</a></div>");
			}
			final Object group = helper.getMethod("getGroupId", Adaptation.class).invoke(null, me);
			if (group == null)
			{
				return MdmBlocks.note(MdmBlocks.t("sv.none", rc));
			}
			final Class<?> factory = Class.forName("com.orchestranetworks.addon.mame.service.OperationsFactory");
			final Object ops = factory.getMethod("getGroupOperations", com.orchestranetworks.service.Session.class).invoke(null, rc.context.getSession());
			final Object g = ops.getClass().getMethod("getGroup", AdaptationTable.class, BigDecimal.class).invoke(ops, rc.context.getCurrentTable(), group);
			final Object it = g.getClass().getMethod("getMatchedRecords").invoke(g);
			try
			{
				@SuppressWarnings("unchecked")
				final Iterator<Adaptation> i = (Iterator<Adaptation>) it;
				while (i.hasNext())
				{
					final Adaptation r = i.next();
					if (!r.getOccurrencePrimaryKey().equals(me.getOccurrencePrimaryKey()))
					{
						members.add(r);
					}
				}
			}
			finally
			{
				if (it instanceof AutoCloseable)
				{
					((AutoCloseable) it).close();
				}
			}
		}
		catch (final ClassNotFoundException | NoClassDefFoundError ex)
		{
			return MdmBlocks.note(MdmBlocks.t("sv.noAddon", rc));
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().info("[layout-designer] values by source of " + me.toXPathPredicateString() + ": " + ex);
			return MdmBlocks.note(MdmBlocks.t("sv.none", rc));
		}
		if (members.isEmpty())
		{
			return MdmBlocks.note(MdmBlocks.t("sv.none", rc));
		}
		final int max = Math.max(1, Math.min(10, b.path("max").asInt(6)));
		final List<Adaptation> shown = members.subList(0, Math.min(max, members.size()));
		final String source = b.path("source").asText("");
		final boolean onlyDiff = b.path("onlyDifferent").asBoolean(true);
		final StringBuilder h = new StringBuilder();
		h.append("<div class=\"ldf-more\">").append(MdmBlocks.esc(RuntimeText.get("sv.count", rc.locale, Integer.valueOf(members.size())))).append("</div>");
		h.append("<div class=\"ldf-sv-wrap\"><table class=\"ldf-sv\"><thead><tr><th></th><th class=\"ldf-sv-g\">").append(MdmBlocks.esc(MdmBlocks.t("sv.golden", rc))).append("</th>");
		for (final Adaptation m : shown)
		{
			final String src = source.isEmpty() ? "" : Values.display(m, source, rc.locale);
			h.append("<th><a href=\"").append(MdmBlocks.esc(MoreBlocks.recordUrl(rc, m))).append("\" target=\"_blank\" rel=\"noopener\">").append(MdmBlocks.esc(src.isEmpty() ? m.getLabel(rc.locale) : src))
				.append("</a>").append(src.isEmpty() ? "" : "<small>" + MdmBlocks.esc(m.getLabel(rc.locale)) + "</small>").append("</th>");
		}
		h.append("</tr></thead><tbody>");
		int rows = 0;
		// the primary key differs from one record to the other by nature: not compared
		final Set<String> key = new LinkedHashSet<String>();
		for (final Path k : rc.context.getCurrentTable().getPrimaryKeySpec())
		{
			key.add(LayoutPaths.stored(k));
		}
		for (final String p : MdmBlocks.paths(rc, b.path("fields")))
		{
			if (key.contains(p))
			{
				continue;
			}
			final String gv = Values.display(me, p, rc.locale);
			final List<String> vs = new ArrayList<String>();
			boolean differ = false;
			for (final Adaptation m : shown)
			{
				final String v = Values.display(m, p, rc.locale);
				vs.add(v);
				differ |= !v.equals(gv);
			}
			if (onlyDiff && !differ)
			{
				continue;
			}
			rows++;
			h.append("<tr><th>").append(MdmBlocks.esc(rc.label(p))).append("</th><td class=\"ldf-sv-g\">").append(gv.isEmpty() ? "<i>—</i>" : MdmBlocks.esc(gv)).append("</td>");
			for (final String v : vs)
			{
				final boolean kept = !gv.isEmpty() && v.equals(gv);
				h.append("<td").append(kept ? " class=\"ldf-sv-k\" title=\"" + MdmBlocks.esc(MdmBlocks.t("sv.kept", rc)) + "\"" : "").append(">").append(v.isEmpty() ? "<i>—</i>" : MdmBlocks.esc(v)).append("</td>");
			}
			h.append("</tr>");
		}
		h.append("</tbody></table></div>");
		if (rows == 0)
		{
			return MdmBlocks.note(MdmBlocks.t("sv.allAgree", rc));
		}
		return h.toString();
	}

	// ------------------------------------------------------------------------------------------------ workflows on the record

	/**
	 * The running workflows whose data context holds this record, through
	 * {@code WorkflowEngine.getProcessInstanceKeysForDataContextVariableIncludingHistory} on every publication (an instance may run on
	 * an earlier version of it), for each variable named in
	 * {@code variables} (comma separated) and the record's XPath or primary key; their current work items open in EBX's pop-up.
	 */
	static String workflowTasks(final RenderContext rc, final JsonNode b, final UIFormPaneWriter w)
	{
		if (rc.creating || rc.record == null)
		{
			return MdmBlocks.note(MdmBlocks.t("afterCreate", rc));
		}
		final List<String> vars = new ArrayList<String>();
		for (final String v : b.path("variables").asText("").split("[,;\\s]+"))
		{
			if (!v.isEmpty())
			{
				vars.add(v);
			}
		}
		if (vars.isEmpty())
		{
			return MdmBlocks.note(MdmBlocks.t("setupBlock", rc));
		}
		final Adaptation me = rc.record;
		final Set<String> values = new LinkedHashSet<String>();
		// the variable holds the record's XPath (written with single or double quotes) or its primary key
		final String xpath = me.toXPathExpression();
		final String table = me.getContainerTable().getTablePath().format();
		final String predicate = me.toXPathPredicateString();
		for (final String x : new String[] { xpath, table + "[" + predicate + "]" })
		{
			values.add(x);
			values.add(x.replace('"', '\''));
			values.add(x.replace('\'', '"'));
		}
		values.add(me.getOccurrencePrimaryKey().format());
		final WorkflowEngine engine;
		try
		{
			engine = WorkflowEngine.getFromRepository(me.getHome().getRepository(), rc.context.getSession());
		}
		catch (final RuntimeException ex)
		{
			return MdmBlocks.note(MdmBlocks.t("wf.none", rc));
		}
		final Map<String, ProcessInstance> found = new LinkedHashMap<String, ProcessInstance>();
		for (final PublishedProcessKey pk : engine.getPublishedKeys())
		{
			for (final String v : vars)
			{
				for (final String value : values)
				{
					try
					{
						for (final ProcessInstanceKey k : engine.getProcessInstanceKeysForDataContextVariableIncludingHistory(pk, v, value))
						{
							if (!found.containsKey(k.format()))
							{
								final ProcessInstance pi = engine.getProcessInstance(k);
								if (pi != null && !pi.isCompleted())
								{
									found.put(k.format(), pi);
								}
							}
						}
					}
					catch (final RuntimeException ex)
					{
						// the variable is not declared in this publication
					}
				}
			}
		}
		if (found.isEmpty())
		{
			return MdmBlocks.note(MdmBlocks.t("wf.none", rc));
		}
		final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, rc.locale);
		final DirectoryHandler dir = DirectoryHandler.getInstance(me.getHome().getRepository());
		final StringBuilder h = new StringBuilder("<div class=\"ldf-wf\">");
		for (final ProcessInstance pi : found.values())
		{
			h.append("<div class=\"ldf-wf-i\"><div class=\"ldf-wf-h\"><b>").append(MdmBlocks.esc(MdmBlocks.text(pi.getLabel(), rc.locale))).append("</b><span class=\"ldf-more\">")
				.append(MdmBlocks.esc(RuntimeText.get("wf.started", rc.locale, df.format(pi.getCreationDate())))).append(pi.getCreator() == null ? "" : " · " + MdmBlocks.esc(MdmBlocks.user(dir, pi.getCreator(), rc.locale)))
				.append("</span>").append(pi.isInError() ? "<span class=\"ldf-badge ldf-t-bad\">" + MdmBlocks.esc(MdmBlocks.t("wf.error", rc)) + "</span>" : "").append("</div>");
			final List<UserTask.WorkItem> items = pi.getWorkItems();
			if (items == null || items.isEmpty())
			{
				h.append("<div class=\"ldf-more\">").append(MdmBlocks.esc(MdmBlocks.t("wf.noTask", rc))).append("</div>");
			}
			else
			{
				for (final UserTask.WorkItem wi : items)
				{
					final String who = wi.getUserReference() != null ? MdmBlocks.user(dir, wi.getUserReference(), rc.locale) : MdmBlocks.offered(dir, wi.getOfferedToProfiles(), rc.locale);
					h.append("<div class=\"ldf-wf-t\"><span class=\"ldf-wf-n\">").append(MdmBlocks.esc(MdmBlocks.text(wi.getLabel(), rc.locale))).append("</span><span class=\"ldf-more\">")
						.append(MdmBlocks.esc(RuntimeText.get(wi.getUserReference() != null ? "wf.allocated" : "wf.offered", rc.locale, who)))
						.append(wi.getStartDate() == null ? "" : " · " + MdmBlocks.esc(RuntimeText.get("wf.since", rc.locale, df.format(wi.getStartDate()))))
						.append(wi.hasReachedDeadline() ? " · <b class=\"ldf-wf-late\">" + MdmBlocks.esc(MdmBlocks.t("wf.late", rc)) + "</b>" : "").append("</span>");
					// the user's own task opens in the inbox; anyone else's in the monitoring view
					final boolean mine = MdmBlocks.isMine(rc, wi);
					final String js = MdmBlocks.openWorkItem(rc, w, wi, mine);
					if (js != null)
					{
						h.append("<button type=\"button\" class=\"ldf-btn\" onclick=\"").append(MdmBlocks.esc(js)).append(";return false\">")
							.append(MdmBlocks.esc(MdmBlocks.t(mine ? "wf.open" : "wf.monitor", rc))).append("</button>");
					}
					h.append("</div>");
				}
			}
			h.append("</div>");
		}
		return h.append("</div>").toString();
	}

	private static boolean isMine(final RenderContext rc, final UserTask.WorkItem pItem)
	{
		final com.orchestranetworks.service.Session session = rc.context.getSession();
		if (pItem.getUserReference() != null)
		{
			return pItem.getUserReference().equals(session.getUserReference());
		}
		if (pItem.getOfferedToProfiles() != null)
		{
			for (final Profile p : pItem.getOfferedToProfiles())
			{
				try
				{
					if (session.isUserInRole(p))
					{
						return true;
					}
				}
				catch (final RuntimeException ex)
				{
					// not a role
				}
			}
		}
		return false;
	}

	private static String openWorkItem(final RenderContext rc, final UIFormPaneWriter w, final UserTask.WorkItem pItem, final boolean pMine)
	{
		try
		{
			final UIHttpManagerComponent wc = rc.context.createWebComponentForSubSession();
			if (pMine)
			{
				wc.selectWorkItem(false, pItem.getWorkItemKey());
			}
			else
			{
				wc.selectWorkItemInMonitoring(pItem.getWorkItemKey());
			}
			return w.buildButtonPreview(wc).getJavaScriptCommand();
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private static String text(final com.onwbp.base.text.UserMessage pMessage, final Locale pLocale)
	{
		if (pMessage == null)
		{
			return "";
		}
		final String s = pMessage.formatMessage(pLocale);
		return s == null ? "" : s;
	}

	private static String user(final DirectoryHandler pDir, final UserReference pUser, final Locale pLocale)
	{
		try
		{
			final String s = pDir.displayUser(pUser, pLocale);
			return s == null || s.trim().isEmpty() ? pUser.getUserId() : s;
		}
		catch (final RuntimeException ex)
		{
			return pUser.getUserId();
		}
	}

	private static String offered(final DirectoryHandler pDir, final List<Profile> pProfiles, final Locale pLocale)
	{
		if (pProfiles == null || pProfiles.isEmpty())
		{
			return "—";
		}
		final List<String> names = new ArrayList<String>();
		for (final Profile p : pProfiles)
		{
			try
			{
				names.add(pDir.displayProfile(p, pLocale));
			}
			catch (final RuntimeException ex)
			{
				names.add(p.format());
			}
		}
		return String.join(", ", names);
	}

	// ------------------------------------------------------------------------------------------------ translations

	/**
	 * How complete each language is. Two shapes: {@code languages} = [{code, fields[]}] for one field per language in the
	 * record, or an association {@code a} whose rows hold a language field {@code lang} and the translated {@code fields},
	 * with the {@code expected} languages (comma separated) to report a missing one.
	 */
	static String translations(final RenderContext rc, final JsonNode b)
	{
		if (rc.creating || rc.record == null)
		{
			return MdmBlocks.note(MdmBlocks.t("afterCreate", rc));
		}
		// language → {label of each expected field → filled}
		final Map<String, Map<String, Boolean>> langs = new LinkedHashMap<String, Map<String, Boolean>>();
		final String a = b.path("a").asText("");
		if (!a.isEmpty())
		{
			final String lang = b.path("lang").asText("");
			final List<String> fields = new ArrayList<String>();
			for (final JsonNode p : b.path("fields"))
			{
				fields.add(p.asText());
			}
			if (lang.isEmpty() || fields.isEmpty())
			{
				return MdmBlocks.note(MdmBlocks.t("setupBlock", rc));
			}
			for (final String code : b.path("expected").asText("").split("[,;\\s]+"))
			{
				if (!code.isEmpty())
				{
					langs.put(code.toUpperCase(Locale.ROOT), new LinkedHashMap<String, Boolean>());
				}
			}
			for (final Adaptation r : rc.rows(a, 200))
			{
				final String code = Values.key(Values.raw(r, lang)).toUpperCase(Locale.ROOT);
				if (code.isEmpty())
				{
					continue;
				}
				final Map<String, Boolean> m = langs.computeIfAbsent(code, x -> new LinkedHashMap<String, Boolean>());
				for (final String f : fields)
				{
					final SchemaNodeLabel l = new SchemaNodeLabel(r, f, rc.locale);
					m.put(l.label, Boolean.valueOf(m.getOrDefault(l.label, Boolean.FALSE).booleanValue() || Values.filled(Values.raw(r, f))));
				}
			}
			// an expected language without any row: every field is missing
			for (final Map.Entry<String, Map<String, Boolean>> e : langs.entrySet())
			{
				if (e.getValue().isEmpty())
				{
					for (final String f : fields)
					{
						e.getValue().put(MdmBlocks.label(rc, a, f), Boolean.FALSE);
					}
				}
			}
		}
		else
		{
			for (final JsonNode l : b.path("languages"))
			{
				final String code = l.path("code").asText("").toUpperCase(Locale.ROOT);
				if (code.isEmpty())
				{
					continue;
				}
				final Map<String, Boolean> m = langs.computeIfAbsent(code, x -> new LinkedHashMap<String, Boolean>());
				for (final JsonNode p : l.path("fields"))
				{
					if (rc.node(p.asText()) != null)
					{
						m.put(rc.label(p.asText()), Boolean.valueOf(Values.filled(rc.raw(p.asText()))));
					}
				}
			}
		}
		if (langs.isEmpty())
		{
			return MdmBlocks.note(MdmBlocks.t("setupBlock", rc));
		}
		final StringBuilder h = new StringBuilder("<div class=\"ldf-tr\">");
		for (final Map.Entry<String, Map<String, Boolean>> e : langs.entrySet())
		{
			final int total = e.getValue().size();
			int filled = 0;
			final List<String> missing = new ArrayList<String>();
			for (final Map.Entry<String, Boolean> f : e.getValue().entrySet())
			{
				if (f.getValue().booleanValue())
				{
					filled++;
				}
				else
				{
					missing.add(f.getKey());
				}
			}
			final int pct = total == 0 ? 0 : Math.round(100f * filled / total);
			final String tone = pct >= 100 ? "ldf-t-ok" : pct >= 50 ? "ldf-t-warn" : "ldf-t-bad";
			final String name = new Locale(e.getKey().toLowerCase(Locale.ROOT)).getDisplayLanguage(rc.locale);
			h.append("<div class=\"ldf-tr-l\"><span class=\"ldf-tr-c\">").append(MdmBlocks.esc(e.getKey())).append("</span><span class=\"ldf-tr-n\">")
				.append(MdmBlocks.esc(name.isEmpty() || name.equalsIgnoreCase(e.getKey()) ? "" : name)).append("</span><span class=\"ldf-bar ").append(tone).append("\"><i style=\"width:").append(pct)
				.append("%\"></i></span><b class=\"").append(tone).append("\">").append(pct).append(" %</b><span class=\"ldf-tr-m\">")
				.append(missing.isEmpty() ? MdmBlocks.esc(MdmBlocks.t("tr.complete", rc)) : MdmBlocks.esc(MdmBlocks.t("tr.missing", rc) + " " + String.join(", ", missing))).append("</span></div>");
		}
		return h.append("</div>").toString();
	}

	private static String label(final RenderContext rc, final String pAssoc, final String pField)
	{
		final List<Adaptation> one = rc.rows(pAssoc, 1);
		return one.isEmpty() ? pField.replaceAll("^.*/", "") : new SchemaNodeLabel(one.get(0), pField, rc.locale).label;
	}

	/** The label of a field of any row. */
	private static final class SchemaNodeLabel
	{
		final String label;

		SchemaNodeLabel(final Adaptation pRow, final String pPath, final Locale pLocale)
		{
			final com.orchestranetworks.schema.SchemaNode n = Values.node(pRow, pPath);
			this.label = n == null ? pPath.replaceAll("^.*/", "") : n.getLabel(pLocale);
		}
	}

	// ------------------------------------------------------------------------------------------------ inheritance

	/**
	 * In a child dataset: the fields of the record that follow the parent dataset and those changed here
	 * ({@code Adaptation.getValueWithoutResolution(path) == AdaptationValue.INHERIT_VALUE}), with the parent's value next to
	 * each local one. A record the parent does not have is created here.
	 */
	static String inheritance(final RenderContext rc, final JsonNode b)
	{
		if (rc.creating || rc.record == null)
		{
			return MdmBlocks.note(MdmBlocks.t("afterCreate", rc));
		}
		final Adaptation me = rc.record;
		final Adaptation dataset = me.getContainer();
		final Adaptation parentDataset = dataset.getParent();
		if (parentDataset == null)
		{
			return MdmBlocks.note(MdmBlocks.t("in.root", rc));
		}
		final String parentName = parentDataset.getLabelOrName(rc.locale);
		Adaptation inParent = null;
		try
		{
			final AdaptationTable pt = parentDataset.getTable(me.getContainerTable().getTablePath());
			inParent = pt == null ? null : pt.lookupAdaptationByPrimaryKey(me.getOccurrencePrimaryKey());
		}
		catch (final RuntimeException ex)
		{
			inParent = null;
		}
		if (inParent == null)
		{
			return "<div class=\"ldf-val\"><span class=\"ldf-badge ldf-t-info\">" + MdmBlocks.esc(MdmBlocks.t("in.local", rc)) + "</span><span class=\"ldf-more\">"
				+ MdmBlocks.esc(RuntimeText.get("in.notInParent", rc.locale, parentName)) + "</span></div>";
		}
		final List<String> inherited = new ArrayList<String>();
		final StringBuilder local = new StringBuilder();
		int changed = 0;
		for (final String p : MdmBlocks.paths(rc, b.path("fields")))
		{
			final Path path = LayoutPaths.relative(p);
			final Object own;
			try
			{
				own = me.getValueWithoutResolution(path);
			}
			catch (final RuntimeException ex)
			{
				continue;
			}
			if (own == AdaptationValue.INHERIT_VALUE)
			{
				inherited.add(rc.label(p));
				continue;
			}
			changed++;
			local.append("<tr><th>").append(MdmBlocks.esc(rc.label(p))).append("</th><td class=\"ldf-in-p\">").append(MdmBlocks.esc(MdmBlocks.or(Values.display(inParent, p, rc.locale))))
				.append("</td><td class=\"ldf-in-a\">→</td><td class=\"ldf-in-h\">").append(MdmBlocks.esc(MdmBlocks.or(Values.display(me, p, rc.locale)))).append("</td></tr>");
		}
		final StringBuilder h = new StringBuilder("<div class=\"ldf-val\">");
		h.append("<span class=\"ldf-badge ").append(changed == 0 ? "ldf-t-ok" : "ldf-t-warn").append("\">")
			.append(MdmBlocks.esc(changed == 0 ? MdmBlocks.t("in.allInherited", rc) : RuntimeText.get("in.changed", rc.locale, Integer.valueOf(changed)))).append("</span><span class=\"ldf-more\">")
			.append(MdmBlocks.esc(RuntimeText.get("in.from", rc.locale, parentName))).append("</span></div>");
		if (changed > 0)
		{
			h.append("<table class=\"ldf-ch ldf-in\"><thead><tr><th></th><th>").append(MdmBlocks.esc(parentName)).append("</th><th></th><th>").append(MdmBlocks.esc(dataset.getLabelOrName(rc.locale)))
				.append("</th></tr></thead><tbody>").append(local).append("</tbody></table>");
		}
		if (!inherited.isEmpty() && b.path("listInherited").asBoolean(true))
		{
			h.append("<div class=\"ldf-more ldf-in-list\">").append(MdmBlocks.esc(RuntimeText.get("in.inheritedList", rc.locale, Integer.valueOf(inherited.size())))).append(" ")
				.append(MdmBlocks.esc(String.join(", ", inherited))).append("</div>");
		}
		return h.toString();
	}

	private static String or(final String s)
	{
		return s == null || s.isEmpty() ? "—" : s;
	}
}
