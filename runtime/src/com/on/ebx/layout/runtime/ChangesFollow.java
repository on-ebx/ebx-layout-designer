package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.Request;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.schema.businessObjects.BusinessObjectModel;
import com.orchestranetworks.schema.businessObjects.BusinessObjectModelRelationship;
import com.orchestranetworks.schema.businessObjects.ChildEntity;
import com.orchestranetworks.schema.businessObjects.Entity;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.service.comparison.NodeDifference;

/**
 * The linked rows the {@code changes} block follows (1.28.0): the rows of associations named by {@code follow} and, with
 * {@code businessObject}, the rows of every relationship of the business object declared on the table (strong ones; the
 * light ones too with {@code light}), level after level. Each relation is read on both sides (the work dataspace and the
 * reference), rows are paired by primary key, and a pair is compared field by field like the record itself
 * ({@code DifferenceHelper.compareOccurrences}); a row present on one side only is added or removed.
 * <p>
 * Permissions: the rows are read with the user's session (EBX leaves out what they may not see), else tested row by row
 * ({@link Access#canSee}); the fields shown are those the user may read. A relation holding more than {@link #CAP} rows
 * on either side is counted but not compared, and says so.
 */
final class ChangesFollow
{
	/** Rows read per relation and side at most: beyond, the relation is not compared. */
	static final int CAP = 2000;

	/** Relationship levels followed under a business object at most. */
	private static final int DEPTH = 3;

	private ChangesFollow()
	{
	}

	/** One followed relation and what changed in it. */
	static final class Relation
	{
		final String label;
		final int level;
		final List<Adaptation> added = new ArrayList<Adaptation>();
		final List<Adaptation> removed = new ArrayList<Adaptation>();
		/** Changed rows: the row after → its differences, and the row before. */
		final List<Adaptation[]> changed = new ArrayList<Adaptation[]>();
		final List<List<NodeDifference>> changedDiffs = new ArrayList<List<NodeDifference>>();
		boolean capped;
		/** Rows on the right when the record is new (nothing to pair). */
		int createdCount;

		Relation(final String pLabel, final int pLevel)
		{
			this.label = pLabel;
			this.level = pLevel;
		}

		boolean touched()
		{
			return this.capped || this.createdCount > 0 || !this.added.isEmpty() || !this.removed.isEmpty() || !this.changed.isEmpty();
		}
	}

	/**
	 * The relations to follow, compared. {@code pBefore} is null when the record is new in the request: every linked row
	 * is then "created in this request" and only counted.
	 */
	static List<Relation> compare(final RenderContext rc, final JsonNode b, final Adaptation pBefore, final Adaptation pAfter)
	{
		final List<Relation> out = new ArrayList<Relation>();
		final Session session = rc.context.getSession();
		// associations named one by one
		for (final JsonNode f : b.path("follow"))
		{
			final String path = f.isTextual() ? f.asText() : f.path("a").asText("");
			if (path.isEmpty())
			{
				continue;
			}
			final SchemaNode a = rc.node(path);
			if (a == null || a.getAssociationLink() == null || !Access.canRead(a, pAfter))
			{
				continue;
			}
			final Relation rel = new Relation(a.getLabel(rc.locale), 0);
			final Map<String, Adaptation> after = ChangesFollow.association(a, pAfter, session, rel);
			final Map<String, Adaptation> before = pBefore == null ? null : ChangesFollow.association(a, pBefore, session, rel);
			ChangesFollow.pair(rc, rel, before, after);
			out.add(rel);
		}
		// the business object declared on the table
		if (b.path("businessObject").asBoolean(false))
		{
			final BusinessObjectModel model = ChangesFollow.model(pAfter);
			if (model == null)
			{
				final Relation none = new Relation("", -1);
				out.add(none);
			}
			else
			{
				try
				{
					final Entity after = model.initForRead(pAfter, session);
					final Entity before = pBefore == null ? null : model.initForRead(pBefore, session);
					final boolean light = b.path("light").asBoolean(false);
					ChangesFollow.walk(rc, model.getStrongRelationshipsChildren(), light ? model.getLightRelationshipsChildren() : null, before, after, 1, light, out);
				}
				catch (final RuntimeException ex)
				{
					LoggingCategory.getKernel().warn("[layout-designer] changes block: business object not read: " + ex);
				}
			}
		}
		return out;
	}

	private static BusinessObjectModel model(final Adaptation pRecord)
	{
		try
		{
			final SchemaNode table = pRecord.getContainerTable().getTableNode();
			BusinessObjectModel m = table.getBusinessObjectModel();
			if (m == null)
			{
				m = pRecord.getSchemaNode().getBusinessObjectModel();
			}
			return m;
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().debug("[layout-designer] changes block: no business object model: " + ex);
			return null;
		}
	}

	private static void walk(final RenderContext rc, final List<BusinessObjectModelRelationship> pStrong, final List<BusinessObjectModelRelationship> pLight,
		final Entity pBefore, final Entity pAfter, final int pLevel, final boolean pWithLight, final List<Relation> pOut)
	{
		if (pLevel > ChangesFollow.DEPTH)
		{
			return;
		}
		final List<BusinessObjectModelRelationship> rels = new ArrayList<BusinessObjectModelRelationship>();
		if (pStrong != null)
		{
			rels.addAll(pStrong);
		}
		if (pLight != null)
		{
			rels.addAll(pLight);
		}
		for (final BusinessObjectModelRelationship r : rels)
		{
			final SchemaNode node = r.getRelationshipNode();
			if (node == null || pAfter == null)
			{
				continue;
			}
			final Relation rel = new Relation(node.getLabel(rc.locale), pLevel);
			final Map<String, ChildEntity> after = ChangesFollow.children(pAfter, node, rel);
			final Map<String, ChildEntity> before = pBefore == null ? null : ChangesFollow.children(pBefore, node, rel);
			ChangesFollow.pair(rc, rel, before == null ? null : ChangesFollow.records(before), ChangesFollow.records(after));
			pOut.add(rel);
			if (rel.capped)
			{
				continue;
			}
			// one level down, pair by pair: the children of a row present on both sides
			for (final Map.Entry<String, ChildEntity> e : after.entrySet())
			{
				final ChildEntity bef = before == null ? null : before.get(e.getKey());
				ChangesFollow.walk(rc, r.getStrongRelationshipsChildren(), pWithLight ? r.getLightRelationshipsChildren() : null, bef, e.getValue(), pLevel + 1, pWithLight, pOut);
			}
		}
	}

	private static Map<String, ChildEntity> children(final Entity pEntity, final SchemaNode pNode, final Relation pRel)
	{
		final Map<String, ChildEntity> out = new LinkedHashMap<String, ChildEntity>();
		try
		{
			final com.orchestranetworks.query.QueryResult<ChildEntity> q = pEntity.getChildEntities(pNode);
			try
			{
				for (final ChildEntity c : q)
				{
					final Adaptation row = c.getRecord();
					if (row == null || !Access.canSee(row))
					{
						continue;
					}
					if (out.size() >= ChangesFollow.CAP)
					{
						pRel.capped = true;
						break;
					}
					out.put(row.getOccurrencePrimaryKey().format(), c);
				}
			}
			finally
			{
				q.close();
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] changes block: children of " + pNode.getPathInAdaptation() + " not read: " + ex);
		}
		return out;
	}

	private static Map<String, Adaptation> records(final Map<String, ChildEntity> pEntities)
	{
		final Map<String, Adaptation> out = new LinkedHashMap<String, Adaptation>();
		for (final Map.Entry<String, ChildEntity> e : pEntities.entrySet())
		{
			out.put(e.getKey(), e.getValue().getRecord());
		}
		return out;
	}

	/** The rows of an association of a record, read with the session when the request can be rerun with it. */
	private static Map<String, Adaptation> association(final SchemaNode pAssociation, final Adaptation pRecord, final Session pSession, final Relation pRel)
	{
		final Map<String, Adaptation> out = new LinkedHashMap<String, Adaptation>();
		RequestResult result = null;
		boolean filtered = false;
		try
		{
			result = pAssociation.getAssociationLink().getAssociationResult(pRecord);
			if (pSession != null)
			{
				try
				{
					final Request request = result.getRequest();
					if (request != null)
					{
						request.setSession(pSession);
						final RequestResult f = request.execute();
						result.close();
						result = f;
						filtered = true;
					}
				}
				catch (final RuntimeException ex)
				{
					LoggingCategory.getKernel().debug("[layout-designer] changes block: association read row by row: " + ex);
				}
			}
			for (Adaptation r = result.nextAdaptation(); r != null; r = result.nextAdaptation())
			{
				if (!filtered && !Access.canSee(r))
				{
					continue;
				}
				if (out.size() >= ChangesFollow.CAP)
				{
					pRel.capped = true;
					break;
				}
				out.put(r.getOccurrencePrimaryKey().format(), r);
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] changes block: rows of " + pAssociation.getPathInAdaptation() + " not read: " + ex);
		}
		finally
		{
			if (result != null)
			{
				result.close();
			}
		}
		return out;
	}

	private static void pair(final RenderContext rc, final Relation pRel, final Map<String, Adaptation> pBefore, final Map<String, Adaptation> pAfter)
	{
		if (pRel.capped)
		{
			return;
		}
		if (pBefore == null)
		{
			pRel.createdCount = pAfter.size();
			return;
		}
		for (final Map.Entry<String, Adaptation> e : pAfter.entrySet())
		{
			final Adaptation before = pBefore.get(e.getKey());
			if (before == null)
			{
				pRel.added.add(e.getValue());
				continue;
			}
			final List<NodeDifference> diffs = MoreBlocks.fieldDiffs(rc, before, e.getValue());
			if (diffs == null)
			{
				continue;
			}
			if (!diffs.isEmpty())
			{
				pRel.changed.add(new Adaptation[] { before, e.getValue() });
				pRel.changedDiffs.add(diffs);
			}
		}
		for (final Map.Entry<String, Adaptation> e : pBefore.entrySet())
		{
			if (!pAfter.containsKey(e.getKey()))
			{
				pRel.removed.add(e.getValue());
			}
		}
	}

	/** The relations rendered under the record's own changes; empty when none is touched. */
	static String render(final RenderContext rc, final List<Relation> pRelations, final int pRows, final int pMaxFields)
	{
		final StringBuilder h = new StringBuilder();
		for (final Relation rel : pRelations)
		{
			if (rel.level < 0)
			{
				h.append(MoreBlocks.emptyNote(RuntimeText.get("chBoNone", rc.locale)));
				continue;
			}
			if (!rel.touched())
			{
				continue;
			}
			h.append("<div class=\"ldf-ch-rel ldf-ch-l").append(Math.min(rel.level, 3)).append("\"><div class=\"ldf-ch-relh\"><span class=\"ldf-ch-reln\">")
				.append(MoreBlocks.esc(rel.label)).append("</span>");
			if (rel.capped)
			{
				h.append("<span class=\"ldf-badge\">").append(MoreBlocks.esc(RuntimeText.get("chRelCapped", rc.locale, Integer.valueOf(ChangesFollow.CAP)))).append("</span></div></div>");
				continue;
			}
			if (rel.createdCount > 0)
			{
				h.append("<span class=\"ldf-badge ldf-t-info\">").append(MoreBlocks.esc(RuntimeText.get("chRelAdded", rc.locale, Integer.valueOf(rel.createdCount)))).append("</span></div></div>");
				continue;
			}
			if (!rel.changed.isEmpty())
			{
				h.append("<span class=\"ldf-badge ldf-t-warn\">").append(MoreBlocks.esc(RuntimeText.get("chRelChanged", rc.locale, Integer.valueOf(rel.changed.size())))).append("</span>");
			}
			if (!rel.added.isEmpty())
			{
				h.append("<span class=\"ldf-badge ldf-t-info\">").append(MoreBlocks.esc(RuntimeText.get("chRelAdded", rc.locale, Integer.valueOf(rel.added.size())))).append("</span>");
			}
			if (!rel.removed.isEmpty())
			{
				h.append("<span class=\"ldf-badge ldf-t-bad\">").append(MoreBlocks.esc(RuntimeText.get("chRelRemoved", rc.locale, Integer.valueOf(rel.removed.size())))).append("</span>");
			}
			h.append("</div>");
			int n = 0;
			for (int i = 0; i < rel.changed.size() && n < pRows; i++, n++)
			{
				final Adaptation before = rel.changed.get(i)[0];
				final Adaptation after = rel.changed.get(i)[1];
				final List<NodeDifference> diffs = rel.changedDiffs.get(i);
				h.append("<details class=\"ldf-hd ldf-ch-row\"><summary>").append(MoreBlocks.esc(after.getLabel(rc.locale))).append(" <span class=\"ldf-ch-rs\">")
					.append(MoreBlocks.esc(RuntimeText.get("chCount", rc.locale, Integer.valueOf(diffs.size())))).append("</span></summary>")
					.append(MoreBlocks.diffTable(rc, before, after, diffs, pMaxFields)).append("</details>");
			}
			for (int i = 0; i < rel.added.size() && n < pRows; i++, n++)
			{
				h.append("<div class=\"ldf-ch-row ldf-ch-line\">").append(MoreBlocks.esc(rel.added.get(i).getLabel(rc.locale))).append(" <span class=\"ldf-badge ldf-t-info\">")
					.append(MoreBlocks.esc(RuntimeText.get("chRowAdded", rc.locale))).append("</span></div>");
			}
			for (int i = 0; i < rel.removed.size() && n < pRows; i++, n++)
			{
				h.append("<div class=\"ldf-ch-row ldf-ch-line ldf-ch-gone\">").append(MoreBlocks.esc(rel.removed.get(i).getLabel(rc.locale))).append(" <span class=\"ldf-badge ldf-t-bad\">")
					.append(MoreBlocks.esc(RuntimeText.get("chRowRemoved", rc.locale))).append("</span></div>");
			}
			final int total = rel.changed.size() + rel.added.size() + rel.removed.size();
			if (total > pRows)
			{
				h.append(MoreBlocks.emptyNote(RuntimeText.get("chMore", rc.locale, Integer.valueOf(total - pRows))));
			}
			h.append("</div>");
		}
		return h.toString();
	}
}
