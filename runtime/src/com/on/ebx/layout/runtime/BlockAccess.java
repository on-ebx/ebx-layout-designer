package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.Session;

/**
 * Whether a block has something the user may see, by EBX permissions. A block built on an association ({@code a})
 * needs the association; otherwise, when it names fields of the record and the user may read none of them, it is left
 * out rather than drawn empty. A block naming no field (validation report, history of the record…) is kept. Used by
 * the form and by "Arrange my form", so that the user never even sees the title of a block they may not read.
 */
public final class BlockAccess
{
	private BlockAccess()
	{
	}

	/** For code outside a form's pane (the "Arrange my form" service): checks with the given session. */
	public static boolean readable(final JsonNode pBlock, final Session pSession, final Adaptation pRecord)
	{
		Access.begin(pSession, pRecord, pRecord == null ? null : pRecord.getContainerTable());
		try
		{
			return BlockAccess.readable(pBlock, pRecord.getContainerTable().getTableOccurrenceRootNode(), pRecord);
		}
		finally
		{
			Access.end();
		}
	}

	static boolean readable(final JsonNode b, final SchemaNode pRoot, final Adaptation pRecord)
	{
		final String a = b.path("a").asText("");
		final SchemaNode an = a.isEmpty() ? null : BlockAccess.node(pRoot, a);
		if (an != null)
		{
			return Access.canRead(an, pRecord);
		}
		if ("panel".equals(b.path("type").asText()))
		{
			boolean any = false;
			for (final JsonNode f : b.path("fields"))
			{
				final SchemaNode n = BlockAccess.node(pRoot, f.path("p").asText(""));
				if (n == null)
				{
					continue;
				}
				if (Access.canRead(n, pRecord))
				{
					return true;
				}
				any = true;
			}
			return !any;
		}
		final List<String> paths = new ArrayList<String>();
		BlockAccess.collectPaths(b, paths);
		boolean any = false;
		for (final String p : paths)
		{
			final SchemaNode n = BlockAccess.node(pRoot, p);
			if (n == null)
			{
				continue;
			}
			if (Access.canRead(n, pRecord))
			{
				return true;
			}
			any = true;
		}
		return !any;
	}

	private static SchemaNode node(final SchemaNode pRoot, final String pStored)
	{
		try
		{
			final com.orchestranetworks.schema.Path p = LayoutPaths.relative(pStored);
			return p == null || pRoot == null ? null : pRoot.getNode(p);
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** Every text of the block that looks like a field path ({@code /x/y}), at any depth, texts of labels and notes excepted. */
	static void collectPaths(final JsonNode pNode, final List<String> pOut)
	{
		if (pNode.isTextual())
		{
			final String s = pNode.asText();
			if (s.startsWith("/") && s.length() > 1 && s.indexOf(' ') < 0 && s.indexOf('[') < 0)
			{
				pOut.add(s);
			}
			return;
		}
		if (pNode.isArray())
		{
			for (final JsonNode x : pNode)
			{
				BlockAccess.collectPaths(x, pOut);
			}
			return;
		}
		if (pNode.isObject())
		{
			final java.util.Iterator<java.util.Map.Entry<String, JsonNode>> it = pNode.fields();
			while (it.hasNext())
			{
				final java.util.Map.Entry<String, JsonNode> e = it.next();
				final String k = e.getKey();
				// localized texts, conditions on the behaviour of the block, and the fields of an association's or another table's rows
				if ("title".equals(k) && !"hero".equals(pNode.path("type").asText()) || "note".equals(k) || "label".equals(k) || "text".equals(k) || "showIf".equals(k)
					|| "cond".equals(k) || "cardFields".equals(k) || "week".equals(k) || "exceptions".equals(k) || "attributesBy".equals(k) || "actions".equals(k))
				{
					continue;
				}
				BlockAccess.collectPaths(e.getValue(), pOut);
			}
		}
	}
}
