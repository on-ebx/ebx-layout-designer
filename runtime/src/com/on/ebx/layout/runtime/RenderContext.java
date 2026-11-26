package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.Request;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.instance.ValueContext;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.ui.form.UIFormContext;

/** What every block of one form display needs: the record, the locale, the layout and where each field landed. */
final class RenderContext
{
	/** Where a field or an association is placed in the layout, for the "go to field" links. */
	static final class Location
	{
		final int tab;
		final String anchor;
		final String block;
		final String field;

		Location(final int pTab, final String pAnchor, final String pBlock, final String pField)
		{
			this.tab = pTab;
			this.anchor = pAnchor;
			this.block = pBlock;
			this.field = pField;
		}
	}

	final UIFormContext context;
	final Locale locale;
	final Locale defaultLocale;
	final Adaptation record;
	final boolean creating;
	final SchemaNode root;
	final JsonNode layout;
	final Map<String, Location> locations = new HashMap<String, Location>();
	private final Map<String, Integer> counts = new HashMap<String, Integer>();
	/** Associations whose count stopped at {@link #COUNT_CAP}: shown "1000+". */
	private final Set<String> capped = new HashSet<String>();
	/** The rows of each association, read once per display and shared by the blocks and tabs that use it (audit L2-05). */
	private final SharedRows<Adaptation> shared = new SharedRows<Adaptation>();
	/** The style sheet and scripts of the form, written by the first pane only (audit L2-17). */
	final FormAssets assets = new FormAssets();
	/** The perspective's main and focus colours, null when it gives none (audit L2-20). */
	final String themeMain;
	final String themeFocus;
	/** Fields whose changes re-run the dynamic behaviours (their widget calls ldfDyn). */
	final java.util.Set<String> controllers = new java.util.HashSet<String>();
	/** Values computed once per display (a configuration table read by a behaviour). */
	final Map<String, Object> cache = new HashMap<String, Object>();
	private List<ReportItems.Item> report;

	/**
	 * Largest count shown as an exact number; a larger association shows "1000+" (audit L2-05). Read at each display:
	 * {@code -Debx.layoutDesigner.countCap}, default 1000.
	 */
	static int countCap()
	{
		return Integer.getInteger("ebx.layoutDesigner.countCap", 1000).intValue();
	}

	RenderContext(final UIFormContext pContext, final JsonNode pLayout)
	{
		this.context = pContext;
		this.locale = pContext.getLocale();
		Locale def = null;
		try
		{
			def = Repository.getDefault().getDefaultLocale();
		}
		catch (final RuntimeException ex)
		{
			def = Locale.US;
		}
		this.defaultLocale = def;
		this.creating = pContext.isCreatingRecord();
		this.record = this.creating ? null : pContext.getCurrentRecord();
		this.root = pContext.getCurrentTable().getTableOccurrenceRootNode();
		this.layout = pLayout;
		final String[] theme = RenderContext.theme(pContext);
		this.themeMain = theme[0];
		this.themeFocus = theme[1];
		this.index();
	}

	String text(final JsonNode pText)
	{
		return LText.pick(pText, this.locale, this.defaultLocale);
	}

	String tabLabel(final JsonNode pTab)
	{
		final String label = this.text(pTab.get("label"));
		final JsonNode count = pTab.path("count");
		if (!count.isArray() || count.size() == 0 || this.record == null)
		{
			return label;
		}
		int n = 0;
		boolean more = false;
		for (final JsonNode p : count)
		{
			n += this.count(p.asText());
			more |= this.capped.contains(p.asText());
		}
		return n > 0 ? label + " (" + n + (more ? "+" : "") + ")" : label;
	}

	SchemaNode node(final String pStoredPath)
	{
		final Path p = LayoutPaths.relative(pStoredPath);
		return p == null ? null : this.root.getNode(p);
	}

	String label(final String pStoredPath)
	{
		final SchemaNode n = this.node(pStoredPath);
		return n == null ? pStoredPath : n.getLabel(this.locale);
	}

	/** The value as EBX displays it in this locale (foreign key label, enumeration label, formatted date). */
	/**
	 * Whether the user may read this field of the record (EBX permissions). Since 1.17.1 a path that is not a node of the
	 * record (a field removed from the data model) is not readable: blocks leave it out instead of showing its path.
	 */
	boolean canRead(final String pStoredPath)
	{
		final SchemaNode n = this.node(pStoredPath);
		return n != null && Access.canRead(n, this.record);
	}

	String display(final String pStoredPath)
	{
		final SchemaNode n = this.node(pStoredPath);
		if (n == null || !Access.canRead(n, this.record))
		{
			return "";
		}
		try
		{
			final ValueContext values = this.context.getValueContext();
			final Object value = values.getValue(LayoutPaths.relative(pStoredPath));
			if (value == null)
			{
				return "";
			}
			final String shown = n.displayOccurrence(value, true, values, this.locale);
			return shown == null ? String.valueOf(value) : shown;
		}
		catch (final RuntimeException ex)
		{
			return "";
		}
	}

	Object raw(final String pStoredPath)
	{
		if (!this.canRead(pStoredPath))
		{
			return null;
		}
		try
		{
			return this.context.getValueContext().getValue(LayoutPaths.relative(pStoredPath));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** The count of an association as shown: "12", or "1000+" when it stopped at the cap. */
	String countText(final String pAssociation)
	{
		final int n = this.count(pAssociation);
		return this.capped.contains(pAssociation) ? n + "+" : String.valueOf(n);
	}

	/** An association opened for this record: the rows, and whether EBX already left out what the user may not see. */
	private static final class Opened
	{
		final RequestResult result;
		final boolean filtered;

		Opened(final RequestResult pResult, final boolean pFiltered)
		{
			this.result = pResult;
			this.filtered = pFiltered;
		}
	}

	/**
	 * Opens the association with the user's session on the request, so that EBX itself leaves out the records they may not
	 * see (CLAUDE.md §9, {@code Request.setSession}): no permission is asked row by row. When the request cannot be rerun
	 * with the session, the unfiltered result is returned and the caller tests each row ({@link Access#canSee}).
	 */
	private Opened open(final SchemaNode pAssociation)
	{
		final RequestResult plain = pAssociation.getAssociationLink().getAssociationResult(this.record);
		final Session session = this.context.getSession();
		if (session != null)
		{
			try
			{
				final Request request = plain.getRequest();
				if (request != null)
				{
					request.setSession(session);
					final RequestResult filtered = request.execute();
					plain.close();
					return new Opened(filtered, true);
				}
			}
			catch (final RuntimeException ex)
			{
				LoggingCategory.getKernel().debug("[layout-designer] association read with the user's own permissions row by row: " + ex);
			}
		}
		return new Opened(plain, false);
	}

	int count(final String pAssociation)
	{
		if (this.record == null)
		{
			return 0;
		}
		final Integer known = this.counts.get(pAssociation);
		if (known != null)
		{
			return known.intValue();
		}
		int n = 0;
		final SchemaNode a = this.node(pAssociation);
		if (a != null && a.getAssociationLink() != null && Access.canRead(a, this.record))
		{
			final int cap = RenderContext.countCap();
			final List<Adaptation> already = this.shared.completeRows(pAssociation);
			if (already != null && already.size() <= cap)
			{
				n = already.size();
			}
			else
			{
				try
				{
					final Opened o = this.open(a);
					try
					{
						if (o.filtered)
						{
							if (o.result.isSizeGreaterOrEqual(cap + 1))
							{
								n = cap;
								this.capped.add(pAssociation);
							}
							else
							{
								n = o.result.getSize();
							}
						}
						else
						{
							final Bounded<Adaptation> c = Bounded.count(o.result.iterator(), cap, new java.util.function.Predicate<Adaptation>()
							{
								@Override
								public boolean test(final Adaptation pRow)
								{
									return Access.canSee(pRow);
								}
							}, 0);
							n = c.count;
							if (c.more)
							{
								this.capped.add(pAssociation);
							}
						}
					}
					finally
					{
						o.result.close();
					}
				}
				catch (final RuntimeException ex)
				{
					LoggingCategory.getKernel().warn("[layout-designer] rows of " + pAssociation + " not counted: " + ex.getMessage());
				}
			}
		}
		this.counts.put(pAssociation, Integer.valueOf(n));
		return n;
	}

	List<Adaptation> rows(final String pAssociation, final int pMax)
	{
		final SchemaNode a = this.node(pAssociation);
		if (this.record == null || a == null || a.getAssociationLink() == null || !Access.canRead(a, this.record))
		{
			return new ArrayList<Adaptation>();
		}
		return this.shared.get(pAssociation, pMax, new SharedRows.Reader<Adaptation>()
		{
			@Override
			public List<Adaptation> read(final int pLimit)
			{
				final List<Adaptation> out = new ArrayList<Adaptation>();
				try
				{
					final Opened o = RenderContext.this.open(a);
					try
					{
						for (Adaptation r = o.result.nextAdaptation(); r != null && out.size() < pLimit; r = o.result.nextAdaptation())
						{
							if (o.filtered || Access.canSee(r))
							{
								out.add(r);
							}
						}
					}
					finally
					{
						o.result.close();
					}
				}
				catch (final RuntimeException ex)
				{
					LoggingCategory.getKernel().warn("[layout-designer] rows of " + pAssociation + " not read: " + ex.getMessage());
				}
				return out;
			}
		});
	}

	/** How many associations were really opened during this display (a DEBUG log line, and what a test counts). */
	int associationReads()
	{
		return this.shared.reads();
	}

	/** The perspective's main and focus colours, each null when absent or not a plain hex colour. */
	private static String[] theme(final UIFormContext pContext)
	{
		try
		{
			final com.orchestranetworks.ui.perspective.Perspective p = pContext.getCurrentPerspective();
			final com.orchestranetworks.ui.perspective.PerspectiveTheme t = p == null ? null : p.getTheme();
			return t == null ? new String[] { null, null } : new String[] { Css.colour(t.getMainColor()), Css.colour(t.getFocusColor()) };
		}
		catch (final RuntimeException ex)
		{
			return new String[] { null, null };
		}
	}

	List<ReportItems.Item> report()
	{
		if (this.report == null)
		{
			List<ReportItems.Item> items;
			try
			{
				items = ReportItems.read(this.record, this.locale);
			}
			catch (final RuntimeException ex)
			{
				LoggingCategory.getKernel().warn("[layout-designer] validation report not read: " + ex.getMessage());
				items = new ArrayList<ReportItems.Item>();
			}
			// a message about a field the user may not read is not shown (its text often quotes the value)
			final List<ReportItems.Item> readable = new ArrayList<ReportItems.Item>();
			for (final ReportItems.Item i : items)
			{
				if (i.node == null || this.canRead(i.node))
				{
					readable.add(i);
				}
			}
			this.report = readable;
		}
		return this.report;
	}

	/** An XPath predicate on the record: its result, pDefault when there is no record or it cannot run. */
	boolean xpath(final String pCondition, final boolean pDefault)
	{
		return RenderContext.xpath(this.record, pCondition, pDefault);
	}

	/** The same test on any record, without a form (1.23.1: a button is checked again when it is launched). */
	static boolean xpath(final Adaptation pRecord, final String pCondition, final boolean pDefault)
	{
		if (pCondition == null || pCondition.trim().isEmpty() || pRecord == null)
		{
			return pDefault;
		}
		try
		{
			final RequestResult result = pRecord.getContainerTable().createRequestResult(pRecord.toXPathPredicateString() + " and (" + pCondition + ")");
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
			LoggingCategory.getKernel().warn("[layout-designer] condition '" + pCondition + "' not evaluated: " + ex.getMessage());
			return pDefault;
		}
	}

	static String blockId(final JsonNode pBlock, final int pTab, final int pRow, final int pCol, final int pIndex)
	{
		final String id = pBlock.path("id").asText("");
		return (id.isEmpty() ? "t" + pTab + "r" + pRow + "c" + pCol + "b" + pIndex : id).replaceAll("[^A-Za-z0-9_-]", "_");
	}

	static String fieldAnchor(final String pBlockId, final int pIndex)
	{
		return "ldf-" + pBlockId + "-" + pIndex;
	}

	static String blockAnchor(final String pBlockId)
	{
		return "ldb-" + pBlockId;
	}

	String blockTitle(final JsonNode pBlock)
	{
		return BlockTitles.of(pBlock, this.locale, this.defaultLocale, this.root);
	}

	/** Where every field and association lands: the tab, the block, an anchor id. */
	private void index()
	{
		final JsonNode tabs = this.layout.path("tabs");
		for (int t = 0; t < tabs.size(); t++)
		{
			final JsonNode rows = tabs.get(t).path("rows");
			final String tabLabel = this.text(tabs.get(t).get("label"));
			for (int r = 0; r < rows.size(); r++)
			{
				final JsonNode cols = rows.get(r).path("blocks");
				for (int c = 0; c < cols.size(); c++)
				{
					final JsonNode col = cols.get(c);
					for (int k = 0; k < col.size(); k++)
					{
						final JsonNode b = col.get(k);
						final String id = RenderContext.blockId(b, t, r, c, k);
						final String type = b.path("type").asText();
						Behaviors.controllers(b, this.controllers);
						final String title = tabLabel + " › " + this.blockTitle(b);
						if ("panel".equals(type))
						{
							final JsonNode fields = b.path("fields");
							for (int i = 0; i < fields.size(); i++)
							{
								final String p = fields.get(i).path("p").asText();
								if (!this.locations.containsKey(p))
								{
									final String fl = LText.hasText(fields.get(i).get("label")) ? this.text(fields.get(i).get("label")) : this.label(p);
									this.locations.put(p, new Location(t, RenderContext.fieldAnchor(id, i), title, fl));
								}
							}
						}
						else if ("grid".equals(type) && !this.locations.containsKey(b.path("a").asText()))
						{
							this.locations.put(b.path("a").asText(), new Location(t, RenderContext.blockAnchor(id), title, null));
						}
						else if (!"panel".equals(type))
						{
							for (final String key : new String[] { "a", "fk", "field", "from", "to", "zip", "city", "country", "parent", "children" })
							{
								final String p = b.path(key).asText("");
								if (p.startsWith("/") && this.node(p) != null && !this.locations.containsKey(p))
								{
									this.locations.put(p, new Location(t, RenderContext.blockAnchor(id), title, null));
								}
							}
						}
						if ("custom".equals(type) || "tree".equals(type))
						{
							for (final JsonNode p : b.path("covers"))
							{
								if (!this.locations.containsKey(p.asText()))
								{
									this.locations.put(p.asText(), new Location(t, RenderContext.blockAnchor(id), title, null));
								}
							}
						}
					}
				}
			}
		}
	}

	/** The placement of a message's node: the field itself, else its closest placed parent. */
	Location locate(final String pNode)
	{
		if (pNode == null)
		{
			return null;
		}
		String p = pNode;
		while (p != null && !p.isEmpty())
		{
			final Location l = this.locations.get(p);
			if (l != null)
			{
				return l;
			}
			final int slash = p.lastIndexOf('/');
			p = slash <= 0 ? null : p.substring(0, slash);
		}
		// a message on a group: the first placed field of that group
		for (final Map.Entry<String, Location> e : this.locations.entrySet())
		{
			if (e.getKey().startsWith(pNode + "/"))
			{
				return e.getValue();
			}
		}
		return null;
	}
}
