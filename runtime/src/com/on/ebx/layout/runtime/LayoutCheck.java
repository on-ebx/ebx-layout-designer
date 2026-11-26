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
import com.onwbp.adaptation.AdaptationHome;
import com.onwbp.adaptation.AdaptationTable;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.workflow.PublishedProcessKey;
import com.orchestranetworks.workflow.WorkflowEngine;

/**
 * What a layout refers to that no longer exists (1.17.1): a field or an association removed from the data model, a
 * table the model no longer has, a workflow publication no longer published, a registered component this instance does
 * not have. Read by the validation of the FormLayout table (a constraint that never blocks a save) and by the designer.
 * <p>
 * A path is accepted when it is a field of the table's record, or of a table the block reaches (the target of an
 * association or of a foreign key it names, or a table it names by its absolute path), so that the fields of linked rows
 * are not reported. Errors: fields, associations and the table, which belong to the model. Warnings: workflows and
 * components, which may exist on one instance and not on another.
 */
public final class LayoutCheck
{
	/** One finding. */
	public static final class Issue
	{
		public final boolean error;
		public final String key;
		public final String where;
		public final String ref;

		Issue(final boolean pError, final String pKey, final String pWhere, final String pRef)
		{
			this.error = pError;
			this.key = pKey;
			this.where = pWhere;
			this.ref = pRef;
		}

		public String text(final Locale pLocale)
		{
			final String what = RuntimeText.get(this.key, pLocale).replace("{0}", this.ref);
			return this.where.isEmpty() ? what : this.where + " : " + what;
		}
	}

	private static final long TTL_MS = 60_000L;
	private static final java.util.regex.Pattern PATH = java.util.regex.Pattern.compile("(/[A-Za-z_][A-Za-z0-9_]*)+");
	private static final Map<String, Object[]> DATASETS = new HashMap<String, Object[]>();

	private LayoutCheck()
	{
	}

	public static List<Issue> check(final Repository pRepository, final String pModel, final String pTable, final JsonNode pLayout, final Locale pLocale)
	{
		final List<Issue> out = new ArrayList<Issue>();
		if (pLayout == null || !pLayout.isObject() || pModel == null || pTable == null)
		{
			return out;
		}
		final Adaptation dataset = LayoutCheck.datasetOf(pRepository, pModel);
		if (dataset == null)
		{
			out.add(new Issue(false, "chk.noDataset", "", pModel));
			return out;
		}
		final AdaptationTable table;
		try
		{
			table = dataset.getTable(Path.parse(pTable));
		}
		catch (final RuntimeException ex)
		{
			out.add(new Issue(true, "chk.noTable", "", pTable));
			return out;
		}
		if (table == null)
		{
			out.add(new Issue(true, "chk.noTable", "", pTable));
			return out;
		}
		final Set<String> published = LayoutCheck.publications(pRepository);
		LayoutCheck.walk(pLayout, "", dataset, table.getTableOccurrenceRootNode(), published, pLocale, out, new HashSet<String>());
		return out;
	}

	private static void walk(final JsonNode pNode, final String pTab, final Adaptation pDataset, final SchemaNode pRoot, final Set<String> pPublished, final Locale pLocale,
		final List<Issue> pOut, final Set<String> pSeen)
	{
		if (pNode.isArray())
		{
			for (final JsonNode x : pNode)
			{
				LayoutCheck.walk(x, pTab, pDataset, pRoot, pPublished, pLocale, pOut, pSeen);
			}
			return;
		}
		if (!pNode.isObject())
		{
			return;
		}
		String tab = pTab;
		if (pNode.has("rows") && pNode.has("label"))
		{
			tab = LText.pick(pNode.get("label"), pLocale, Locale.US);
		}
		if (pNode.path("type").isTextual() && pNode.has("id"))
		{
			// a block (variants repeat blocks by id: each is checked once)
			final String id = pNode.path("id").asText() + "|" + tab;
			if (pSeen.add(id))
			{
				LayoutCheck.block(pNode, tab, pDataset, pRoot, pPublished, pLocale, pOut);
			}
			return;
		}
		final java.util.Iterator<Map.Entry<String, JsonNode>> it = pNode.fields();
		while (it.hasNext())
		{
			LayoutCheck.walk(it.next().getValue(), tab, pDataset, pRoot, pPublished, pLocale, pOut, pSeen);
		}
	}

	private static void block(final JsonNode b, final String pTab, final Adaptation pDataset, final SchemaNode pRoot, final Set<String> pPublished, final Locale pLocale,
		final List<Issue> pOut)
	{
		final String title = LText.hasText(b.get("title")) ? LText.pick(b.get("title"), pLocale, Locale.US) : b.path("type").asText();
		final String where = RuntimeText.get("chk.where", pLocale).replace("{0}", pTab.isEmpty() ? "—" : pTab).replace("{1}", title);
		// fields and associations
		final List<String> paths = new ArrayList<String>();
		BlockAccess.collectPaths(b, paths);
		// only what reads as a path of the model: not an address (/ebx/?…), an icon or a web resource
		paths.removeIf(p -> !LayoutCheck.PATH.matcher(p).matches());
		LayoutCheck.conditionFields(b, paths);
		for (final JsonNode f : b.path("fields"))
		{
			LayoutCheck.conditionFields(f, paths);
		}
		final List<SchemaNode> roots = new ArrayList<SchemaNode>();
		roots.add(pRoot);
		for (final String p : paths)
		{
			final SchemaNode n = LayoutCheck.node(pRoot, p);
			if (n != null)
			{
				LayoutCheck.addTarget(n, pDataset, roots);
			}
			else if (p.startsWith("/root/"))
			{
				// a table named by its absolute path (another table of the dataset): its fields are fine too
				try
				{
					final AdaptationTable other = pDataset.getTable(Path.parse(p));
					if (other != null)
					{
						roots.add(other.getTableOccurrenceRootNode());
					}
				}
				catch (final RuntimeException ex)
				{
					// not a table path
				}
			}
		}
		final Set<String> reported = new HashSet<String>();
		for (final String p : paths)
		{
			if (p.startsWith("/root/") || reported.contains(p))
			{
				continue;
			}
			boolean found = false;
			for (final SchemaNode r : roots)
			{
				if (LayoutCheck.node(r, p) != null)
				{
					found = true;
					break;
				}
			}
			if (!found)
			{
				reported.add(p);
				pOut.add(new Issue(true, "chk.noField", where, p));
			}
		}
		// registered components
		if ("custom".equals(b.path("type").asText()))
		{
			final String cls = b.path("cls").asText("");
			if (!cls.isEmpty() && LayoutComponents.get(cls) == null)
			{
				pOut.add(new Issue(false, "chk.noComponent", where, cls));
			}
		}
		// workflows: buttons of an actions block, conditions tied to workflows
		LayoutCheck.workflow(b.path("wf").path("pub").asText(""), where, pPublished, pOut);
		for (final JsonNode a : b.path("items"))
		{
			LayoutCheck.workflow(a.path("workflow").asText(""), where, pPublished, pOut);
			LayoutCheck.workflow(a.path("wf").path("pub").asText(""), where, pPublished, pOut);
		}
	}

	private static void conditionFields(final JsonNode pNode, final List<String> pOut)
	{
		for (final String k : new String[] { "showIf", "mandatoryIf", "readOnlyIf" })
		{
			final String f = pNode.path(k).path("field").asText("");
			if (f.startsWith("/"))
			{
				pOut.add(f);
			}
		}
	}

	private static void workflow(final String pName, final String pWhere, final Set<String> pPublished, final List<Issue> pOut)
	{
		if (!pName.trim().isEmpty() && pPublished != null && !pPublished.contains(pName.trim()))
		{
			pOut.add(new Issue(false, "chk.noWorkflow", pWhere, pName.trim()));
		}
	}

	private static void addTarget(final SchemaNode pNode, final Adaptation pDataset, final List<SchemaNode> pRoots)
	{
		try
		{
			if (pNode.isAssociationNode() && pNode.getAssociationLink() != null)
			{
				final AdaptationTable t = pNode.getAssociationLink().getTargetTable(pDataset);
				if (t != null)
				{
					pRoots.add(t.getTableOccurrenceRootNode());
				}
			}
			else if (pNode.getFacetOnTableReference() != null && pNode.getFacetOnTableReference().getTableNode() != null)
			{
				pRoots.add(pNode.getFacetOnTableReference().getTableNode().getTableOccurrenceRootNode());
			}
		}
		catch (final RuntimeException ex)
		{
			// a target in another dataset: its fields are not checked
		}
	}

	private static SchemaNode node(final SchemaNode pRoot, final String pStored)
	{
		try
		{
			final Path p = LayoutPaths.relative(pStored);
			return p == null ? null : pRoot.getNode(p);
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** The names of the published workflows; null when they cannot be read (then workflows are not checked). */
	private static Set<String> publications(final Repository pRepository)
	{
		try
		{
			final Set<String> names = new HashSet<String>();
			for (final PublishedProcessKey k : WorkflowEngine.getFromRepository(pRepository, null).getPublishedKeys())
			{
				names.add(k.getName());
			}
			return names;
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** A dataset built on the model, in an open dataspace; kept a minute. */
	static Adaptation datasetOf(final Repository pRepository, final String pModel)
	{
		synchronized (LayoutCheck.DATASETS)
		{
			final Object[] hit = LayoutCheck.DATASETS.get(pModel);
			if (hit != null && System.currentTimeMillis() - ((Long) hit[1]).longValue() < LayoutCheck.TTL_MS)
			{
				return (Adaptation) hit[0];
			}
		}
		Adaptation found = null;
		try
		{
			final SchemaLocation location = SchemaLocation.parse(pModel);
			found = LayoutCheck.find(pRepository.getReferenceBranch(), location, new HashSet<AdaptationHome>(), new int[] { 0 });
		}
		catch (final RuntimeException ex)
		{
			found = null;
		}
		synchronized (LayoutCheck.DATASETS)
		{
			LayoutCheck.DATASETS.put(pModel, new Object[] { found, Long.valueOf(System.currentTimeMillis()) });
		}
		return found;
	}

	private static Adaptation find(final AdaptationHome pHome, final SchemaLocation pModel, final Set<AdaptationHome> pSeen, final int[] pCount)
	{
		if (pHome == null || !pSeen.add(pHome) || pCount[0]++ > 500)
		{
			return null;
		}
		if (pHome.isBranch() && pHome.isTechnicalBranch())
		{
			return null;
		}
		if (pHome.isBranch() && pHome.isOpenBranch())
		{
			for (final Adaptation d : pHome.findAllRoots())
			{
				try
				{
					if (pModel.equals(d.getSchemaLocation()))
					{
						return d;
					}
				}
				catch (final RuntimeException ex)
				{
					// a dataset whose model does not load
				}
			}
		}
		for (final AdaptationHome version : pHome.getVersionChildren())
		{
			for (final AdaptationHome child : version.getBranchChildren())
			{
				final Adaptation d = LayoutCheck.find(child, pModel, pSeen, pCount);
				if (d != null)
				{
					return d;
				}
			}
		}
		return null;
	}
}
