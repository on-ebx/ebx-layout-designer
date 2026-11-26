package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.instance.ValueContext;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.LoggingCategory;
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
	private List<ReportItems.Item> report;

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
		for (final JsonNode p : count)
		{
			n += this.count(p.asText());
		}
		return n > 0 ? label + " (" + n + ")" : label;
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
	String display(final String pStoredPath)
	{
		final SchemaNode n = this.node(pStoredPath);
		if (n == null)
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
		try
		{
			return this.context.getValueContext().getValue(LayoutPaths.relative(pStoredPath));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
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
		if (a != null && a.getAssociationLink() != null)
		{
			try
			{
				final RequestResult rows = a.getAssociationLink().getAssociationResult(this.record);
				try
				{
					n = rows.getSize();
				}
				finally
				{
					rows.close();
				}
			}
			catch (final RuntimeException ex)
			{
				LoggingCategory.getKernel().warn("[layout-designer] rows of " + pAssociation + " not counted: " + ex.getMessage());
			}
		}
		this.counts.put(pAssociation, Integer.valueOf(n));
		return n;
	}

	List<Adaptation> rows(final String pAssociation, final int pMax)
	{
		final List<Adaptation> out = new ArrayList<Adaptation>();
		final SchemaNode a = this.node(pAssociation);
		if (this.record == null || a == null || a.getAssociationLink() == null)
		{
			return out;
		}
		try
		{
			final RequestResult rows = a.getAssociationLink().getAssociationResult(this.record);
			try
			{
				for (Adaptation r = rows.nextAdaptation(); r != null && out.size() < pMax; r = rows.nextAdaptation())
				{
					out.add(r);
				}
			}
			finally
			{
				rows.close();
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] rows of " + pAssociation + " not read: " + ex.getMessage());
		}
		return out;
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
			this.report = items;
		}
		return this.report;
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
		final String t = this.text(pBlock.get("title"));
		if (!t.isEmpty())
		{
			return t;
		}
		if ("grid".equals(pBlock.path("type").asText()))
		{
			return this.label(pBlock.path("a").asText());
		}
		return "";
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
						else if ("custom".equals(type) || "tree".equals(type))
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
