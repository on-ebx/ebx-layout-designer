package com.on.ebx.layout.designer;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.on.ebx.layout.explorer.Cond;
import com.on.ebx.layout.explorer.Engine;
import com.on.ebx.layout.explorer.ExplorerStore;
import com.on.ebx.layout.explorer.Query;
import com.on.ebx.layout.explorer.Spec;
import com.on.ebx.layout.explorer.View;
import com.on.ebx.layout.explorer.Views;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.on.ebx.layout.runtime.Media;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.PrimaryKey;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.instance.ValueContext;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.ServiceKey;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.ui.UIHttpManagerComponent;
import com.orchestranetworks.ui.selection.TableViewEntitySelection;
import com.orchestranetworks.userservice.UserService;
import com.orchestranetworks.userservice.UserServiceAjaxContext;
import com.orchestranetworks.userservice.UserServiceAjaxRequest;
import com.orchestranetworks.userservice.UserServiceDisplayConfigurator;
import com.orchestranetworks.userservice.UserServiceEventOutcome;
import com.orchestranetworks.userservice.UserServiceObjectContextBuilder;
import com.orchestranetworks.userservice.UserServicePane;
import com.orchestranetworks.userservice.UserServicePaneContext;
import com.orchestranetworks.userservice.UserServicePaneWriter;
import com.orchestranetworks.userservice.UserServiceProcessEventOutcomeContext;
import com.orchestranetworks.userservice.UserServiceSetupDisplayContext;
import com.orchestranetworks.userservice.UserServiceSetupObjectContext;
import com.orchestranetworks.userservice.UserServiceValidateContext;
import com.orchestranetworks.workflow.PublishedProcessKey;
import com.orchestranetworks.workflow.WorkflowEngine;

/**
 * The explorer page ({@code www/explorer.js}). Search, facets, tiles, sort and paging run on the server over the user's
 * {@link View}: the page receives one page of cards and the counts, never the whole table nor a whole list of values.
 * Each AJAX call recomputes the view's permissions when they are older than a minute, and re-checks any record the page
 * names. A record opens in EBX's pop-up (documented {@code buildButtonPreview} on a sub-session web component), whose
 * address the page completes with the record's key ({@link #MARK}).
 */
public final class ExplorerUserService implements UserService<TableViewEntitySelection>
{
	/** In a pop-up command, replaced by the page with the n-th primary key value of the record. */
	static final String MARK = "LDXMARK";

	private Adaptation dataset;
	private AdaptationTable table;
	private int explorerId = -1;
	private String perspective;

	@Override
	public void setupObjectContext(final UserServiceSetupObjectContext<TableViewEntitySelection> pContext, final UserServiceObjectContextBuilder pBuilder)
	{
		if (this.table != null)
		{
			return;
		}
		this.table = pContext.getEntitySelection().getTable();
		this.dataset = pContext.getEntitySelection().getDataset();
		try
		{
			this.explorerId = Integer.parseInt(String.valueOf(pContext.getParameter(LayoutPaths.EXPLORER_PARAM)).trim());
		}
		catch (final RuntimeException ex)
		{
			this.explorerId = -1;
		}
		try
		{
			final com.orchestranetworks.ui.perspective.Perspective p = pContext.getCurrentPerspective();
			this.perspective = p == null || p.isAdvanced() ? null : p.getName();
		}
		catch (final RuntimeException ex)
		{
			this.perspective = null;
		}
	}

	/** The explorer asked for, else the first active one of the table; null when none. */
	private ExplorerStore.Row row()
	{
		final Repository repository = this.dataset.getHome().getRepository();
		if (this.explorerId >= 0)
		{
			final ExplorerStore.Row r = ExplorerStore.find(repository, this.explorerId);
			return r != null && this.dataset.getSchemaLocation().format().equals(r.model) && this.table.getTablePath().format().equals(r.table) ? r : null;
		}
		for (final ExplorerStore.Row r : ExplorerStore.list(repository, this.dataset.getSchemaLocation(), this.table.getTablePath()))
		{
			if (r.active)
			{
				this.explorerId = r.id;
				return r;
			}
		}
		return null;
	}

	@Override
	public void setupDisplay(final UserServiceSetupDisplayContext<TableViewEntitySelection> pContext, final UserServiceDisplayConfigurator pConfigurator)
	{
		final Session session = pContext.getSession();
		final Locale locale = session.getLocale();
		final ExplorerStore.Row row = this.row();
		final String title = row == null ? Messages.get(locale, "explorer.label")
			: row.json != null && row.json.has("title") ? com.on.ebx.layout.runtime.LText.pick(row.json.get("title"), locale, Locale.US)
				: row.label == null ? Messages.get(locale, "explorer.label") : row.label;
		pConfigurator.setTitle(title.isEmpty() ? Messages.get(locale, "explorer.label") : title);
		// the search box and the facets are inputs of EBX's form: without this, leaving the page asks whether to lose changes
		pConfigurator.setDetectionOfLostModificationDisabled(true);
		pConfigurator.setRightButtons(pConfigurator.newCloseButton());
		pConfigurator.setContent(new UserServicePane()
		{
			@Override
			public void writePane(final UserServicePaneContext pPane, final UserServicePaneWriter pWriter)
			{
				ExplorerUserService.this.write(pPane, pWriter, session, row);
			}
		});
	}

	private void write(final UserServicePaneContext pPane, final UserServicePaneWriter pWriter, final Session pSession, final ExplorerStore.Row pRow)
	{
		final Locale locale = pSession.getLocale();
		String refusal = null;
		if (pRow == null || pRow.json == null)
		{
			refusal = Messages.get(locale, this.explorerId >= 0 ? "explorer.notFound" : "explorer.none");
		}
		else if (!pRow.active)
		{
			refusal = Messages.get(locale, "explorer.off");
		}
		else if (!Views.mayOpen(pSession, this.dataset, this.table, pRow.json))
		{
			refusal = Messages.get(locale, "explorer.denied");
		}
		if (refusal != null)
		{
			pWriter.add("<p style=\"padding:16px\">").add(DesignerUserService.esc(refusal)).add("</p>");
			return;
		}
		final ObjectNode boot;
		try
		{
			final View view = Views.view(pSession, this.dataset, this.table, pRow.id, pRow.json);
			boot = this.boot(pPane, pWriter, pSession, pRow, view);
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] explorer " + pRow.id + " not shown: " + ex, ex);
			pWriter.add("<p style=\"padding:16px;color:#C73200\">").add(DesignerUserService.esc(Messages.get(locale, "explorer.failed", ex.getMessage()))).add("</p>");
			return;
		}
		final String base = "/" + LayoutPaths.MODULE_NAME + "/www/";
		pWriter.add("<link rel=\"stylesheet\" href=\"").add(base).add("explorer.css?v=").add(DesignerUserService.ASSET_VERSION).add("\">");
		pWriter.add("<div id=\"ldx-run\" class=\"ldx-run\"><p class=\"ldx-loading\">").add(DesignerUserService.esc(Messages.get(locale, "explorer.loading")))
			.add("</p></div>");
		String json;
		try
		{
			json = LayoutStore.json().writeValueAsString(boot);
		}
		catch (final Exception ex)
		{
			json = "{}";
		}
		final String safe = json.replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
		pWriter.addJS_cr("window.LDX_RUN=" + safe + ";");
		pWriter.addJS_cr("(function(){var s=document.createElement('script');s.src='" + base + "explorer.js?v=" + DesignerUserService.ASSET_VERSION
			+ "';document.body.appendChild(s);})();");
	}

	private ObjectNode boot(final UserServicePaneContext pPane, final UserServicePaneWriter pWriter, final Session pSession, final ExplorerStore.Row pRow,
		final View pView)
	{
		final Repository repository = this.dataset.getHome().getRepository();
		final ObjectNode boot = LayoutStore.json().createObjectNode();
		boot.put("explorer", pRow.id);
		boot.put("locale", pSession.getLocale().toLanguageTag());
		boot.put("defaultLocale", repository.getDefaultLocale().toLanguageTag());
		boot.put("user", pSession.getUserReference().getUserId());
		if (pRow.json.has("title"))
		{
			boot.set("title", pRow.json.get("title"));
		}
		boot.set("config", ExplorerUserService.clientConfig(pView));
		final ObjectNode ajax = boot.putObject("ajax");
		ajax.put("query", pWriter.getURLForAjaxRequest(this.query));
		ajax.put("values", pWriter.getURLForAjaxRequest(this.values));
		if (pSession.isUserInRole(com.orchestranetworks.service.Profile.ADMINISTRATOR))
		{
			ajax.put("load", pWriter.getURLForAjaxRequest(this.load));
		}
		if (ExplorerStore.favoritesAvailable(repository))
		{
			ajax.put("fav", pWriter.getURLForAjaxRequest(this.favorite));
		}
		// pop-up commands: the record, the records a foreign key points to, the actions
		final ObjectNode open = boot.putObject("open");
		open.put("cmd", this.popup(pWriter, this.table.getTablePath(), this.table.getPrimaryKeySpec(), null));
		final ObjectNode links = boot.putObject("links");
		for (final String src : ExplorerUserService.cardSources(pView.spec.layout.path("card")))
		{
			final SchemaNode n = src.startsWith(Spec.DERIVED) ? null : ExplorerUserService.node(this.table, src);
			if (n != null && n.getFacetOnTableReference() != null && n.getFacetOnTableReference().getContainerReference() == null)
			{
				final Path[] pk = new Path[n.getFacetOnTableReference().getTablePrimaryKeyNodes().length];
				for (int i = 0; i < pk.length; i++)
				{
					pk[i] = n.getFacetOnTableReference().getTablePrimaryKeyNodes()[i].getPathInAdaptation();
				}
				links.put(src, this.popup(pWriter, n.getFacetOnTableReference().getTablePath(), pk, null));
			}
		}
		// the labels of the fields a card shows (a part without its own label: lines and key figures)
		final ObjectNode labels = boot.putObject("labels");
		for (final String src : ExplorerUserService.cardSources(pView.spec.layout.path("card")))
		{
			final SchemaNode n = src.startsWith(Spec.DERIVED) ? null : ExplorerUserService.node(this.table, src);
			labels.put(src, n == null ? src.replace(Spec.DERIVED, "") : n.getLabel(pSession.getLocale()));
		}
		final ArrayNode actions = boot.putArray("actions");
		WorkflowEngine engine = null;
		int i = -1;
		for (final JsonNode a : pView.spec.layout.path("card").path("actions"))
		{
			i++;
			final int index = i;
			final String workflow = a.path("workflow").asText("").trim();
			final String service = a.path("service").asText("").trim();
			try
			{
				if (!workflow.isEmpty())
				{
					if (engine == null)
					{
						engine = WorkflowEngine.getFromRepository(repository, pSession);
					}
					if (!engine.isUserAuthorizedToLaunch(PublishedProcessKey.forName(workflow)))
					{
						actions.addNull();
						continue;
					}
					actions.add(this.popup(pWriter, this.table.getTablePath(), this.table.getPrimaryKeySpec(), wc -> {
						wc.setService(ServiceKey.forModuleServiceName(LayoutPaths.MODULE_NAME, LayoutPaths.ACTION_SERVICE));
						wc.setAdditionalParameter(LayoutPaths.EXPLORER_PARAM, String.valueOf(pRow.id));
						wc.setAdditionalParameter(LayoutPaths.ACTION_PARAM, String.valueOf(index));
						if (this.perspective != null)
						{
							wc.setAdditionalParameter(LayoutPaths.PERSPECTIVE_PARAM, this.perspective);
						}
					}));
				}
				else if (!service.isEmpty())
				{
					final ServiceKey key = ServiceKey.parse(service);
					actions.add(this.popup(pWriter, this.table.getTablePath(), this.table.getPrimaryKeySpec(), wc -> wc.setService(key)));
				}
				else
				{
					actions.addNull();
				}
			}
			catch (final RuntimeException ex)
			{
				// a publication or a service absent on this instance: no button
				LoggingCategory.getKernel().warn("[layout-designer] explorer " + pRow.id + " action " + i + " not drawn: " + ex.getMessage());
				actions.addNull();
			}
		}
		boot.put("pk", this.table.getPrimaryKeySpec().length);
		try
		{
			final com.orchestranetworks.ui.perspective.Perspective p = pPane.getCurrentPerspective();
			if (p != null && p.getTheme() != null && p.getTheme().getMainColor() != null)
			{
				boot.put("main", p.getTheme().getMainColor());
			}
		}
		catch (final RuntimeException ex)
		{
			// no perspective theme
		}
		return boot;
	}

	private interface Setup
	{
		void apply(UIHttpManagerComponent pComponent);
	}

	/** A pop-up command on one record of a table, the record's key values left as LDXMARK0, LDXMARK1, … */
	private String popup(final UserServicePaneWriter pWriter, final Path pTable, final Path[] pKey, final Setup pSetup)
	{
		try
		{
			final StringBuilder xpath = new StringBuilder(pTable.format()).append('[');
			for (int i = 0; i < pKey.length; i++)
			{
				final String p = pKey[i].format();
				xpath.append(i == 0 ? "" : " and ").append(p.startsWith(".") ? p : "." + (p.startsWith("/") ? p : "/" + p)).append("='").append(ExplorerUserService.MARK)
					.append(i).append('\'');
			}
			xpath.append(']');
			final UIHttpManagerComponent wc = pWriter.createWebComponentForSubSession();
			wc.select(this.dataset.getHome().getKey(), this.dataset.getAdaptationName(), xpath.toString());
			if (pSetup != null)
			{
				pSetup.apply(wc);
			}
			return pWriter.buildButtonPreview(wc).getJavaScriptCommand();
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] explorer: no pop-up on " + pTable.format() + ": " + ex.getMessage());
			return "";
		}
	}

	/** What the page draws: the user's variant, without what their roles or EBX permissions hide. */
	static ObjectNode clientConfig(final View pView)
	{
		final ObjectNode out = LayoutStore.json().createObjectNode();
		final JsonNode layout = pView.spec.layout;
		final ObjectNode search = out.putObject("search");
		search.put("on", !pView.search.isEmpty());
		if (layout.path("search").has("placeholder"))
		{
			search.set("placeholder", layout.path("search").get("placeholder"));
		}
		final ArrayNode facets = out.putArray("facets");
		for (final Spec.Facet f : pView.facets)
		{
			final ObjectNode o = facets.addObject();
			o.put("id", f.id);
			o.put("kind", f.kind);
			if (f.title != null)
			{
				o.set("title", f.title);
			}
			o.put("widget", f.widget);
			o.put("open", f.open);
			o.put("top", f.top);
			if ("flags".equals(f.kind))
			{
				final ArrayNode flags = o.putArray("flags");
				for (final Spec.Flag fl : f.flags)
				{
					final ObjectNode x = flags.addObject();
					x.put("id", fl.id);
					x.put("tone", fl.tone);
					if (fl.label != null)
					{
						x.set("label", fl.label);
					}
				}
			}
		}
		final ArrayNode tiles = out.putArray("tiles");
		for (final Spec.Tile t : pView.tiles)
		{
			final ObjectNode o = tiles.addObject();
			o.put("id", t.id);
			o.put("tone", t.tone);
			if (t.label != null)
			{
				o.set("label", t.label);
			}
		}
		final ArrayNode sorts = out.putArray("sorts");
		for (final Spec.Sort s : pView.sorts)
		{
			final ObjectNode o = sorts.addObject();
			o.put("i", pView.spec.sorts.indexOf(s));
			if (s.label != null)
			{
				o.set("label", s.label);
			}
		}
		out.put("pageSize", pView.spec.pageSize);
		final ObjectNode card = layout.path("card").isObject() ? ((ObjectNode) layout.get("card")).deepCopy() : out.objectNode();
		for (final String list : new String[] { "overline", "subtitle", "lines", "tags", "kpis" })
		{
			final JsonNode parts = card.path(list);
			if (parts instanceof ArrayNode)
			{
				for (int i = parts.size() - 1; i >= 0; i--)
				{
					final JsonNode p = parts.get(i);
					final String src = Spec.source(p);
					if (src != null && pView.hidden.contains(src) || p.hasNonNull("flags") && !ExplorerUserService.hasFacet(pView, p.get("flags").asText()))
					{
						((ArrayNode) parts).remove(i);
					}
				}
			}
		}
		for (final String one : new String[] { "pill", "gauge", "image" })
		{
			final String src = card.path(one).isObject() ? Spec.source(card.get(one)) : null;
			if (src != null && pView.hidden.contains(src))
			{
				card.remove(one);
			}
		}
		if (ExplorerUserService.titleSource(card) != null && pView.hidden.contains(ExplorerUserService.titleSource(card)))
		{
			card.remove("title");
		}
		out.set("card", card);
		return out;
	}

	private static boolean hasFacet(final View pView, final String pId)
	{
		for (final Spec.Facet f : pView.facets)
		{
			if (f.id.equals(pId))
			{
				return true;
			}
		}
		return false;
	}

	/** Every source a card reads: title, image, pill, gauge, parts. */
	static Set<String> cardSources(final JsonNode pCard)
	{
		final Set<String> out = new LinkedHashSet<String>();
		final String title = ExplorerUserService.titleSource(pCard);
		if (title != null)
		{
			out.add(title);
		}
		for (final String one : new String[] { "pill", "gauge", "image" })
		{
			if (pCard.path(one).isObject() && Spec.source(pCard.get(one)) != null)
			{
				out.add(Spec.source(pCard.get(one)));
			}
		}
		for (final String list : new String[] { "overline", "subtitle", "lines", "tags", "kpis" })
		{
			for (final JsonNode p : pCard.path(list))
			{
				final String s = Spec.source(p);
				if (s != null)
				{
					out.add(s);
				}
			}
		}
		return out;
	}

	/** The card's title: a path, or an object {"field"} / {"derived"}; null when the record's label is used. */
	static String titleSource(final JsonNode pCard)
	{
		final JsonNode t = pCard.get("title");
		if (t == null || t.isNull())
		{
			return null;
		}
		final String s = t.isTextual() ? t.asText() : Spec.source(t);
		return s == null || s.isEmpty() ? null : s;
	}

	static SchemaNode node(final AdaptationTable pTable, final String pPath)
	{
		try
		{
			return pTable.getTableOccurrenceRootNode().getNode(LayoutPaths.relative(pPath));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	// ----- AJAX -----------------------------------------------------------------------------------------------------

	private View view(final UserServiceAjaxContext pContext)
	{
		final ExplorerStore.Row row = this.row();
		if (row == null || row.json == null || !row.active || !Views.mayOpen(pContext.getSession(), this.dataset, this.table, row.json))
		{
			return null;
		}
		return Views.view(pContext.getSession(), this.dataset, this.table, row.id, row.json);
	}

	/**
	 * One page (parameters: q text, f selection "facet=v1|v2;…", tile, sort, off, lim, more "facet:20;…", fq "facet:text;…"
	 * with ";" ":" "%" written %3B %3A %25).
	 */
	private final UserServiceAjaxRequest query = (pContext, pResponse) -> {
		final Session session = pContext.getSession();
		final ObjectNode out = LayoutStore.json().createObjectNode();
		try
		{
			final View view = this.view(pContext);
			if (view == null)
			{
				out.put("error", Messages.get(session.getLocale(), "explorer.denied"));
			}
			else
			{
				final Query.Request request = new Query.Request();
				request.text = pContext.getParameter("q");
				request.selected.putAll(Query.parseSelection(pContext.getParameter("f")));
				request.tile = pContext.getParameter("tile");
				request.sort = ExplorerUserService.integer(pContext.getParameter("sort"), 0, 0, 100);
				for (final Map.Entry<String, String> e : ExplorerUserService.pairs(pContext.getParameter("more")).entrySet())
				{
					request.shown.put(e.getKey(), Integer.valueOf(ExplorerUserService.integer(e.getValue(), 10, 1, Query.MAX_VALUES)));
				}
				request.facetSearch.putAll(ExplorerUserService.pairs(pContext.getParameter("fq")));
				request.favorites = ExplorerStore.favorites(this.dataset.getHome().getRepository(), session.getUserReference().getUserId(), this.explorerId, this.dataset);
				final int off = ExplorerUserService.integer(pContext.getParameter("off"), 0, 0, Integer.MAX_VALUE);
				final int lim = ExplorerUserService.integer(pContext.getParameter("lim"), view.spec.pageSize, 1, 96);
				// an administrator may ask what EBX was asked, with each statement's time
				final boolean explain = "1".equals(pContext.getParameter("explain")) && session.isUserInRole(com.orchestranetworks.service.Profile.ADMINISTRATOR);
				final long start = System.currentTimeMillis();
				// set (or cleared) at every call: a thread of the pool never keeps an earlier experiment
				Engine.preferSql(explain && "sql".equals(pContext.getParameter("route")));
				Engine.sequential(explain && "0".equals(pContext.getParameter("parallel")));
				if (explain)
				{
					Engine.startTrace();
				}
				final Query.Result result = Query.run(view, request, off, lim);
				out.put("all", result.all);
				out.put("total", result.total);
				out.put("off", off);
				final ObjectNode tiles = out.putObject("tiles");
				result.tiles.forEach((k, v) -> tiles.put(k, v.intValue()));
				final ObjectNode facets = out.putObject("facets");
				result.facets.forEach((k, page) -> facets.set(k, ExplorerUserService.page(page)));
				final ArrayNode cards = out.putArray("cards");
				final Engine.Cards extra = ExplorerUserService.cardExtras(view, result.keys);
				for (final String k : result.keys)
				{
					final ObjectNode c = this.card(view, k, session, request.favorites, extra);
					if (c != null)
					{
						cards.add(c);
					}
				}
				if (explain)
				{
					Engine.preferSql(false);
					out.put("ms", System.currentTimeMillis() - start);
					final ArrayNode trace = out.putArray("trace");
					for (final String[] t : Engine.trace())
					{
						trace.addObject().put("kind", t[0]).put("ms", Long.parseLong(t[2])).put("text", t[1]);
					}
				}
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] explorer query failed: " + ex, ex);
			out.put("error", String.valueOf(ex.getMessage()));
		}
		pResponse.getWriter().add(out.toString());
	};

	/**
	 * Administrators only: a load test. {@code n} threads (1-300) each play a user asking the same page at the same time
	 * (parameters of the query: q, f, tile, sort), cards included, nothing answered from the minute's cache; {@code rounds}
	 * times each. Answers the response times (ms) and the errors. The threads share the administrator's session.
	 */
	private final UserServiceAjaxRequest load = (pContext, pResponse) -> {
		final Session session = pContext.getSession();
		final ObjectNode out = LayoutStore.json().createObjectNode();
		if (!session.isUserInRole(com.orchestranetworks.service.Profile.ADMINISTRATOR))
		{
			out.put("error", "administrators only");
			pResponse.getWriter().add(out.toString());
			return;
		}
		final View view = this.view(pContext);
		if (view == null)
		{
			out.put("error", Messages.get(session.getLocale(), "explorer.denied"));
			pResponse.getWriter().add(out.toString());
			return;
		}
		final int n = ExplorerUserService.integer(pContext.getParameter("n"), 10, 1, 300);
		final int rounds = ExplorerUserService.integer(pContext.getParameter("rounds"), 1, 1, 10);
		final String q = pContext.getParameter("q"), f = pContext.getParameter("f"), tile = pContext.getParameter("tile");
		final int sort = ExplorerUserService.integer(pContext.getParameter("sort"), 0, 0, 100);
		// cache=1: the simulated users share answers like real users who see the whole table
		final boolean keep = "1".equals(pContext.getParameter("cache"));
		final java.util.List<Long> times = java.util.Collections.synchronizedList(new java.util.ArrayList<Long>());
		final java.util.List<String> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
		final java.util.concurrent.ExecutorService users = java.util.concurrent.Executors.newFixedThreadPool(n, r -> {
			final Thread t = new Thread(r, "layout-designer-load-test");
			t.setDaemon(true);
			t.setContextClassLoader(ExplorerUserService.class.getClassLoader());
			return t;
		});
		final java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
		final long wall = System.currentTimeMillis();
		try
		{
			for (int i = 0; i < n; i++)
			{
				users.execute(() -> {
					try
					{
						go.await();
					}
					catch (final InterruptedException ex)
					{
						return;
					}
					Engine.noCache(!keep);
					try
					{
						for (int r = 0; r < rounds; r++)
						{
							final long t0 = System.currentTimeMillis();
							try
							{
								final Query.Request request = new Query.Request();
								request.text = q;
								request.selected.putAll(Query.parseSelection(f));
								request.tile = tile;
								request.sort = sort;
								final Query.Result result = Query.run(view, request, 0, view.spec.pageSize);
								ExplorerUserService.cardExtras(view, result.keys);
								times.add(Long.valueOf(System.currentTimeMillis() - t0));
							}
							catch (final RuntimeException ex)
							{
								errors.add(String.valueOf(ex.getMessage()));
							}
						}
					}
					finally
					{
						Engine.noCache(false);
					}
				});
			}
			go.countDown();
			users.shutdown();
			users.awaitTermination(30, java.util.concurrent.TimeUnit.MINUTES);
		}
		catch (final InterruptedException ex)
		{
			errors.add("interrupted");
		}
		finally
		{
			users.shutdownNow();
		}
		final java.util.List<Long> sorted = new java.util.ArrayList<Long>(times);
		java.util.Collections.sort(sorted);
		out.put("users", n);
		out.put("rounds", rounds);
		out.put("answers", sorted.size());
		out.put("wallMs", System.currentTimeMillis() - wall);
		if (!sorted.isEmpty())
		{
			out.put("minMs", sorted.get(0).longValue());
			out.put("medianMs", sorted.get(sorted.size() / 2).longValue());
			out.put("p90Ms", sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.9) - 1)).longValue());
			out.put("maxMs", sorted.get(sorted.size() - 1).longValue());
		}
		out.put("errors", errors.size());
		if (!errors.isEmpty())
		{
			out.put("firstError", errors.get(0));
		}
		LoggingCategory.getKernel().info("[layout-designer] explorer load test: " + out);
		pResponse.getWriter().add(out.toString());
	};

	/** Values of one source, for a long facet list: f source, q text, off, n. */
	private final UserServiceAjaxRequest values = (pContext, pResponse) -> {
		final ObjectNode out = LayoutStore.json().createObjectNode();
		try
		{
			final View view = this.view(pContext);
			final String source = pContext.getParameter("f");
			if (view == null || source == null || view.hidden.contains(source) || view.model.src(source) == null)
			{
				out.put("total", 0);
				out.putArray("items");
			}
			else
			{
				final Query.FacetPage page = Query.values(view, source, pContext.getParameter("q"), ExplorerUserService.integer(pContext.getParameter("off"), 0, 0,
					Integer.MAX_VALUE), ExplorerUserService.integer(pContext.getParameter("n"), 10, 1, Query.MAX_VALUES));
				out.setAll(ExplorerUserService.page(page));
			}
		}
		catch (final RuntimeException ex)
		{
			out.put("error", String.valueOf(ex.getMessage()));
		}
		pResponse.getWriter().add(out.toString());
	};

	/** Stars or unstars a record the user may see (parameter k, the record key): {"favorite": true|false} or {"error"}. */
	private final UserServiceAjaxRequest favorite = (pContext, pResponse) -> {
		final ObjectNode out = LayoutStore.json().createObjectNode();
		try
		{
			final View view = this.view(pContext);
			final String key = pContext.getParameter("k");
			if (view == null || key == null || ExplorerUserService.visibleRecord(this.table, key, pContext.getSession()) == null)
			{
				out.put("error", Messages.get(pContext.getSession().getLocale(), "explorer.unknownRecord"));
			}
			else
			{
				out.put("favorite", ExplorerStore.toggleFavorite(this.dataset.getHome().getRepository(), pContext.getSession(), this.explorerId, this.dataset, key));
			}
		}
		catch (final Exception ex)
		{
			out.put("error", String.valueOf(ex.getMessage()));
		}
		pResponse.getWriter().add(out.toString());
	};

	static ObjectNode page(final Query.FacetPage pPage)
	{
		final ObjectNode o = LayoutStore.json().createObjectNode();
		o.put("total", pPage.total);
		if (pPage.more)
		{
			o.put("more", true);
		}
		final ArrayNode items = o.putArray("items");
		for (final Query.Item it : pPage.items)
		{
			final ObjectNode x = items.addObject();
			x.put("v", it.v);
			x.put("l", it.l);
			x.put("n", it.n);
			if (it.d > 0)
			{
				x.put("d", it.d);
			}
			if (it.sel)
			{
				x.put("sel", true);
			}
		}
		return o;
	}

	/** One card: the record's key values, label, favorite flag and the values its card shows, as this user may read them. */
	private ObjectNode card(final View pView, final String pKey, final Session pSession, final Set<String> pFavorites, final Engine.Cards pExtra)
	{
		final String key = pKey;
		final Adaptation record = ExplorerUserService.visibleRecord(this.table, key, pSession);
		if (record == null)
		{
			return null;
		}
		final Locale locale = pSession.getLocale();
		final ObjectNode c = LayoutStore.json().createObjectNode();
		c.put("k", key);
		c.put("l", record.getLabel(locale));
		final ArrayNode pk = c.putArray("pk");
		for (final Path p : this.table.getPrimaryKeySpec())
		{
			pk.add(Cond.key(record.get(p)));
		}
		if (pFavorites.contains(key))
		{
			c.put("fav", true);
		}
		final ObjectNode v = c.putObject("v");
		final JsonNode card = pView.spec.layout.path("card");
		ValueContext vc = null;
		for (final String src : ExplorerUserService.cardSources(card))
		{
			if (pView.hidden.contains(src))
			{
				continue;
			}
			if (src.startsWith(Spec.DERIVED))
			{
				final Map<String, Object> d = pExtra.derived.get(key);
				Object value = d == null ? null : d.get(src);
				if (value == null)
				{
					value = ExplorerUserService.provided(pView, src, record);
				}
				final ObjectNode x = ExplorerUserService.derivedValue(this.table, pSession, value);
				if (x != null)
				{
					v.set(src, x);
				}
				continue;
			}
			final SchemaNode node = ExplorerUserService.node(this.table, src);
			if (node == null || pSession.getPermissions().getNodeAccessPermission(node, record).isHidden())
			{
				continue;
			}
			final Object raw = record.get(node.getPathInAdaptation());
			if (raw == null || raw instanceof String && ((String) raw).isEmpty() || raw instanceof Collection && ((Collection<?>) raw).isEmpty())
			{
				continue;
			}
			if (vc == null)
			{
				vc = record.createValueContext();
			}
			v.set(src, ExplorerUserService.fieldValue(record, node, raw, vc, locale));
		}
		// the flags of a "flags" part come from the facet
		for (final String list : new String[] { "overline", "subtitle", "lines", "tags", "kpis" })
		{
			for (final JsonNode p : card.path(list))
			{
				if (p.hasNonNull("flags"))
				{
					final ArrayNode f = c.with("flags").putArray(p.get("flags").asText());
					final Map<String, List<String>> raised = pExtra.flags.get(key);
					for (final String id : raised == null ? java.util.Collections.<String> emptyList()
						: raised.getOrDefault(p.get("flags").asText(), java.util.Collections.<String> emptyList()))
					{
						f.add(id);
					}
				}
			}
		}
		// actions whose condition does not hold on this record
		int i = -1;
		for (final JsonNode a : card.path("actions"))
		{
			i++;
			if (a.has("cond") && !Cond.test(a.get("cond"), com.on.ebx.layout.explorer.IndexBuilderAccess.lookup(record)))
			{
				c.withArray("off").add(i);
			}
		}
		return c;
	}

	private static ObjectNode fieldValue(final Adaptation pRecord, final SchemaNode pNode, final Object pRaw, final ValueContext pContext, final Locale pLocale)
	{
		final ObjectNode x = LayoutStore.json().createObjectNode();
		if (ModelExport.kind(pNode) != null && "media".equals(ModelExport.kind(pNode)))
		{
			final String[] asset = Media.urlAndName(pRecord, pRaw, pLocale);
			if (asset != null)
			{
				x.put("url", asset[0]);
				x.put("name", asset[1]);
			}
			return x;
		}
		if (pRaw instanceof Collection)
		{
			final ArrayNode ks = x.putArray("ks");
			final ArrayNode ls = x.putArray("ls");
			for (final Object o : (Collection<?>) pRaw)
			{
				ks.add(Cond.key(o));
				ls.add(ExplorerUserService.display(pNode, o, pContext, pLocale));
			}
			return x;
		}
		x.put("k", Cond.key(pRaw));
		x.put("l", ExplorerUserService.display(pNode, pRaw, pContext, pLocale));
		if (pRaw instanceof Number)
		{
			x.put("n", ((Number) pRaw).doubleValue());
		}
		if (pRaw instanceof Date)
		{
			x.put("d", new SimpleDateFormat("yyyy-MM-dd").format((Date) pRaw));
		}
		if (pNode.getFacetOnTableReference() != null && pRaw instanceof String)
		{
			try
			{
				final ArrayNode pk = x.putArray("pk");
				for (final Object o : pNode.getFacetOnTableReference().getTablePrimaryKeyValues((String) pRaw))
				{
					pk.add(Cond.key(o));
				}
			}
			catch (final Exception ex)
			{
				x.remove("pk");
			}
		}
		return x;
	}

	private static String display(final SchemaNode pNode, final Object pValue, final ValueContext pContext, final Locale pLocale)
	{
		try
		{
			final String s = pNode.displayOccurrence(pValue, true, pContext, pLocale);
			return s == null ? Cond.key(pValue) : s;
		}
		catch (final RuntimeException ex)
		{
			return Cond.key(pValue);
		}
	}

	/** A record of the table the user may see, by its formatted key; null otherwise (gone since, or hidden by a rule). */
	static Adaptation visibleRecord(final AdaptationTable pTable, final String pKey, final Session pSession)
	{
		try
		{
			final Adaptation r = pTable.lookupAdaptationByPrimaryKey(PrimaryKey.parseString(pKey));
			return r == null || pSession.getPermissions().getAdaptationAccessPermission(r).isHidden() ? null : r;
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** The computed values and flags the cards of a page show, asked to EBX once for the page. */
	private static Engine.Cards cardExtras(final View pView, final List<String> pKeys)
	{
		final JsonNode card = pView.spec.layout.path("card");
		final Set<String> derived = new LinkedHashSet<String>();
		for (final String src : ExplorerUserService.cardSources(card))
		{
			if (src.startsWith(Spec.DERIVED) && !pView.hidden.contains(src))
			{
				derived.add(src);
			}
		}
		final Set<String> flags = new LinkedHashSet<String>();
		for (final String list : new String[] { "overline", "subtitle", "lines", "tags", "kpis" })
		{
			for (final JsonNode p : card.path(list))
			{
				if (p.hasNonNull("flags"))
				{
					flags.add(p.get("flags").asText());
				}
			}
		}
		return Engine.cards(pView, pKeys, derived, flags);
	}

	/** A value computed in Java ({@code provided}): asked to its provider for the record shown. */
	private static Object provided(final View pView, final String pSource, final Adaptation pRecord)
	{
		final JsonNode def = pView.spec.derived.get(pSource.substring(Spec.DERIVED.length()));
		if (def == null || !"provided".equals(def.path("type").asText()))
		{
			return null;
		}
		final com.on.ebx.layout.runtime.ExplorerValues.Provider p = com.on.ebx.layout.runtime.ExplorerValues.get(def.path("name").asText(""));
		try
		{
			return p == null ? null : p.value(pRecord);
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** A computed value: a number, a text, true/false, or the records it names (suspects) with their labels and keys. */
	private static ObjectNode derivedValue(final AdaptationTable pTable, final Session pSession, final Object pValue)
	{
		if (pValue == null)
		{
			return null;
		}
		final ObjectNode x = LayoutStore.json().createObjectNode();
		if (pValue instanceof Collection)
		{
			final ArrayNode ks = x.putArray("ks");
			final ArrayNode ls = x.putArray("ls");
			for (final Object o : (Collection<?>) pValue)
			{
				final String k = Cond.key(o);
				final Adaptation r = ExplorerUserService.visibleRecord(pTable, k, pSession);
				if (r == null)
				{
					// a record the user may not see is not named
					continue;
				}
				ks.add(k);
				ls.add(r.getLabel(pSession.getLocale()));
			}
			return ks.size() == 0 ? null : x;
		}
		x.put("k", Cond.key(pValue));
		x.put("l", Cond.key(pValue));
		if (pValue instanceof Number)
		{
			x.put("n", ((Number) pValue).doubleValue());
		}
		return x;
	}

	/** {@code "a:20;b:5"} as a map; values escaped like the selection. */
	private static Map<String, String> pairs(final String pText)
	{
		final Map<String, String> out = new java.util.HashMap<String, String>();
		if (pText == null)
		{
			return out;
		}
		for (final String part : pText.split(";"))
		{
			final int i = part.indexOf(':');
			if (i > 0)
			{
				out.put(part.substring(0, i), part.substring(i + 1).replace("%3B", ";").replace("%3A", ":").replace("%25", "%"));
			}
		}
		return out;
	}

	private static int integer(final String pValue, final int pDefault, final int pMin, final int pMax)
	{
		try
		{
			return Math.max(pMin, Math.min(pMax, Integer.parseInt(pValue.trim())));
		}
		catch (final RuntimeException ex)
		{
			return pDefault;
		}
	}

	@Override
	public void validate(final UserServiceValidateContext<TableViewEntitySelection> pContext)
	{
		// nothing posted
	}

	@Override
	public UserServiceEventOutcome processEventOutcome(final UserServiceProcessEventOutcomeContext<TableViewEntitySelection> pContext, final UserServiceEventOutcome pOutcome)
	{
		return pOutcome;
	}
}
