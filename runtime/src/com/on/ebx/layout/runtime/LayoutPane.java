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
import com.orchestranetworks.ui.base.JsFunctionCall;
import com.orchestranetworks.ui.form.widget.UIAtomicWidget;
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
	private Behaviors.Rules rules;
	/** Blocks that passed their display conditions on this tab. */
	private int drawn;

	LayoutPane(final RenderContext pContext, final JsonNode pTab, final int pTabIndex)
	{
		this.rc = pContext;
		this.tab = pTab;
		this.tabIndex = pTabIndex;
	}

	@Override
	public void writePane(final UIFormPaneWriter pWriter, final UIFormContext pContext)
	{
		Access.begin(pContext.getSession(), this.rc.record, pContext.getCurrentTable());
		final long t0 = System.currentTimeMillis();
		try
		{
			this.write(pWriter);
		}
		catch (final RuntimeException ex)
		{
			// audit L2-25: a pane that cannot be drawn says so (the blocks are protected one by one, this is what is left)
			LoggingCategory.getKernel().error("[layout-designer] LDF-RENDER tab " + this.tabIndex + " not drawn: " + ex, ex);
			pWriter.add("<div class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get("layoutError", this.rc.locale))).add("</div>");
		}
		finally
		{
			Access.end();
		}
		ConfigurableForm.slow("tab " + this.tabIndex + " written", this.rc.context.getCurrentTable().getTablePath().format(), t0);
	}

	private void write(final UIFormPaneWriter pWriter)
	{
		// the style sheet and the scripts are the same in every tab and every tab is in the page: the first pane writes them (audit L2-17)
		final boolean assets = this.rc.assets.first();
		if (assets)
		{
			pWriter.add("<style>").add(Css.STYLE).add(Css.themeVars(this.rc.themeMain, this.rc.themeFocus)).add("</style>");
		}
		pWriter.add("<div class=\"ldf\">");
		this.rules = new Behaviors.Rules(pWriter);
		this.drawn = 0;
		if (this.tabIndex == 0)
		{
			this.personalizeButton(pWriter);
		}
		final JsonNode rows = this.tab.path("rows");
		for (int r = 0; r < rows.size(); r++)
		{
			final JsonNode row = rows.get(r);
			final JsonNode cols = row.path("blocks");
			final boolean two = row.path("columns").asInt(1) == 2 && cols.size() == 2;
			final String ratio = row.path("ratio").asText("11");
			final JsonNode section = row.path("section");
			final boolean inSection = section.isObject() && section.path("on").asBoolean(true) && !section.isEmpty();
			if (inSection)
			{
				pWriter.add("<details class=\"ldf-sec\"").add(section.path("collapsed").asBoolean(false) ? "" : " open").add("><summary>")
					.add(LayoutPane.esc(this.rc.text(section.get("title")))).add("</summary>");
			}
			pWriter.add("<div class=\"ldf-row").add(two ? " ldf-c2" + ("21".equals(ratio) ? " ldf-r21" : "12".equals(ratio) ? " ldf-r12" : "") : "").add("\">");
			for (int c = 0; c < cols.size(); c++)
			{
				pWriter.add("<div class=\"ldf-col\">");
				final JsonNode col = cols.get(c);
				for (int k = 0; k < col.size(); k++)
				{
					final JsonNode block = col.get(k);
					final long b0 = System.currentTimeMillis();
					try
					{
						this.block(pWriter, block, RenderContext.blockId(block, this.tabIndex, r, c, k));
					}
					catch (final RuntimeException ex)
					{
						LoggingCategory.getKernel().error("[layout-designer] block " + block.path("type").asText() + " not drawn: " + ex, ex);
					}
					ConfigurableForm.slow("block " + block.path("type").asText() + " of tab " + this.tabIndex, this.rc.context.getCurrentTable().getTablePath().format(), b0);
				}
				pWriter.add("</div>");
			}
			pWriter.add("</div>");
			if (inSection)
			{
				pWriter.add("</details>");
			}
		}
		if (rows.size() == 0)
		{
			pWriter.add("<div class=\"ldf-none\">").add(LayoutPane.esc(RuntimeText.get("nothingForRole", this.rc.locale))).add("</div>");
		}
		else if (this.drawn == 0 && Personal.MINE.equals(this.tab.path("id").asText()))
		{
			// every pinned block has a condition this record does not meet
			pWriter.add("<div class=\"ldf-none\">").add(LayoutPane.esc(RuntimeText.get("pinnedNotHere", this.rc.locale))).add("</div>");
		}
		pWriter.add("</div>");
		if (assets)
		{
			pWriter.addJS_cr(Css.GO_JS);
			pWriter.addJS_cr(DisplayWidgets.JS);
			pWriter.addJS_cr(OrgChart.JS);
			pWriter.addJS_cr(OrgChart.BLOCKS_JS);
			pWriter.addJS_cr(Css.LAZY_JS);
		}
		if (!this.rules.isEmpty())
		{
			pWriter.addJS_cr(this.rules.js());
		}
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
		// 1.17.0: only outside / only inside a workflow task, hidden or shown while a workflow runs for the record
		if (!WorkflowState.allows(this.rc, b.get("wf")))
		{
			return;
		}
		// 1.23.0: the tracking information or an attribute of the user's session
		if (!SessionState.allows(this.rc, b.get("session")))
		{
			return;
		}
		if (!this.readable(b))
		{
			return;
		}
		this.drawn++;
		final JsonNode showIf = b.get("showIf");
		final boolean dynamic = showIf != null && !showIf.path("field").asText("").isEmpty();
		if (dynamic)
		{
			final boolean on = Behaviors.test(showIf, this.rc.raw(showIf.path("field").asText()));
			final List<String> clear = new ArrayList<String>();
			if ("clear".equals(b.path("onHide").asText("keep")))
			{
				for (final JsonNode f : b.path("fields"))
				{
					clear.add(f.path("p").asText());
				}
			}
			w.add("<div class=\"ldf-dyn\" id=\"ldd-").add(id).add("\"").add(on ? "" : " style=\"display:none\"").add(">");
			this.rules.show(this.rc, showIf, "ldd-" + id, clear);
		}
		final boolean folded = b.path("_collapsed").asBoolean(false);
		if (folded)
		{
			final String title = this.rc.blockTitle(b);
			w.add("<details class=\"ldf-fold\"><summary>").add(LayoutPane.esc(title.isEmpty() ? RuntimeText.get("foldedBlock", this.rc.locale) : title)).add("</summary>");
		}
		this.typed(w, b, id);
		if (folded)
		{
			w.add("</details>");
		}
		if (dynamic)
		{
			w.add("</div>");
		}
	}

	/** Whether the block has something the user may see (EBX permissions): {@link BlockAccess}. */
	private boolean readable(final JsonNode b)
	{
		return BlockAccess.readable(b, this.rc.root, this.rc.record);
	}

	/** "Arrange my form": the user's own arrangement, in EBX's pop-up (a module service on the record, documented API). */
	private void personalizeButton(final UIFormPaneWriter w)
	{
		if (!Personal.allowed(this.rc.layout) || this.rc.creating || this.rc.record == null)
		{
			return;
		}
		String js = null;
		try
		{
			final com.orchestranetworks.ui.UIHttpManagerComponent wc = this.rc.context.createWebComponentForSubSession();
			wc.selectInstanceOrOccurrence(this.rc.record);
			wc.setService(com.orchestranetworks.service.ServiceKey.forModuleServiceName(LayoutPaths.MODULE_NAME, LayoutPaths.PERSONALIZE_SERVICE));
			js = w.buildButtonPreview(wc).getJavaScriptCommand();
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] personalization link not built: " + ex, ex);
		}
		if (js == null)
		{
			return;
		}
		w.add("<div class=\"ldf-mine\"><button type=\"button\" class=\"ldf-mine-btn\" onclick=\"").add(LayoutPane.esc(js)).add(";return false;\">")
			.add(LayoutPane.esc(RuntimeText.get("arrangeMyForm", this.rc.locale))).add("</button></div>");
	}

	private void typed(final UIFormPaneWriter w, final JsonNode b, final String id)
	{
		final String type = b.path("type").asText();
		final String bid = b.path("id").asText(id);
		final String inner = "orgChart".equals(type) ? OrgChart.render(this.rc, b, bid, w)
			: "valueHistory".equals(type) ? ValueHistory.render(this.rc, b, bid) : "sitesMap".equals(type) ? SitesMap.render(this.rc, b, bid)
			: "workflowTasks".equals(type) ? MdmBlocks.workflowTasks(this.rc, b, w) : "actions".equals(type) ? ActionsBlock.render(this.rc, b, w) : "sourceValues".equals(type) ? MdmBlocks.sourceValues(this.rc, b)
			: "translations".equals(type) ? MdmBlocks.translations(this.rc, b) : "inheritance".equals(type) ? MdmBlocks.inheritance(this.rc, b)
			: "asOfTree".equals(type) ? EffectiveBlocks.asOfTree(this.rc, b, w) : "stateTimeline".equals(type) ? EffectiveBlocks.stateTimeline(this.rc, b, w)
			: "delta".equals(type) ? EffectiveBlocks.delta(this.rc, b, w) : this.more(type, b);
		if (inner != null)
		{
			this.startPanel(w, b, id, this.rc.blockTitle(b), null, null);
			w.add(inner).add("</section>");
			return;
		}
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
		else if ("hours".equals(type))
		{
			this.startPanel(w, b, id, this.rc.text(b.get("title")), null, null);
			w.add(OpeningHours.render(this.rc, b)).add("</section>");
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

	/** The HTML of a block added in 1.4.0, null for the other types. */
	private String more(final String pType, final JsonNode b)
	{
		switch (pType)
		{
		case "timeline":
			return MoreBlocks.timeline(this.rc, b);
		case "history":
			return MoreBlocks.history(this.rc, b);
		case "completeness":
			return MoreBlocks.completeness(this.rc, b);
		case "stepper":
			return MoreBlocks.stepper(this.rc, b);
		case "checklist":
			return MoreBlocks.checklist(this.rc, b);
		case "chart":
			return MoreBlocks.chart(this.rc, b);
		case "aggregates":
			return MoreBlocks.aggregates(this.rc, b);
		case "pivot":
			return MoreBlocks.pivot(this.rc, b);
		case "linked":
			return MoreBlocks.linkedBlock(this.rc, b);
		case "contacts":
			return MoreBlocks.contacts(this.rc, b);
		case "gallery":
			return MoreBlocks.gallery(this.rc, b);
		case "documents":
			return MoreBlocks.documents(this.rc, b);
		case "links":
			return MoreBlocks.links(this.rc, b);
		case "validity":
			return MoreBlocks.validity(this.rc, b);
		case "address":
			return MoreBlocks.address(this.rc, b);
		case "hierarchy":
			return MoreBlocks.hierarchy(this.rc, b);
		case "recordInfo":
			return MoreBlocks.recordInfo(this.rc, b);
		case "changes":
			return MoreBlocks.changes(this.rc, b);
		case "usedBy":
			return MoreBlocks.usedBy(this.rc, b);
		case "heatmap":
			return ChartBlocks.heatmap(this.rc, b);
		case "gantt":
			return ChartBlocks.gantt(this.rc, b);
		case "compare":
			return ChartBlocks.compare(this.rc, b);
		case "coverage":
			return ChartBlocks.coverage(this.rc, b);
		default:
			return null;
		}
	}

	/** An XPath predicate on the record; true when there is none, when the record is new or when it cannot run. */
	private boolean visible(final String pCondition)
	{
		return this.rc.xpath(pCondition, true);
	}

	/**
	 * The panel's colour: {@code "toneIf": {"if": "<XPath predicate on the record>", "then": "bad", "else": "ok"}} when the
	 * record exists (1.14.0), else {@code "tone"}. A missing branch falls back to {@code "tone"}.
	 */
	private String toneOf(final JsonNode b)
	{
		final JsonNode ti = b.path("toneIf");
		final String fixed = b.path("tone").asText("");
		if (!ti.isObject() || ti.path("if").asText("").trim().isEmpty() || this.rc.record == null)
		{
			return fixed;
		}
		final String t = this.rc.xpath(ti.path("if").asText(), false) ? ti.path("then").asText("") : ti.path("else").asText("");
		return t.isEmpty() ? fixed : t;
	}

	private void startPanel(final UIFormPaneWriter w, final JsonNode b, final String pId, final String pTitle, final String pCount, final String pExtra)
	{
		w.add("<section class=\"ldf-panel").add(Values.toneClass(this.toneOf(b))).add("\" id=\"").add(RenderContext.blockAnchor(pId)).add("\">");
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
		final JsonNode by = b.get("attributesBy");
		final List<String[]> familyFields = new ArrayList<String[]>();
		boolean table = false;
		for (int i = 0; i < fields.size(); i++)
		{
			final JsonNode f = fields.get(i);
			final String stored = f.path("p").asText();
			final SchemaNode node = this.rc.node(stored);
			if (node == null || !Access.canRead(node, this.rc.record) || !SessionState.allows(this.rc, f.get("session")))
			{
				continue;
			}
			final Path path = LayoutPaths.relative(stored);
			final String anchor = RenderContext.fieldAnchor(id, i);
			final String kind = f.path("w").asText("");
			// a display widget (figure, badge…) only shows a value: it makes the field read only, except while a record is created
			final boolean displayKind = "badge".equals(kind) || "fig".equals(kind) || "link".equals(kind) || "mono".equals(kind) || DisplayWidgets.isDisplay(kind);
			final boolean ro = allReadOnly || f.path("ro").asBoolean(false) || node.isValueFunction() || Behaviors.readOnly(this.rc, f.get("readOnlyIf"))
				|| (displayKind && !this.rc.creating);
			final JsonNode fShow = f.get("showIf");
			if (fShow != null && !fShow.path("field").asText("").isEmpty())
			{
				this.rules.show(this.rc, fShow, anchor, "clear".equals(f.path("onHide").asText("keep")) ? java.util.Collections.singletonList(stored) : null);
			}
			final JsonNode fMand = f.get("mandatoryIf");
			if (fMand != null && !fMand.path("field").asText("").isEmpty() && !ro)
			{
				this.rules.mandatory(this.rc, fMand, stored);
			}
			if (by != null)
			{
				familyFields.add(new String[] { anchor, stored });
			}
			final String label = LText.hasText(f.get("label")) ? this.rc.text(f.get("label")) : node.getLabel(this.rc.locale);
			final boolean displayOnly = ro && displayKind;
			if (displayOnly)
			{
				if (table)
				{
					w.endTableFormRow();
					table = false;
				}
				w.add("<div class=\"ldf-ro\" id=\"").add(anchor).add("\"><span class=\"ldf-l\">").add(LayoutPane.esc(label)).add("</span>");
				w.add(DisplayWidgets.isDisplay(kind) ? DisplayWidgets.html(this.rc, f, stored, kind) : this.displayHtml(stored, kind)).add("</div>");
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
		if (by != null && !by.path("key").asText("").isEmpty())
		{
			this.rules.family(this.rc, by, RenderContext.blockAnchor(id), familyFields);
		}
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
			else if (!ro && this.rc.controllers.contains(LayoutPaths.stored(path)))
			{
				widget = LayoutPane.atomic(w, path, node); // the best-matching widget takes no afterValueChanged action
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
		else if (widget instanceof UIAtomicWidget && this.rc.controllers.contains(LayoutPaths.stored(path)))
		{
			((UIAtomicWidget) widget).setActionOnAfterValueChanged(JsFunctionCall.on("ldfDyn"));
		}
		return widget;
	}

	/** An atomic widget for a field whose changes drive behaviours, close to what EBX would choose. */
	private static UIWidget atomic(final UIFormPaneWriter w, final Path path, final SchemaNode node)
	{
		if (node.getMaxOccurs() > 1)
		{
			return w.newBestMatching(path);
		}
		if (node.getFacetOnTableReference() != null || node.getFacetEnumeration() != null)
		{
			return w.newComboBox(path);
		}
		final Class<?> c = node.getOccurrencesJavaClass();
		if (c == Boolean.class)
		{
			return w.newRadioButtonGroup(path);
		}
		if (c == java.util.Date.class)
		{
			return w.newDateTime(path);
		}
		return w.newTextBox(path);
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
		if (!meter.isEmpty() && !this.rc.creating && this.rc.canRead(meter))
		{
			final Object raw = this.rc.raw(meter);
			// 1.22.0: "meterLabel" names what the meter measures (progress, score…); completeness by default
			final String meterLabel = LText.pick(b.get("meterLabel"), locale, locale);
			int m = raw instanceof Number ? ((Number) raw).intValue() : raw instanceof BigDecimal ? ((BigDecimal) raw).intValue() : 0;
			m = Math.max(0, Math.min(100, m));
			w.add("<span class=\"ldf-meter\"><i><b style=\"width:").add(String.valueOf(m)).add("%\"></b></i>").add(String.valueOf(m)).add(" % <small>")
				.add(LayoutPane.esc(meterLabel.isEmpty() ? RuntimeText.get("completeness", locale) : meterLabel)).add("</small></span>");
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
			if (!this.rc.canRead(p.asText()))
			{
				continue;
			}
			final String v = this.rc.creating ? "" : this.rc.display(p.asText());
			w.add("<div class=\"ldf-fact\"><b title=\"").add(LayoutPane.esc(v)).add("\">").add(LayoutPane.esc(v.isEmpty() ? "—" : v)).add("</b><span>")
				.add(LayoutPane.esc(this.rc.label(p.asText()))).add("</span></div>");
		}
		final String count = b.path("count").asText("");
		if (!count.isEmpty() && this.rc.canRead(count))
		{
			w.add("<div class=\"ldf-fact\"><b>").add(this.rc.creating ? "—" : this.rc.countText(count)).add("</b><span>")
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
		// 1.14.0: a native grid outside the first tab loads when its tab opens, unless the layout says "lazy": false
		final boolean lazy = b.has("lazy") ? b.path("lazy").asBoolean(false) : this.tabIndex > 0 && !"cards".equals(b.path("style").asText());
		if (lazy && !this.rc.creating && this.rc.record != null && !b.path("id").asText("").isEmpty())
		{
			final String src = this.lazySource(b.path("id").asText());
			if (src != null)
			{
				// built when its tab is opened: a frame showing this one native grid (module service LayoutGrid)
				final int n = this.rc.count(a);
				this.startPanel(w, b, id, title, this.rc.countText(a), null);
				final String countUrl = this.countUrl(a);
				w.add("<div class=\"ldf-lazy\" data-n=\"").add(String.valueOf(n)).add("\"").add(countUrl == null ? "" : " data-count=\"" + LayoutPane.esc(countUrl) + "\"")
					.add(" data-src=\"").add(LayoutPane.esc(src)).add("\" style=\"height:").add(String.valueOf(Math.max(200, b.path("height").asInt(460))))
					.add("px\"><div class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get("lazyLoading", this.rc.locale))).add("</div></div></section>");
				return;
			}
		}
		final String count = this.rc.record == null ? null : this.rc.countText(a);
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

	/**
	 * Data Services' count of the association's rows for this record ({@code …/root/T/<key>/<association>:count}), read by
	 * the page after a row is added or removed in the frame, to keep the block's and the tab's counters right. It answers
	 * with the user's own permissions (the browser's EBX session).
	 */
	private String countUrl(final String pAssociation)
	{
		try
		{
			final Adaptation r = this.rc.record;
			final StringBuilder u = new StringBuilder("/ebx-dataservices/rest/data/v1/");
			u.append(LayoutPane.seg("B" + r.getHome().getKey().getName())).append('/').append(LayoutPane.seg(r.getContainer().getAdaptationName().getStringName()));
			for (final String s : r.getContainerTable().getTablePath().format().split("/"))
			{
				if (!s.isEmpty())
				{
					u.append('/').append(LayoutPane.seg(s));
				}
			}
			u.append('/').append(LayoutPane.seg(r.getOccurrencePrimaryKey().format()));
			for (final String s : pAssociation.split("/"))
			{
				if (!s.isEmpty())
				{
					u.append('/').append(LayoutPane.seg(s));
				}
			}
			return u.append(":count").toString();
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private static String seg(final String s)
	{
		try
		{
			return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
		}
		catch (final java.io.UnsupportedEncodingException ex)
		{
			return s;
		}
	}

	/** The address of the page that draws this grid alone (a sub-session of this form, documented API); null when it cannot be built. */
	private String lazySource(final String pBlockId)
	{
		try
		{
			final com.orchestranetworks.ui.UIHttpManagerComponent wc = this.rc.context.createWebComponentForSubSession();
			wc.selectInstanceOrOccurrence(this.rc.record);
			wc.setService(com.orchestranetworks.service.ServiceKey.forModuleServiceName(LayoutPaths.MODULE_NAME, LayoutPaths.GRID_SERVICE));
			wc.setAdditionalParameter(LayoutPaths.GRID_BLOCK_PARAM, pBlockId);
			return wc.getURIWithParameters();
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] lazy grid not prepared, drawn at once: " + ex);
			return null;
		}
	}

	/** The fields listed in the block's "cardFields", formatted, on one line. */
	private String cardDetail(final Adaptation r, final JsonNode b)
	{
		// Values.display applies the user's field permission on this row, as every other block does (audit L2-03): a
		// hidden field is neither drawn nor counted, and a field the target table does not have gives ""
		return LayoutPane.cardLine(b, new java.util.function.Function<String, String>()
		{
			@Override
			public String apply(final String pPath)
			{
				return Values.display(r, pPath, LayoutPane.this.rc.locale);
			}
		});
	}

	/** The non-empty values of the block's "cardFields", each shown by {@code pDisplay}, on one line. */
	static String cardLine(final JsonNode b, final java.util.function.Function<String, String> pDisplay)
	{
		final List<String> parts = new ArrayList<String>();
		for (final JsonNode p : b.path("cardFields"))
		{
			final String s = pDisplay.apply(p.asText());
			if (s != null && !s.isEmpty())
			{
				parts.add(s);
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
		// no coordinates: the position of the address, when the block names address fields (number, street, postcode, city, country…)
		String address = "";
		boolean approximate = false;
		boolean pendingPosition = false;
		if ((Double.isNaN(la) || Double.isNaN(lo)) && b.path("address").isArray() && this.rc.record != null)
		{
			final List<String> parts = new ArrayList<String>();
			for (final JsonNode p : b.path("address"))
			{
				final String v = this.rc.display(p.asText()).trim();
				if (!v.isEmpty())
				{
					parts.add(v);
				}
			}
			address = String.join(", ", parts);
			Geocoder.warnIfLarge(this.rc.record.getContainerTable(), id);
			// never wait for the geocoding service while the form is drawn (1.15.0): looked up in the background, shown next time
			final double[] at = Geocoder.known(address);
			if (at != null)
			{
				la = at[0];
				lo = at[1];
				approximate = true;
			}
			else if (!address.isEmpty() && !Geocoder.isKnown(address))
			{
				Geocoder.lookLater(address, this.rc.locale);
				pendingPosition = true;
			}
		}
		if (Double.isNaN(la) || Double.isNaN(lo))
		{
			w.add("<div class=\"ldf-more\">").add(LayoutPane.esc(RuntimeText.get(address.isEmpty() ? "noCoords" : pendingPosition ? "addressPending" : "addressNotFound", this.rc.locale)));
			if (!address.isEmpty())
			{
				w.add(" · <a href=\"https://www.openstreetmap.org/search?query=").add(LayoutPane.esc(java.net.URLEncoder.encode(address, java.nio.charset.StandardCharsets.UTF_8)))
					.add("\" target=\"_blank\" rel=\"noopener\">").add(LayoutPane.esc(RuntimeText.get("openMap", this.rc.locale))).add("</a>");
			}
			if (pendingPosition)
			{
				// "Locate now" (1.15.1): the browser asks the module for this address, then draws the map in place
				w.add(" <button type=\"button\" class=\"ldf-btn ldf-map-locate\" data-g=\"").add(LayoutPane.esc(Geocoding.url())).add("\" data-t=\"")
					.add(LayoutPane.esc(GeoTicket.issue(this.rc.context.getSession().getUserReference().getUserId()))).add("\" data-q=\"").add(LayoutPane.esc(address))
					.add("\" data-h=\"").add(String.valueOf(b.path("tall").asBoolean(false) ? 560 : 360)).add("\" data-wait=\"")
					.add(LayoutPane.esc(RuntimeText.get("locatingOne", this.rc.locale))).add("\" data-miss=\"").add(LayoutPane.esc(RuntimeText.get("addressNotFound", this.rc.locale)))
					.add("\">").add(LayoutPane.esc(RuntimeText.get("sm.locate", this.rc.locale))).add("</button>");
			}
			w.add("</div></section>");
			return;
		}
		final int height = b.path("tall").asBoolean(false) ? 560 : 360;
		final String bbox = (lo - 0.012) + "," + (la - 0.007) + "," + (lo + 0.012) + "," + (la + 0.007);
		w.add("<div class=\"ldf-map\" style=\"--ldf-map-h:").add(String.valueOf(height)).add("px\"><iframe loading=\"lazy\" title=\"map\" src=\"https://www.openstreetmap.org/export/embed.html?bbox=")
			.add(bbox).add("&amp;layer=mapnik&amp;marker=").add(la + "," + lo).add("\"></iframe></div>");
		w.add("<div class=\"ldf-more\"><a href=\"https://www.openstreetmap.org/?mlat=").add(String.valueOf(la)).add("&amp;mlon=").add(String.valueOf(lo))
			.add("#map=16/").add(la + "/" + lo).add("\" target=\"_blank\" rel=\"noopener\">").add(LayoutPane.esc(RuntimeText.get("openMap", this.rc.locale)))
			.add("</a> · ").add(LayoutPane.esc(approximate ? RuntimeText.get("fromAddress", this.rc.locale, address) : lat + ", " + lng)).add("</div></section>");
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
