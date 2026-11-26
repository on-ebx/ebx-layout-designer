package com.on.ebx.layout.explorer;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One explorer's index over one dataset: a compact row per record holding only the values the explorer filters, counts,
 * searches and sorts on (its sources, every variant included), read once and kept until a table it reads changes. Built
 * without any user's permissions: {@link View} filters it for each user. Pure data, so the query code runs in tests.
 */
public final class Index
{
	/** The rows of a linked table counted by a {@code count} value, kept to count again over what one user may see. */
	public static final class Children
	{
		public final String derived;
		/** Path of the linked table in the dataset, for its permission. */
		public final String table;
		public final String[] keys;
		public final int[] parent;
		public final boolean[] counted;

		public Children(final String pDerived, final String pTable, final String[] pKeys, final int[] pParent, final boolean[] pCounted)
		{
			this.derived = pDerived;
			this.table = pTable;
			this.keys = pKeys;
			this.parent = pParent;
			this.counted = pCounted;
		}
	}

	/** A hierarchy for a {@code tree} facet: key of a record of the target table, its label and its parent's key. */
	public static final class Tree
	{
		public final Map<String, String> label = new LinkedHashMap<String, String>();
		public final Map<String, String> parent = new HashMap<String, String>();
	}

	public final String[] keys;
	public final String[] labels;
	public final Object[][] vals;
	public final Map<String, Integer> slots;
	public final Map<String, Integer> pos = new HashMap<String, Integer>();
	/** Per source: value key to its label in the index locale; for a static enumeration, in the model's order. */
	public final Map<String, Map<String, String>> valueLabels;
	public final java.util.Set<String> modelOrder;
	public final Map<String, Tree> trees;
	public final List<Children> children;
	/** Per row and slot: the normalized text the search reads (null for a slot nobody searches). */
	public final String[][] text;
	public final long token;
	public final long builtAt = System.currentTimeMillis();
	public final long buildMillis;

	public Index(final String[] pKeys, final String[] pLabels, final Object[][] pVals, final Map<String, Integer> pSlots,
		final Map<String, Map<String, String>> pValueLabels, final java.util.Set<String> pModelOrder, final Map<String, Tree> pTrees,
		final List<Children> pChildren, final String[][] pText, final long pToken, final long pBuildMillis)
	{
		this.keys = pKeys;
		this.labels = pLabels;
		this.vals = pVals;
		this.slots = pSlots;
		this.valueLabels = pValueLabels;
		this.modelOrder = pModelOrder;
		this.trees = pTrees;
		this.children = pChildren;
		this.text = pText;
		this.token = pToken;
		this.buildMillis = pBuildMillis;
		for (int i = 0; i < pKeys.length; i++)
		{
			this.pos.put(pKeys[i], Integer.valueOf(i));
		}
	}

	public int size()
	{
		return this.keys.length;
	}

	/** The stored value of a source for a row; null when the index does not hold the source. */
	public Object value(final int pRow, final String pSource)
	{
		final Integer s = this.slots.get(pSource);
		return s == null ? null : this.vals[pRow][s.intValue()];
	}

	/** The label of one value of a source, else the value itself. */
	public String label(final String pSource, final String pKey)
	{
		final Map<String, String> m = this.valueLabels.get(pSource);
		final String l = m == null ? null : m.get(pKey);
		return l == null ? pKey : l;
	}

	/** Lower case, accents removed: what the search compares. */
	public static String norm(final String pText)
	{
		return pText == null ? "" : Normalizer.normalize(pText, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	/** The search text of one value: its labels (or keys), several values joined. */
	static String searchText(final Index pIndex, final String pSource, final Object pValue, final Map<String, Map<String, String>> pLabels)
	{
		if (pValue == null)
		{
			return null;
		}
		final StringBuilder b = new StringBuilder();
		final Collection<?> all = pValue instanceof Collection ? (Collection<?>) pValue : java.util.Collections.singletonList(pValue);
		for (final Object o : all)
		{
			final String k = Cond.key(o);
			if (k == null)
			{
				continue;
			}
			final Map<String, String> m = pLabels.get(pSource);
			final String l = m == null ? null : m.get(k);
			b.append(' ').append(k);
			if (l != null && !l.equals(k))
			{
				b.append(' ').append(l);
			}
		}
		return Index.norm(b.toString());
	}
}
