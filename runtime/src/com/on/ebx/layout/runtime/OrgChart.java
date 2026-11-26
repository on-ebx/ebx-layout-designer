package com.on.ebx.layout.runtime;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.PrimaryKey;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.ui.UIHttpManagerComponent;
import com.orchestranetworks.ui.form.UIFormPaneWriter;

/**
 * The "Hierarchy chart" block: the group the record belongs to, through a foreign key to the parent in the same table,
 * drawn in the browser as an org chart centred on the record, a collapsible tree or rings ({@code orgchart.js}). The table
 * is read once into a key → parent index, kept until {@code AdaptationTable.getLastModificationDate()} changes; only the
 * records of the group (at most {@link #MAX_NODES}) are then read for their label, line and colour.
 * <p>
 * Since 1.23.2 (audit L2-07) the index is built by one thread at a time ({@link StampedCache}: the others use the previous
 * one, or wait for the first build), at most {@link #maxIndexLinks()} links are kept (a larger table gives a partial index
 * and the chart says so), at most eight indexes stay in memory, and the entities the user may not see are left out while the
 * group is walked, so they no longer count towards {@link #MAX_NODES}.
 */
final class OrgChart
{
	static final int MAX_NODES = 2000;
	static final String JS = OrgChart.resource("orgchart.js");
	/** The "Value over time" and "Sites on a map" blocks. */
	static final String BLOCKS_JS = OrgChart.resource("blocks.js");

	/** Dot colours: a tone of the "colors" map, or one of six by distinct value. */
	private static final Map<String, String> TONES = new HashMap<String, String>();
	private static final String[] PALETTE = { "#7F17FF", "#1D63B8", "#C26A00", "#1F8A4C", "#B4372E", "#6B6575" };
	static
	{
		OrgChart.TONES.put("ok", "#1F9D5B");
		OrgChart.TONES.put("warn", "#D98200");
		OrgChart.TONES.put("bad", "#D0402A");
		OrgChart.TONES.put("accent", "#7F17FF");
		OrgChart.TONES.put("info", "#3931EC");
		OrgChart.TONES.put("muted", "#9A94A3");
	}

	/** key → parent and parent → children of a table; {@code partial} when the table had more links than the limit. */
	static final class Index
	{
		final Map<String, String> parentOf = new HashMap<String, String>();
		final Map<String, List<String>> kids = new HashMap<String, List<String>>();
		boolean partial;
	}

	private static final StampedCache<Index> INDEXES = new StampedCache<Index>(8);

	/** Most links an index keeps ({@code -Debx.layoutDesigner.orgChart.maxIndexLinks}, default 500 000). */
	static int maxIndexLinks()
	{
		return Integer.getInteger("ebx.layoutDesigner.orgChart.maxIndexLinks", 500000).intValue();
	}

	/**
	 * The index of {@code {key, parent}} pairs, stopping at {@code pMaxLinks} links (then {@code partial}). A pair with no
	 * parent, or whose parent is itself, is no link.
	 */
	static Index build(final java.util.Iterator<String[]> pPairs, final int pMaxLinks)
	{
		final Index ix = new Index();
		while (pPairs.hasNext())
		{
			final String[] kp = pPairs.next();
			if (kp == null || kp[1] == null || kp[1].isEmpty() || kp[1].equals(kp[0]))
			{
				continue;
			}
			if (ix.parentOf.size() >= pMaxLinks)
			{
				ix.partial = true;
				break;
			}
			ix.parentOf.put(kp[0], kp[1]);
			ix.kids.computeIfAbsent(kp[1], x -> new ArrayList<String>()).add(kp[0]);
		}
		return ix;
	}

	private OrgChart()
	{
	}

	static String render(final RenderContext rc, final JsonNode b, final String pId, final UIFormPaneWriter w)
	{
		final String parent = b.path("parent").asText("");
		final SchemaNode node = rc.node(parent);
		if (node == null || node.getFacetOnTableReference() == null)
		{
			return "<div class=\"ldf-more\">" + LayoutPane.esc(RuntimeText.get("setupBlock", rc.locale)) + "</div>";
		}
		final AdaptationTable table = rc.context.getCurrentTable();
		if (!node.getFacetOnTableReference().getTablePath().format().equals(table.getTablePath().format()))
		{
			return "<div class=\"ldf-more\">" + LayoutPane.esc(RuntimeText.get("ocSameTable", rc.locale)) + "</div>";
		}
		if (rc.creating || rc.record == null)
		{
			return "<div class=\"ldf-more\">" + LayoutPane.esc(RuntimeText.get("afterCreate", rc.locale)) + "</div>";
		}
		final Index ix = OrgChart.index(table, LayoutPaths.relative(parent));
		final String me = rc.record.getOccurrencePrimaryKey().format();
		final Map<String, Adaptation> loaded = new HashMap<String, Adaptation>();
		loaded.put(me, rc.record);

		// the root: up the parents, a loop stops the walk
		final List<String> up = new ArrayList<String>();
		final Set<String> seen = new HashSet<String>();
		for (String k = me; k != null && seen.add(k); k = ix.parentOf.get(k))
		{
			up.add(0, k);
		}
		final String root = up.get(0);
		// the group, breadth first from the root; the path to the record always belongs to it
		final Set<String> group = new LinkedHashSet<String>(up);
		final ArrayDeque<String> queue = new ArrayDeque<String>();
		queue.add(root);
		boolean cut = ix.partial;
		while (!queue.isEmpty())
		{
			final List<String> ch = ix.kids.get(queue.poll());
			if (ch == null)
			{
				continue;
			}
			for (final String c : ch)
			{
				if (group.contains(c))
				{
					queue.add(c);
					continue;
				}
				if (OrgChart.resolve(table, loaded, c) == null)
				{
					continue; // an entity the user may not see: left out, and not counted
				}
				if (group.size() >= OrgChart.MAX_NODES)
				{
					cut = true;
					break;
				}
				group.add(c);
				queue.add(c);
			}
		}
		final ObjectNode data = LayoutStore.json().createObjectNode();
		data.put("focus", me);
		data.put("root", root);
		data.put("present", b.path("present").asText("auto"));
		data.put("thr", Math.max(1, b.path("threshold").asInt(24)));
		// how a click opens an entity: "popup" (default, EBX's own modal pop-up over this record: closing it comes back here),
		// "replace" (this record is replaced) or "newTab"
		final String openIn = b.path("openIn").asText(b.path("newTab").asBoolean(false) ? "newTab" : "popup");
		data.put("openIn", openIn);
		data.put("newTab", "newTab".equals(openIn));
		data.put("cut", cut);
		final ObjectNode t = data.putObject("t");
		for (final String k : new String[] { "open", "oneOther", "others", "oneChild", "children", "noChild", "below", "level", "search", "expandAll", "collapseAll", "expand",
			"collapse", "noMatch", "ringsAria", "ringsNote", "switched", "cut" })
		{
			t.put(k, RuntimeText.get("oc." + k, rc.locale));
		}
		final String label = b.path("label").asText("");
		final String sub = b.path("sub").asText("");
		final String color = b.path("color").asText("");
		final JsonNode colors = b.get("colors");
		final Map<String, String> palette = new LinkedHashMap<String, String>();
		final ArrayNode nodes = data.putArray("nodes");
		for (final String k : group)
		{
			final Adaptation r = OrgChart.resolve(table, loaded, k);
			if (r == null)
			{
				continue;
			}
			final ObjectNode n = nodes.addObject();
			n.put("k", k);
			final String p = ix.parentOf.get(k);
			if (p != null && !k.equals(root))
			{
				n.put("p", p);
			}
			final String name = label.isEmpty() ? "" : Values.display(r, label, rc.locale);
			n.put("n", name.isEmpty() ? r.getLabel(rc.locale) : name);
			if (!sub.isEmpty())
			{
				n.put("s", Values.display(r, sub, rc.locale));
			}
			String hex = OrgChart.themed(rc, OrgChart.PALETTE[0]);
			if (!color.isEmpty())
			{
				final String shown = Values.display(r, color, rc.locale);
				final String tone = Values.tone(colors, Values.key(Values.raw(r, color)), shown).replace(" ldf-t-", "");
				if (!tone.isEmpty() && OrgChart.TONES.containsKey(tone))
				{
					hex = OrgChart.themed(rc, OrgChart.TONES.get(tone));
				}
				else
				{
					final String key = shown.isEmpty() ? "—" : shown;
					if (!palette.containsKey(key))
					{
						palette.put(key, OrgChart.themed(rc, OrgChart.PALETTE[palette.size() % OrgChart.PALETTE.length]));
					}
					hex = palette.get(key);
				}
				n.put("cv", shown);
			}
			n.put("col", hex);
			n.put("u", MoreBlocks.recordUrl(rc, r));
			if ("popup".equals(openIn))
			{
				n.put("pv", OrgChart.preview(rc, w, r));
			}
		}
		final String json = data.toString().replace("</", "<\\/");
		final List<String[]> views = new ArrayList<String[]>();
		views.add(new String[] { "org", RuntimeText.get("oc.org", rc.locale) });
		views.add(new String[] { "tree", RuntimeText.get("oc.tree", rc.locale) });
		views.add(new String[] { "sun", RuntimeText.get("oc.rings", rc.locale) });
		final StringBuilder h = new StringBuilder("<div class=\"ldf-oc\" data-oc=\"").append(LayoutPane.esc(pId)).append("\">");
		if (!b.path("hideSwitch").asBoolean(false))
		{
			h.append("<div class=\"ldf-oc-seg\" role=\"group\">");
			for (final String[] v : views)
			{
				h.append("<button type=\"button\" data-v=\"").append(v[0]).append("\">").append(LayoutPane.esc(v[1])).append("</button>");
			}
			h.append("</div>");
		}
		h.append("<div class=\"ldf-oc-body\"><div class=\"ldf-more\">").append(LayoutPane.esc(rc.record.getLabel(rc.locale))).append("</div></div>");
		h.append("<div class=\"ldf-more ldf-oc-note\" hidden></div>");
		h.append("<script type=\"application/json\">").append(json).append("</script></div>");
		return h.toString();
	}

	/** The JavaScript of EBX's standard preview button on the record: its modal pop-up, a sub-session of this form (documented API). */
	private static String preview(final RenderContext rc, final UIFormPaneWriter w, final Adaptation pRecord)
	{
		try
		{
			final UIHttpManagerComponent wc = rc.context.createWebComponentForSubSession();
			wc.selectInstanceOrOccurrence(pRecord);
			return w.buildButtonPreview(wc).getJavaScriptCommand();
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** The perspective's main colour in place of the ON EBX purple (audit L2-20). */
	private static String themed(final RenderContext rc, final String pHex)
	{
		return rc.themeMain != null && "#7F17FF".equals(pHex) ? rc.themeMain : pHex;
	}

	/** The record of a key, read once per display ({@code null} when absent or hidden from the user). */
	private static Adaptation resolve(final AdaptationTable pTable, final Map<String, Adaptation> pLoaded, final String pKey)
	{
		if (!pLoaded.containsKey(pKey))
		{
			pLoaded.put(pKey, OrgChart.lookup(pTable, pKey));
		}
		return pLoaded.get(pKey);
	}

	private static Adaptation lookup(final AdaptationTable pTable, final String pKey)
	{
		try
		{
			final Adaptation r = pTable.lookupAdaptationByPrimaryKey(PrimaryKey.parseString(pKey));
			// an entity the user may not see is left out of the chart (EBX permissions)
			return r != null && Access.canSee(r) ? r : null;
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** key → parent of the whole table, rebuilt (by one thread) when the table has changed since the last build. */
	private static Index index(final AdaptationTable pTable, final Path pParent)
	{
		final Adaptation dataset = pTable.getContainerAdaptation();
		final String id = dataset.getHome().getKey().getName() + "|" + dataset.getAdaptationName().getStringName() + "|" + pTable.getTablePath().format() + "|"
			+ pParent.format();
		return OrgChart.INDEXES.get(id, pTable.getLastModificationDate(), new StampedCache.Loader<Index>()
		{
			@Override
			public Index load()
			{
				final long t0 = System.currentTimeMillis();
				// the whole table, without a session: the index is shared; what a user may not see is left out when it is walked
				final RequestResult rows = pTable.createRequest().execute();
				final Index ix;
				try
				{
					final java.util.Iterator<Adaptation> it = rows.iterator();
					ix = OrgChart.build(new java.util.Iterator<String[]>()
					{
						@Override
						public boolean hasNext()
						{
							return it.hasNext();
						}

						@Override
						public String[] next()
						{
							final Adaptation r = it.next();
							final Object p = r == null ? null : r.get(pParent);
							return r == null ? null : new String[] { r.getOccurrencePrimaryKey().format(), p == null ? null : String.valueOf(p) };
						}
					}, OrgChart.maxIndexLinks());
				}
				finally
				{
					rows.close();
				}
				final long ms = System.currentTimeMillis() - t0;
				if (ms > 500 || ix.partial)
				{
					LoggingCategory.getKernel().info("[layout-designer] hierarchy index of " + pTable.getTablePath().format() + " built in " + ms + " ms (" + ix.parentOf.size() + " links"
						+ (ix.partial ? ", PARTIAL: raise -Debx.layoutDesigner.orgChart.maxIndexLinks" : "") + ")");
				}
				return ix;
			}
		});
	}

	static String resource(final String pName)
	{
		try (InputStream in = OrgChart.class.getResourceAsStream(pName))
		{
			return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (final Exception ex)
		{
			return "";
		}
	}
}
