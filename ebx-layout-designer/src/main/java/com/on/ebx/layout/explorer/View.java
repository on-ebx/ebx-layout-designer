package com.on.ebx.layout.explorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Session;

/**
 * One user's explorer: the facets, tiles, search fields and sorts left once their roles ({@link Spec}) and EBX's
 * permissions on fields and linked tables are applied, and what EBX can evaluate ({@link Model}). Holds no record: every
 * answer is asked to EBX with the user's session ({@link Engine}), which applies the record permissions itself.
 */
public final class View
{
	public final Model model;
	public final Spec spec;
	public final Session session;
	/** Sources this user may not read: fields, and the computed values reading them or a linked table they may not read. */
	public final Set<String> hidden;
	public final List<Spec.Facet> facets = new ArrayList<Spec.Facet>();
	public final List<Spec.Tile> tiles = new ArrayList<Spec.Tile>();
	public final List<String> search = new ArrayList<String>();
	public final List<Spec.Sort> sorts = new ArrayList<Spec.Sort>();

	public View(final Model pModel, final Spec pSpec, final Session pSession, final Set<String> pHiddenFields, final Set<String> pHiddenTables)
	{
		this.model = pModel;
		this.spec = pSpec;
		this.session = pSession;
		this.hidden = View.hiddenSources(pSpec, pHiddenFields, pHiddenTables);
		final Set<String> keptFlags = new HashSet<String>();
		for (final Spec.Facet f : pSpec.facets)
		{
			if ("flags".equals(f.kind))
			{
				f.flags.removeIf(fl -> this.reads(fl.when) || View.unsupported(pModel.pred(fl.when), "flag " + fl.id));
				if (f.flags.isEmpty())
				{
					continue;
				}
				for (final Spec.Flag fl : f.flags)
				{
					keptFlags.add(fl.id);
				}
			}
			else if (!"favorites".equals(f.kind))
			{
				final Model.Src s = pModel.src(f.source);
				if (this.hidden.contains(f.source) || s == null || s.sql == null && s.xpath == null && View.unsupported(null, "facet " + f.id))
				{
					continue;
				}
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
			if (t.flag != null ? keptFlags.contains(t.flag)
				: t.set.isEmpty() ? !this.reads(t.when) && !View.unsupported(pModel.pred(t.when), "tile " + t.id) : keptFacets.containsAll(t.set.keySet()))
			{
				this.tiles.add(t);
			}
		}
		for (final String s : pSpec.search)
		{
			if (!this.hidden.contains(s) && pModel.src(s) != null)
			{
				this.search.add(s);
			}
		}
		for (final Spec.Sort s : pSpec.sorts)
		{
			final Model.Src src = pModel.src(s.source);
			if (!this.hidden.contains(s.source) && src != null && (src.sql != null || src.node != null && !src.node.isLinkedNode() && !"list".equals(src.type)))
			{
				this.sorts.add(s);
			}
		}
	}

	/** True (and logged) when EBX cannot evaluate a condition: a value only Java computes ({@code provided}). */
	private static boolean unsupported(final Model.Pred pPred, final String pWhat)
	{
		if (pPred != null && (pPred.sql != null || pPred.xpath != null))
		{
			return false;
		}
		LoggingCategory.getKernel().warn("[layout-designer] explorer: " + pWhat + " left out, EBX cannot evaluate it (a value computed in Java?)");
		return true;
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

	/** True when a condition reads something this user may not read. */
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

	public Spec.Facet facet(final String pId)
	{
		for (final Spec.Facet f : this.facets)
		{
			if (f.id.equals(pId))
			{
				return f;
			}
		}
		return null;
	}
}
