package com.on.ebx.layout.explorer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One request of the explorer page on a {@link View}: the text, the selected facet values, the tile, the sort. Answers
 * one page of records, each facet's counts (a record counts for a facet when it passes every OTHER facet), each tile's
 * count, and for each facet one page of values: never a whole list of values. The answers are EBX's ({@link Engine}).
 */
public final class Query
{
	public static final int MAX_VALUES = 50;

	/** One value of a facet: key, label, count, depth (tree), selected. */
	public static final class Item
	{
		public final String v, l;
		public final int n, d;
		public final boolean sel;

		Item(final String pV, final String pL, final int pN, final int pD, final boolean pSel)
		{
			this.v = pV;
			this.l = pL;
			this.n = pN;
			this.d = pD;
			this.sel = pSel;
		}
	}

	public static final class FacetPage
	{
		/** Distinct values matching the facet's search (all values when none), among the rows counted. */
		public int total;
		/** True when {@link #total} is only "more than the values shown" (a long list under a filter: not counted exactly). */
		public boolean more;
		/** Not counted: the facet is collapsed on the page (counted when opened). */
		public boolean lazy;
		public final List<Item> items = new ArrayList<Item>();
	}

	public static final class Result
	{
		/** Records the user may see; records matching the request; the keys of the page asked for, in order. */
		public int all, total;
		public final List<String> keys = new ArrayList<String>();
		public final Map<String, Integer> tiles = new LinkedHashMap<String, Integer>();
		public final Map<String, FacetPage> facets = new LinkedHashMap<String, FacetPage>();
		/** When the oldest answer used was computed (ms); whether the data changed since (on-demand mode); the mode. */
		public long computedAt;
		public boolean changed;
		public String mode;
	}

	/** What the page asks: per facet, how many values to show and the text typed in the facet's own search. */
	public static final class Request
	{
		public String text;
		public final Map<String, Set<String>> selected = new HashMap<String, Set<String>>();
		public String tile;
		public int sort;
		public final Map<String, Integer> shown = new HashMap<String, Integer>();
		public final Map<String, String> facetSearch = new HashMap<String, String>();
		public Set<String> favorites = new TreeSet<String>();
		/** {@code main}: total, tiles and the page of records; {@code facets}: the facets only; {@code all}: everything. */
		public String part = "all";
		/** Facets collapsed on the page: not counted. */
		public final Set<String> closed = new TreeSet<String>();
		/** Start new answers (the Refresh button). */
		public boolean refresh;
		/** True once the same page asked a newer request: this one stops. */
		public java.util.function.BooleanSupplier cancelled;
	}

	private Query()
	{
	}

	/** {@code "st=ACTIF|BLOQUE;flag=RIB"}: facet id, then its values; unknown ids are ignored by the query. */
	public static Map<String, Set<String>> parseSelection(final String pText)
	{
		final Map<String, Set<String>> out = new HashMap<String, Set<String>>();
		if (pText == null)
		{
			return out;
		}
		for (final String part : pText.split(";"))
		{
			final int eq = part.indexOf('=');
			if (eq <= 0)
			{
				continue;
			}
			final Set<String> values = new TreeSet<String>();
			for (final String v : part.substring(eq + 1).split("\\|"))
			{
				if (!v.isEmpty())
				{
					values.add(Query.unescape(v));
				}
			}
			if (!values.isEmpty())
			{
				out.put(part.substring(0, eq), values);
			}
		}
		return out;
	}

	/** Values may hold ";", "|" or "=": the page writes them as %3B, %7C, %3D, and % as %25. */
	static String unescape(final String pValue)
	{
		return pValue.replace("%3B", ";").replace("%7C", "|").replace("%3D", "=").replace("%25", "%");
	}

	/** The page of records {@code pOffset}..{@code pOffset + pLimit}, the counts and the facets: all asked to EBX ({@link Engine}). */
	public static Result run(final View pView, final Request pRequest, final int pOffset, final int pLimit)
	{
		return Engine.run(pView, pRequest, pOffset, pLimit);
	}

	/** A configured text in the user's locale (else the closest one). */
	static String text(final com.fasterxml.jackson.databind.JsonNode pText, final Locale pLocale)
	{
		return pText == null ? "" : com.on.ebx.layout.runtime.LText.pick(pText, pLocale == null ? Locale.US : pLocale, Locale.US);
	}

	/** Values of one source over the records this user sees, most frequent first (designer and long lists). */
	public static FacetPage values(final View pView, final String pSource, final String pSearch, final int pOffset, final int pCount)
	{
		return Engine.values(pView, pSource, pSearch, pOffset, pCount);
	}
}
