package com.on.ebx.layout.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;

/**
 * Where the form has been drawn since the server started: per data model and table, the last time and record. The
 * designer reads it to tell a table that really uses the form (a DMA model gives no other way to know it). Kept in memory
 * only: the runtime jar sits in the server's lib, shared by the form and the designer module.
 */
public final class FormSightings
{
	/** One sighting: when, on which record. */
	public static final class Sighting
	{
		public final long at;
		public final String record;

		Sighting(final long pAt, final String pRecord)
		{
			this.at = pAt;
			this.record = pRecord;
		}
	}

	private static final Map<String, Sighting> SEEN = new ConcurrentHashMap<String, Sighting>();

	private FormSightings()
	{
	}

	static void seen(final SchemaLocation pModel, final Path pTable, final String pRecord)
	{
		if (pModel != null && pTable != null)
		{
			FormSightings.SEEN.put(FormSightings.key(pModel, pTable), new Sighting(System.currentTimeMillis(), pRecord == null ? "" : pRecord));
		}
	}

	/** The last sighting of the form for this table, or null when it has not been drawn since the server started. */
	public static Sighting last(final SchemaLocation pModel, final Path pTable)
	{
		return pModel == null || pTable == null ? null : FormSightings.SEEN.get(FormSightings.key(pModel, pTable));
	}

	private static String key(final SchemaLocation pModel, final Path pTable)
	{
		return pModel.format() + "|" + pTable.format();
	}
}
