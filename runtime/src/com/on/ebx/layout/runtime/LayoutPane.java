package com.on.ebx.layout.runtime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.ui.UIFormLabelSpec;
import com.orchestranetworks.ui.form.UIFormContext;
import com.orchestranetworks.ui.form.UIFormPane;
import com.orchestranetworks.ui.form.UIFormPaneWriter;
import com.orchestranetworks.ui.form.UIFormRow;
import com.orchestranetworks.ui.form.widget.UIComboBox;
import com.orchestranetworks.ui.form.widget.UITextBox;
import com.orchestranetworks.ui.form.widget.UIWidget;

/**
 * One tab of a designed form: its rows (one or two columns) and their blocks. Fields are EBX's own form rows and
 * widgets, so validation, saving, permissions and the history stay EBX's; the blocks around them are HTML.
 */
final class LayoutPane implements UIFormPane
{
	private final RenderContext rc;
	private final JsonNode tab;
	private final int tabIndex;

	LayoutPane(final RenderContext pContext, final JsonNode pTab, final int pTabIndex)
	{
		this.rc = pContext;
		this.tab = pTab;
		this.tabIndex = pTabIndex;
	}

	@Override
	public void writePane(final UIFormPaneWriter pWriter, final UIFormContext pContext)
	{
		pWriter.add("<style>").add(Css.STYLE).add("</style><div class=\"ldf\">");
		final JsonNode rows = this.tab.path("rows");
		for (int r = 0; r < rows.size(); r++)
		{
			final JsonNode row = rows.get(r);
			final JsonNode cols = row.path("blocks");
			final boolean two = row.path("columns").asInt(1) == 2 && cols.size() == 2;
			final String ratio = row.path("ratio").asText("11");
			pWriter.add("<div class=\"ldf-row").add(two ? " ldf-c2" + ("21".equals(ratio) ? " ldf-r21" : "12".equals(ratio) ? " ldf-r12" : "") : "").add("\">");
			for (int c = 0; c < cols.size(); c++)
			{
				pWriter.add("<div class=\"ldf-col\">");
				final JsonNode col = cols.get(c);
				for (int k = 0; k < col.size(); k++)
				{
					final JsonNode block = col.get(k);
					try
					{
						this.block(pWriter, block, RenderContext.blockId(block, this.tabIndex, r, c, k));
					}
					catch (final RuntimeException ex)
					{
						LoggingCategory.getKernel().error("[layout-designer] block " + block.path("type").asText() + " not drawn: " + ex, ex);
					}
				}
				pWriter.add("</div>");
			}
			pWriter.add("</div>");
		}
		pWriter.add("</div>");
		pWriter.addJS_cr(Css.GO_JS);
	}

	// ------------------------------------------------------------------------------------------------ blocks

	private void block(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		if (this.rc.creating && b.path("hideCreate").asBoolean(false))
		{
			return;
		}
		if (!this.visible(b.path("cond").asText("")))
		{
			return;
		}
		final String type = b.path("type").asText();
		if ("panel".equals(type))
		{
			this.panel(w, b, id);
		}
		else if ("hero".equals(type))
		{
			this.hero(w, b, id);
		}
		else if ("facts".equals(type))
		{
			this.facts(w, b, id);
		}
		else if ("validation".equals(type))
		{
			this.validation(w, b, id);
		}
		else if ("grid".equals(type))
		{
			this.grid(w, b, id);
		}
		else if ("map".equals(type))
		{
			this.map(w, b, id);
		}
		else if ("tree".equals(type))
		{
			this.tree(w, b, id);
		}
		else if ("duplicates".equals(type))
		{
			this.duplicates(w, b, id);
		}
		else if ("note".equals(type))
		{
			w.add("<div class=\"ldf-note\" id=\"").add(RenderContext.blockAnchor(id)).add("\">").add(LayoutPane.esc(this.rc.text(b.get("text")))).add("</div>");
		}
		else if ("custom".equals(type))
		{
			this.custom(w, b, id);
		}
	}

	/** An XPath predicate on the record; true when there is none, when the record is new or when it cannot run. */
	private boolean visible(final String pCondition)
	{
		if (pCondition.trim().isEmpty() || this.rc.record == null)
		{
			return true;
		}
		try
		{
			final RequestResult result = this.rc.record.getContainerTable()
				.createRequestResult(this.rc.record.toXPathPredicateString() + " and (" + pCondition + ")");
			try
			{
				return !result.isEmpty();
			}
			finally
			{
				result.close();
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] condition '" + pCondition + "' not evaluated, block shown: " + ex.getMessage());
			return true;
		}
	}

	private void startPanel(final UIFormPaneWriter w, final JsonNode b, final String pId, final String pTitle, final String pCount, final String pExtra)
	{
		w.add("<section class=\"ldf-panel\" id=\"").add(RenderContext.blockAnchor(pId)).add("\">");
		final String note = this.rc.text(b.get("note"));
		if (!pTitle.isEmpty() || !note.isEmpty() || pExtra != null)
		{
			w.add("<header><div>");
			if (!pTitle.isEmpty())
			{
				w.add("<h3>").add(LayoutPane.esc(pTitle));
				if (pCount != null)
				{
					w.add(" <span class=\"ldf-n\">· ").add(pCount).add("</span>");
				}
				w.add("</h3>");
			}
			if (!note.isEmpty())
			{
				w.add("<p>").add(LayoutPane.esc(note)).add("</p>");
			}
			w.add("</div>");
			if (pExtra != null)
			{
				w.add(pExtra);
			}
			w.add("</header>");
		}
	}

	// ------------------------------------------------------------------------------------------------ panel

	private void panel(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		final JsonNode fields = b.path("fields");
		final boolean allReadOnly = b.path("ro").asBoolean(false);
		this.startPanel(w, b, id, this.rc.text(b.get("title")), null, null);
		boolean table = false;
		for (int i = 0; i < fields.size(); i++)
		{
			final JsonNode f = fields.get(i);
			final String stored = f.path("p").asText();
			final SchemaNode node = this.rc.node(stored);
			if (node == null)
			{
				continue;
			}
			final Path path = LayoutPaths.relative(stored);
			final String anchor = RenderContext.fieldAnchor(id, i);
			final String kind = f.path("w").asText("");
			final boolean ro = allReadOnly || f.path("ro").asBoolean(false) || node.isValueFunction();
			final String label = LText.hasText(f.get("label")) ? this.rc.text(f.get("label")) : node.getLabel(this.rc.locale);
			final boolean displayOnly = ro && ("badge".equals(kind) || "fig".equals(kind) || "link".equals(kind) || "mono".equals(kind));
			if (displayOnly)
			{
				if (table)
				{
					w.endTableFormRow();
					table = false;
				}
				w.add("<div class=\"ldf-ro\" id=\"").add(anchor).add("\"><span class=\"ldf-l\">").add(LayoutPane.esc(label)).add("</span>");
				w.add(this.displayHtml(stored, kind)).add("</div>");
				continue;
			}
			if (f.path("wide").asBoolean(false))
			{
				if (table)
				{
					w.endTableFormRow();
					table = false;
				}
				w.add("<div id=\"").add(anchor).add("\"><div class=\"ldf-wl\">").add(LayoutPane.esc(label)).add("</div>");
				w.addWidget(this.widget(w, path, node, kind, ro));
				w.add("</div>");
				continue;
			}
			if (!table)
			{
				w.startTableFormRow();
				table = true;
			}
			final UIFormRow row = w.newFormRow(path);
			row.setRowId(anchor);
			if (LText.hasText(f.get("label")))
			{
				row.setLabel(new UIFormLabelSpec(label));
			}
			w.addFormRow(row, this.widget(w, path, node, kind, ro));
		}
		if (table)
		{
			w.endTableFormRow();
		}
		w.add("</section>");
	}

	private UIWidget widget(final UIFormPaneWriter w, final Path path, final SchemaNode node, final String kind, final boolean ro)
	{
		UIWidget widget;
		try
		{
			if ("nocreate".equals(kind))
			{
				final UIComboBox combo = w.newComboBox(path);
				combo.setCreateButtonDisplayable(false);
				widget = combo;
			}
			else if ("select".equals(kind))
			{
				widget = w.newComboBox(path);
			}
			else if ("radio".equals(kind) || "yesno".equals(kind))
			{
				widget = w.newRadioButtonGroup(path);
			}
			else if ("check".equals(kind))
			{
				widget = w.newCheckBox(path);
			}
			else if ("line".equals(kind) || "area".equals(kind))
			{
				final UITextBox box = w.newTextBox(path);
				box.setMultiLine("area".equals(kind));
				widget = box;
			}
			else
			{
				widget = w.newBestMatching(path);
			}
		}
		catch (final RuntimeException ex)
		{
			widget = w.newBestMatching(path);
		}
		if (ro)
		{
			widget.setEditorDisabled(true);
		}
		return widget;
	}

	private String displayHtml(final String pStored, final String pKind)
	{
		final String v = this.rc.display(pStored);
		if (v.isEmpty())
		{
			return "<span class=\"ldf-v ldf-empty\">—</span>";
		}
		if ("badge".equals(pKind))
		{
			return "<span class=\"ldf-v\"><span class=\"ldf-badge\">" + LayoutPane.esc(v) + "</span></span>";
		}
		if ("fig".equals(pKind))
		{
			return "<span class=\"ldf-v ldf-fig\">" + LayoutPane.esc(v) + "</span>";
		}
		if ("link".equals(pKind))
		{
			final String href = v.matches("(?i)^https?://.*") ? v : v.contains("@") && !v.contains(" ") ? "mailto:" + v : "https://" + v;
			return "<span class=\"ldf-v\"><a href=\"" + LayoutPane.esc(href) + "\" target=\"_blank\" rel=\"noopener\">" + LayoutPane.esc(v) + "</a></span>";
		}
		return "<span class=\"ldf-v ldf-mono\">" + LayoutPane.esc(v) + "</span>";
	}

	// ------------------------------------------------------------------------------------------------ hero, facts

	private void hero(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		final Locale locale = this.rc.locale;
		final String title = this.rc.creating ? RuntimeText.get("newRecord", locale) : this.rc.display(b.path("title").asText());
		final String initials = title.trim().isEmpty() ? "?" : title.trim().substring(0, Math.min(3, title.trim().length()));
		w.add("<div class=\"ldf-hero\" id=\"").add(RenderContext.blockAnchor(id)).add("\"><div class=\"ldf-hero-l\"><div class=\"ldf-logo\">").add(LayoutPane.esc(initials)).add("</div><div style=\"min-width:0\">");
		w.add("<div class=\"ldf-ey\">").add(LayoutPane.esc(this.rc.context.getCurrentTable().getTableNode().getLabel(locale)));
		if (this.rc.record != null)
		{
			w.add(" · #").add(LayoutPane.esc(this.rc.record.getOccurrencePrimaryKey().format()));
		}
		w.add("</div><h2>").add(LayoutPane.esc(title)).add("</h2>");
		if (!this.rc.creating)
		{
			final List<String> sub = new ArrayList<String>();
			for (final JsonNode p : b.path("sub"))
			{
				final String v = this.rc.display(p.asText());
				if (!v.isEmpty())
				{
					sub.add(v);
				}
			}
			w.add("<div class=\"ldf-sub\">").add(LayoutPane.esc(String.join(" · ", sub))).add("</div><div class=\"ldf-chips\">");
			for (final JsonNode p : b.path("chips"))
			{
				final String v = this.rc.display(p.asText());
				if (!v.isEmpty())
				{
					w.add("<span class=\"ldf-badge\">").add(LayoutPane.esc(v)).add("</span>");
				}
			}
			w.add("</div>");
		}
		w.add("</div></div><div class=\"ldf-hero-r\">");
		final String meter = b.path("meter").asText("");
		if (!meter.isEmpty() && !this.rc.creating)
		{
			final Object raw = this.rc.raw(meter);
			int m = raw instanceof Number ? ((Number) raw).intValue() : raw instanceof BigDecimal ? ((BigDecimal) raw).intValue() : 0;
			m = Math.max(0, Math.min(100, m));
			w.add("<span class=\"ldf-meter\"><i><b style=\"width:").add(String.valueOf(m)).add("%\"></b></i>").add(String.valueOf(m)).add(" % <small>")
				.add(LayoutPane.esc(RuntimeText.get("completeness", locale))).add("</small></span>");
		}
		final String status = b.path("status").asText("");
		if (!status.isEmpty() && !this.rc.creating)
		{
			final String v = this.rc.display(status);
			if (!v.isEmpty())
			{
				w.add("<span class=\"ldf-badge ldf-warn\">").add(LayoutPane.esc(this.rc.label(status) + " : " + v)).add("</span>");
			}
		}
		w.add("</div></div>");
	}

	private void facts(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		w.add("<div class=\"ldf-facts\" id=\"").add(RenderContext.blockAnchor(id)).add("\">");
		for (final JsonNode p : b.path("items"))
		{
			final String v = this.rc.creating ? "" : this.rc.display(p.asText());
			w.add("<div class=\"ldf-fact\"><b title=\"").add(LayoutPane.esc(v)).add("\">").add(LayoutPane.esc(v.isEmpty() ? "—" : v)).add("</b><span>")
				.add(LayoutPane.esc(this.rc.label(p.asText()))).add("</span></div>");
		}
		final String count = b.path("count").asText("");
		if (!count.isEmpty())
		{
			w.add("<div class=\"ldf-fact\"><b>").add(this.rc.creating ? "—" : String.valueOf(this.rc.count(count))).add("</b><span>")
				.add(LayoutPane.esc(this.rc.label(count))).add("</span></div>");
		}
		w.add("</div>");
	}

	// ------------------------------------------------------------------------------------------------ validation report

	private void validation(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		final Locale locale = this.rc.locale;
		final JsonNode sev = b.path("sev");
		final List<ReportItems.Item> all = new ArrayList<ReportItems.Item>();
		for (final ReportItems.Item item : this.rc.report())
		{
			if (sev.path(item.severity).asBoolean(!ReportItems.INFO.equals(item.severity)))
			{
				all.add(item);
			}
		}
		if (all.isEmpty() && b.path("hideEmpty").asBoolean(false))
		{
			return;
		}
		final int max = b.path("max").asInt(5);
		final List<ReportItems.Item> shown = max > 0 && all.size() > max ? all.subList(0, max) : all;
		w.add("<div class=\"ldf-vrep\" id=\"").add(RenderContext.blockAnchor(id)).add("\"><div class=\"ldf-vhd\"><h3>").add(LayoutPane.esc(this.rc.text(b.get("title"))));
		if (!all.isEmpty())
		{
			w.add(" · ").add(String.valueOf(all.size()));
		}
		w.add("</h3><div class=\"ldf-vcs\">");
		for (final String s : new String[] { ReportItems.ERROR, ReportItems.WARNING, ReportItems.INFO })
		{
			int n = 0;
			for (final ReportItems.Item i : all)
			{
				if (s.equals(i.severity))
				{
					n++;
				}
			}
			if (n > 0)
			{
				w.add("<span class=\"ldf-vc ldf-vc-").add(s).add("\">").add(String.valueOf(n)).add(" ").add(LayoutPane.esc(RuntimeText.get(n == 1 ? s + "1" : s + "N", locale))).add("</span>");
			}
		}
		w.add("</div></div>");
		if (all.isEmpty())
		{
			w.add("<div class=\"ldf-vnone\">").add(LayoutPane.esc(RuntimeText.get("noIssue", locale))).add("</div></div>");
			return;
		}
		final String group = b.path("group").asText("severity");
		if ("severity".equals(group))
		{
			for (final String s : new String[] { ReportItems.ERROR, ReportItems.WARNING, ReportItems.INFO })
			{
				final List<ReportItems.Item> list = new ArrayList<ReportItems.Item>();
				for (final ReportItems.Item i : shown)
				{
					if (s.equals(i.severity))
					{
						list.add(i);
					}
				}
				this.group(w, b, RuntimeText.get(s, locale), list);
			}
		}
		else if ("tab".equals(group))
		{
			final JsonNode tabs = this.rc.layout.path("tabs");
			for (int t = -1; t < tabs.size(); t++)
			{
				final List<ReportItems.Item> list = new ArrayList<ReportItems.Item>();
				for (final ReportItems.Item i : shown)
				{
					final RenderContext.Location l = this.rc.locate(i.node);
					if ((l == null && t == -1) || (l != null && l.tab == t))
					{
						list.add(i);
					}
				}
				this.group(w, b, t == -1 ? RuntimeText.get("offForm", locale) : this.rc.text(tabs.get(t).get("label")), list);
			}
		}
		else
		{
			for (final ReportItems.Item i : shown)
			{
				this.item(w, b, i);
			}
		}
		if (all.size() > shown.size())
		{
			w.add("<div class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get("more", locale, Integer.valueOf(all.size() - shown.size())))).add("</div>");
		}
		w.add("</div>");
	}

	private void group(final UIFormPaneWriter w, final JsonNode b, final String pTitle, final List<ReportItems.Item> pItems)
	{
		if (pItems.isEmpty())
		{
			return;
		}
		w.add("<div class=\"ldf-vg\"><span class=\"ldf-vgh\">").add(LayoutPane.esc(pTitle)).add(" · ").add(String.valueOf(pItems.size())).add("</span>");
		for (final ReportItems.Item i : pItems)
		{
			this.item(w, b, i);
		}
		w.add("</div>");
	}

	private void item(final UIFormPaneWriter w, final JsonNode b, final ReportItems.Item i)
	{
		final Locale locale = this.rc.locale;
		final RenderContext.Location l = this.rc.locate(i.node);
		w.add("<div class=\"ldf-vi ldf-vi-").add(i.severity).add("\"><span class=\"ldf-sv ldf-sv-").add(i.severity).add("\" role=\"img\" aria-label=\"")
			.add(LayoutPane.esc(RuntimeText.get(i.severity, locale))).add("\">").add(ReportItems.INFO.equals(i.severity) ? "i" : "!").add("</span>");
		w.add("<div class=\"ldf-vt\"><b>").add(LayoutPane.esc(i.message)).add("</b><span class=\"ldf-where\">");
		if (l != null)
		{
			w.add(LayoutPane.esc(l.block + (l.field == null ? "" : " › " + l.field)));
		}
		else if (i.node != null)
		{
			w.add("<span class=\"ldf-absent\">").add(LayoutPane.esc(this.rc.label(i.node) + " · " + RuntimeText.get("absent", locale))).add("</span>");
		}
		w.add("</span></div><div class=\"ldf-va\">");
		for (final JsonNode a : b.path("actions"))
		{
			final String on = a.path("on").asText("");
			if (!on.isEmpty() && i.node != null && (i.node.equals(on) || i.node.startsWith(on + "/")) && LText.hasText(a.get("btn")))
			{
				final RenderContext.Location target = this.rc.locate(a.path("go").asText(on));
				if (target != null)
				{
					w.add("<a class=\"ldf-btn\" href=\"#\" onclick=\"return ldfGo('").add(target.anchor).add("',").add(String.valueOf(target.tab)).add(")\">")
						.add(LayoutPane.esc(this.rc.text(a.get("btn")))).add("</a>");
				}
			}
		}
		if (l != null && b.path("links").asBoolean(true))
		{
			w.add("<a class=\"ldf-go\" href=\"#\" onclick=\"return ldfGo('").add(l.anchor).add("',").add(String.valueOf(l.tab)).add(")\">")
				.add(LayoutPane.esc(RuntimeText.get(l.field == null ? "gotoRow" : "goto", locale))).add("</a>");
		}
		w.add("</div></div>");
	}

	// ------------------------------------------------------------------------------------------------ associations

	private void grid(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		final String a = b.path("a").asText();
		final SchemaNode node = this.rc.node(a);
		if (node == null)
		{
			return;
		}
		final String title = this.rc.blockTitle(b);
		final String count = this.rc.record == null ? null : String.valueOf(this.rc.count(a));
		final String add = LText.hasText(b.get("addBtn")) ? "<span class=\"ldf-btn\">" + LayoutPane.esc(this.rc.text(b.get("addBtn"))) + "</span>" : null;
		this.startPanel(w, b, id, title, count, null);
		if ("cards".equals(b.path("style").asText()) && this.rc.record != null)
		{
			final List<Adaptation> rows = this.rc.rows(a, 60);
			if (rows.isEmpty())
			{
				w.add("<div class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get("noRow", this.rc.locale))).add("</div>");
			}
			else
			{
				w.add("<div class=\"ldf-cards\">");
				for (final Adaptation r : rows)
				{
					w.add("<div class=\"ldf-card\"><b>").add(LayoutPane.esc(r.getLabel(this.rc.locale))).add("</b>");
					final String sub = this.cardDetail(r, b);
					if (!sub.isEmpty())
					{
						w.add("<div class=\"ldf-s\">").add(LayoutPane.esc(sub)).add("</div>");
					}
					w.add("</div>");
				}
				w.add("</div>");
			}
			w.add("<details class=\"ldf-manage\"><summary>").add(LayoutPane.esc(add != null ? this.rc.text(b.get("addBtn")) : RuntimeText.get("manageRows", this.rc.locale)))
				.add("</summary>");
			w.addWidget(LayoutPaths.relative(a)); // the native association grid: toolbar, filter, paging
			w.add("</details>");
		}
		else
		{
			w.addWidget(LayoutPaths.relative(a)); // the native association grid: toolbar, filter, paging
		}
		w.add("</section>");
	}

	/** The fields listed in the block's "cardFields", formatted, on one line. */
	private String cardDetail(final Adaptation r, final JsonNode b)
	{
		final List<String> parts = new ArrayList<String>();
		for (final JsonNode p : b.path("cardFields"))
		{
			try
			{
				final Path path = LayoutPaths.relative(p.asText());
				final SchemaNode n = r.getSchemaNode().getNode(path);
				final Object v = n == null ? null : r.get(path);
				if (v != null)
				{
					final String s = n.displayOccurrence(v, true, r.createValueContext(), this.rc.locale);
					if (s != null && !s.isEmpty())
					{
						parts.add(s);
					}
				}
			}
			catch (final RuntimeException ex)
			{
				// a field the target table does not have
			}
		}
		return String.join(" · ", parts);
	}

	private void map(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		this.startPanel(w, b, id, this.rc.text(b.get("title")), null, null);
		final String lat = this.rc.display(b.path("lat").asText());
		final String lng = this.rc.display(b.path("lng").asText());
		double la = Double.NaN;
		double lo = Double.NaN;
		try
		{
			la = Double.parseDouble(lat.replace(',', '.').trim());
			lo = Double.parseDouble(lng.replace(',', '.').trim());
		}
		catch (final RuntimeException ex)
		{
			// no coordinates
		}
		if (Double.isNaN(la) || Double.isNaN(lo))
		{
			w.add("<div class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get("noCoords", this.rc.locale))).add("</div></section>");
			return;
		}
		final int height = b.path("tall").asBoolean(false) ? 560 : 360;
		final String bbox = (lo - 0.012) + "," + (la - 0.007) + "," + (lo + 0.012) + "," + (la + 0.007);
		w.add("<div class=\"ldf-map\" style=\"--ldf-map-h:").add(String.valueOf(height)).add("px\"><iframe loading=\"lazy\" title=\"map\" src=\"https://www.openstreetmap.org/export/embed.html?bbox=")
			.add(bbox).add("&amp;layer=mapnik&amp;marker=").add(la + "," + lo).add("\"></iframe></div>");
		w.add("<div class=\"ldf-more\"><a href=\"https://www.openstreetmap.org/?mlat=").add(String.valueOf(la)).add("&amp;mlon=").add(String.valueOf(lo))
			.add("#map=16/").add(la + "/" + lo).add("\" target=\"_blank\" rel=\"noopener\">").add(LayoutPane.esc(RuntimeText.get("openMap", this.rc.locale)))
			.add("</a> · ").add(LayoutPane.esc(lat + ", " + lng)).add("</div></section>");
	}

	private void tree(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		this.startPanel(w, b, id, this.rc.text(b.get("title")), null, null);
		final String parent = this.rc.display(b.path("parent").asText());
		w.add("<div class=\"ldf-tree\"><span class=\"ldf-top\">").add(LayoutPane.esc(parent.isEmpty() ? RuntimeText.get("noParent", this.rc.locale) : parent)).add("</span>");
		w.add("<span class=\"ldf-me\">").add(LayoutPane.esc(this.rc.record == null ? "" : this.rc.record.getLabel(this.rc.locale))).add("</span>");
		final String children = b.path("children").asText("");
		if (!children.isEmpty())
		{
			final int n = this.rc.count(children);
			for (final Adaptation r : this.rc.rows(children, 12))
			{
				w.add("<span class=\"ldf-kid\">").add(LayoutPane.esc(r.getLabel(this.rc.locale))).add("</span>");
			}
			if (n > 12)
			{
				w.add("<span class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get("more", this.rc.locale, Integer.valueOf(n - 12)))).add("</span>");
			}
		}
		w.add("</div></section>");
	}

	/** The Match and Merge state of the record, read by reflection: the form works on an instance without the add-on. */
	private void duplicates(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		this.startPanel(w, b, id, this.rc.text(b.get("title")), null, null);
		w.add("<div class=\"ldf-dup\">");
		if (this.rc.record == null)
		{
			w.add("</div></section>");
			return;
		}
		try
		{
			final Class<?> helper = Class.forName("com.orchestranetworks.addon.mame.common.util.metadata.MetadataHelper");
			final Object state = helper.getMethod("getState", Adaptation.class).invoke(null, this.rc.record);
			Object group = null;
			try
			{
				group = helper.getMethod("getGroupId", Adaptation.class).invoke(null, this.rc.record);
			}
			catch (final ReflectiveOperationException ex)
			{
				// older add-on
			}
			if (state == null)
			{
				w.add("<span class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get("mameNone", this.rc.locale))).add("</span>");
			}
			else
			{
				final String s = String.valueOf(state);
				w.add("<span class=\"ldf-badge ").add("GOLDEN".equals(s) ? "ldf-ok" : "MERGED".equals(s) ? "ldf-warn" : "").add("\">").add(LayoutPane.esc(s)).add("</span>");
				if (group != null)
				{
					w.add("<span class=\"ldf-badge\">").add(LayoutPane.esc(RuntimeText.get("group", this.rc.locale) + " " + group)).add("</span>");
				}
			}
		}
		catch (final ClassNotFoundException | LinkageError ex)
		{
			w.add("<span class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get("mameAbsent", this.rc.locale))).add("</span>");
		}
		catch (final ReflectiveOperationException | RuntimeException ex)
		{
			w.add("<span class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get("mameNone", this.rc.locale))).add("</span>");
		}
		w.add("</div></section>");
	}

	private void custom(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		final String name = b.path("cls").asText("");
		final LayoutComponent component = LayoutComponents.get(name);
		this.startPanel(w, b, id, this.rc.text(b.get("title")), null, null);
		if (component == null)
		{
			w.add("<div class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get("componentMissing", this.rc.locale, name))).add("</div>");
		}
		else
		{
			component.write(w, this.rc.context, b, this.rc.locale);
		}
		w.add("</section>");
	}

	static String esc(final String s)
	{
		if (s == null)
		{
			return "";
		}
		final StringBuilder b = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++)
		{
			final char c = s.charAt(i);
			switch (c)
			{
			case '&':
				b.append("&amp;");
				break;
			case '<':
				b.append("&lt;");
				break;
			case '>':
				b.append("&gt;");
				break;
			case '"':
				b.append("&quot;");
				break;
			case '\'':
				b.append("&#39;");
				break;
			default:
				b.append(c);
			}
		}
		return b.toString();
	}
}
