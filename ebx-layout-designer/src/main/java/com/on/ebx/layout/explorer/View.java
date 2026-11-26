package com.on.ebx.layout.explorer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One user's view of an {@link Index}: the records they may see, the values they may read, the facets, tiles, search
 * fields and sorts left once both their roles ({@link Spec}) and EBX permissions are applied, and each row's facet values.
 * Pure: the permissions come in as data ({@link Views} reads them from EBX), so it runs in tests.
 */
public final class View
{
	public final Index ix;
	public final Spec spec;
	public final BitSet visible;
	public final int visibleCount;
	/** Sources this user may not read anywhere: fields, and the computed values reading them or a hidden linked table. */
	public final Set<String> hidden;
	/** Sources hidden on some records only (record-dependent rules on fields). */
	private final Map<Integer, Set<String>> rowHidden;
	/** {@code count} values counted again over the linked rows this user may see. */
	private final Map<String, int[]> counts;
	public final List<Spec.Facet> facets = new ArrayList<Spec.Facet>();
	public final List<Spec.Tile> tiles = new ArrayList<Spec.Tile>();
	public final List<String> search = new ArrayList<String>();
	public final List<Spec.Sort> sorts = new ArrayList<Spec.Sort>();
	/** Per kept facet, per row: its values (flags raised, range code, value keys, tree chain); null rows are not visible. */
	final String[][][] facetValues;
	public final long builtAt = System.currentTimeMillis();

	public View(final Index pIndex, final Spec pSpec, final BitSet pVisible, final Set<String> pHiddenFields, final Map<Integer, Set<String>> pRowHidden,
		final Map<String, int[]> pCounts, final Set<String> pHiddenTables)
	{
		this.ix = pIndex;
		this.spec = pSpec;
		this.visible = pVisible;
		this.visibleCount = pVisible.cardinality();
		this.rowHidden = pRowHidden == null ? Collections.<Integer, Set<String>> emptyMap() : pRowHidden;
		this.counts = pCounts == null ? Collections.<String, int[]> emptyMap() : pCounts;
		this.hidden = View.hiddenSources(pSpec, pHiddenFields, pHiddenTables);

		final Set<String> keptFlags = new HashSet<String>();
		for (final Spec.Facet f : pSpec.facets)
		{
			if ("flags".equals(f.kind))
			{
				f.flags.removeIf(fl -> this.reads(fl.when));
				if (f.flags.isEmpty())
				{
					continue;
				}
				for (final Spec.Flag fl : f.flags)
				{
					keptFlags.add(fl.id);
				}
			}
			else if (!"favorites".equals(f.kind) && this.hidden.contains(f.source))
			{
				continue;
			}
			this.facets.add(f);
		}
		final Set<String> keptFacets = new HashSet<String>();
		for (final Spec.Facet f : this.facets)
		{
			keptFacets.add(f.id);
		}
		for (final Spec.Tile t : pSpec.tiles)
		{
			if (t.flag != null ? keptFlags.contains(t.flag) : t.set.isEmpty() ? !this.reads(t.when) : keptFacets.containsAll(t.set.keySet()))
			{
				this.tiles.add(t);
			}
		}
		for (final String s : pSpec.search)
		{
			if (!this.hidden.contains(s))
			{
				this.search.add(s);
			}
		}
		for (final Spec.Sort s : pSpec.sorts)
		{
			if (!this.hidden.contains(s.source))
			{
				this.sorts.add(s);
			}
		}
		this.facetValues = new String[this.facets.size()][][];
		for (int f = 0; f < this.facets.size(); f++)
		{
			this.facetValues[f] = new String[pIndex.size()][];
			for (int r = this.visible.nextSetBit(0); r >= 0; r = this.visible.nextSetBit(r + 1))
			{
				this.facetValues[f][r] = this.computeFacet(this.facets.get(f), r);
			}
		}
	}

	private static Set<String> hiddenSources(final Spec pSpec, final Set<String> pFields, final Set<String> pTables)
	{
		final Set<String> out = new HashSet<String>(pFields == null ? Collections.<String> emptySet() : pFields);
		// a computed value is hidden when it reads a hidden field or value, or a linked table the user may not read
		boolean grew = true;
		while (grew)
		{
			grew = false;
			for (final Map.Entry<String, JsonNode> e : pSpec.derived.entrySet())
			{
				final String src = Spec.DERIVED + e.getKey();
				if (out.contains(src))
				{
					continue;
				}
				final Set<String> fields = new LinkedHashSet<String>();
				final Set<String> derived = new LinkedHashSet<String>();
				Spec.derivedReads(e.getValue(), fields, derived);
				boolean hide = pTables != null && pTables.contains(e.getKey());
				for (final String f : fields)
				{
					hide |= out.contains(f);
				}
				for (final String d : derived)
				{
					hide |= out.contains(Spec.DERIVED + d);
				}
				if (hide)
				{
					out.add(src);
					grew = true;
				}
			}
		}
		return out;
	}

	/** True when a condition reads something this user may not read anywhere. */
	boolean reads(final JsonNode pCond)
	{
		final Set<String> fields = new HashSet<String>();
		final Set<String> derived = new HashSet<String>();
		Cond.reads(pCond, fields, derived);
		for (final String f : fields)
		{
			if (this.hidden.contains(f))
			{
				return true;
			}
		}
		for (final String d : derived)
		{
			if (this.hidden.contains(Spec.DERIVED + d))
			{
				return true;
			}
		}
		return false;
	}

	/** A source's value on a row as this user may read it: null when hidden for them (everywhere or on that record). */
	public Object value(final int pRow, final String pSource)
	{
		if (pSource == null || this.hidden.contains(pSource))
		{
			return null;
		}
		final Set<String> h = this.rowHidden.get(Integer.valueOf(pRow));
		if (h != null && h.contains(pSource))
		{
			return null;
		}
		if (pSource.startsWith(Spec.DERIVED))
		{
			final int[] c = this.counts.get(pSource.substring(Spec.DERIVED.length()));
			if (c != null)
			{
				return Double.valueOf(c[pRow]);
			}
		}
		return this.ix.value(pRow, pSource);
	}

	/** True when a source is hidden on this record for this user. */
	public boolean hiddenOn(final int pRow, final String pSource)
	{
		if (this.hidden.contains(pSource))
		{
			return true;
		}
		final Set<String> h = this.rowHidden.get(Integer.valueOf(pRow));
		return h != null && h.contains(pSource);
	}

	public Cond.Lookup lookup(final int pRow)
	{
		return new Cond.Lookup()
		{
			@Override
			public Object field(final String pPath)
			{
				return View.this.value(pRow, pPath);
			}

			@Override
			public Object derived(final String pName)
			{
				return View.this.value(pRow, Spec.DERIVED + pName);
			}
		};
	}

	private String[] computeFacet(final Spec.Facet pFacet, final int pRow)
	{
		switch (pFacet.kind)
		{
		case "favorites":
			return new String[0];
		case "flags":
		{
			final Cond.Lookup l = this.lookup(pRow);
			final List<String> out = new ArrayList<String>();
			for (final Spec.Flag fl : pFacet.flags)
			{
				if (Cond.test(fl.when, l))
				{
					out.add(fl.id);
				}
			}
			return out.toArray(new String[out.size()]);
		}
		case "ranges":
		{
			final Double n = Cond.number(this.value(pRow, pFacet.source));
			if (n == null)
			{
				return new String[0];
			}
			for (final Spec.Range r : pFacet.ranges)
			{
				if ((r.min == null || n.doubleValue() >= r.min.doubleValue()) && (r.max == null || n.doubleValue() < r.max.doubleValue()))
				{
					return new String[] { r.code };
				}
			}
			return new String[0];
		}
		case "tree":
		{
			final String k = Cond.key(this.value(pRow, pFacet.source));
			final Index.Tree tree = this.ix.trees.get(pFacet.source);
			if (k == null || tree == null)
			{
				return k == null ? new String[0] : new String[] { k };
			}
			final List<String> chain = new ArrayList<String>();
			for (String c = k; c != null && chain.size() < 12 && !chain.contains(c); c = tree.parent.get(c))
			{
				chain.add(c);
			}
			return chain.toArray(new String[chain.size()]);
		}
		default:
		{
			final Object v = this.value(pRow, pFacet.source);
			if (v == null)
			{
				return new String[0];
			}
			if (v instanceof Collection)
			{
				final List<String> out = new ArrayList<String>();
				for (final Object o : (Collection<?>) v)
				{
					final String k = Cond.key(o);
					if (k != null && !out.contains(k))
					{
						out.add(k);
					}
				}
				return out.toArray(new String[out.size()]);
			}
			return new String[] { Cond.key(v) };
		}
		}
	}

	/** Visible rows only; for the others the answer is meaningless. */
	String[] facetValues(final int pFacet, final int pRow)
	{
		final String[] v = this.facetValues[pFacet][pRow];
		return v == null ? new String[0] : v;
	}

	/** The values of a kept facet on a visible row (flags raised, range code, value keys); empty for an unknown facet. */
	public String[] values(final String pFacetId, final int pRow)
	{
		for (int f = 0; f < this.facets.size(); f++)
		{
			if (this.facets.get(f).id.equals(pFacetId))
			{
				return this.facetValues(f, pRow);
			}
		}
		return new String[0];
	}

	public static Map<Integer, Set<String>> noRowRules()
	{
		return new HashMap<Integer, Set<String>>();
	}
}
