package com.on.ebx.layout.explorer;

import com.onwbp.adaptation.Adaptation;

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
}
