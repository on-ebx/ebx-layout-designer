package com.on.ebx.layout.explorer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.orchestranetworks.schema.Path;

/** A record's values for a condition, for the services outside this package (an action's {@code cond}). */
public final class IndexBuilderAccess
{
	private IndexBuilderAccess()
	{
	}

	public static Cond.Lookup lookup(final Adaptation pRecord)
	{
		return IndexBuilder.recordLookup(pRecord, pRecord.getSchemaNode());
	}

	/** The table an action's {@code association} (or {@code table} + {@code fk}) reaches, and the field there pointing back. */
	public static AdaptationTable linkedTable(final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pDef)
	{
		return IndexBuilder.linkedTable(pDataset, pTable, IndexBuilderAccess.asCount(pDef));
	}

	public static Path linkedKey(final AdaptationTable pTable, final JsonNode pDef, final AdaptationTable pChild)
	{
		return IndexBuilder.linkedKey(pTable, IndexBuilderAccess.asCount(pDef), pChild);
	}

	private static JsonNode asCount(final JsonNode pDef)
	{
		final ObjectNode d = pDef.isObject() ? ((ObjectNode) pDef).deepCopy() : com.on.ebx.layout.runtime.LayoutStore.json().createObjectNode();
		d.put("type", "count");
		return d;
	}
}
