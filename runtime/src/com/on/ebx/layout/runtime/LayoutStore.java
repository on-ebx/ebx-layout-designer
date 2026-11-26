package com.on.ebx.layout.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationHome;
import com.onwbp.adaptation.AdaptationName;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.HomeKey;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;
import com.orchestranetworks.service.LoggingCategory;

/**
 * Reads the layout rows. A form reads its layout at each display: the table holds one row per (model, table), the
 * read is a scan of a few rows, and the parsed JSON is kept as long as the stored text does not change.
 */
public final class LayoutStore
{
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Map<String, Parsed> PARSED = new ConcurrentHashMap<String, Parsed>();

	private static final class Parsed
	{
		final String text;
		final JsonNode json;

		Parsed(final String pText, final JsonNode pJson)
		{
			this.text = pText;
			this.json = pJson;
		}
	}

	private LayoutStore()
	{
	}

	public static Adaptation dataset(final Repository pRepository)
	{
		final AdaptationHome home = pRepository.lookupHome(HomeKey.forBranchName(LayoutPaths.DATASPACE));
		return home == null ? null : home.findAdaptationOrNull(AdaptationName.forName(LayoutPaths.DATASET));
	}

	/** The stored row for this model and table, active or not; null when there is none. */
	public static Adaptation findRow(final Repository pRepository, final SchemaLocation pModel, final Path pTable)
	{
		final Adaptation dataset = LayoutStore.dataset(pRepository);
		if (dataset == null)
		{
			return null;
		}
		final AdaptationTable table = dataset.getTable(LayoutPaths.FORM_LAYOUT);
		final String model = pModel.format();
		final String tablePath = pTable.format();
		final RequestResult rows = table.createRequest().execute();
		try
		{
			for (Adaptation row = rows.nextAdaptation(); row != null; row = rows.nextAdaptation())
			{
				if (model.equals(row.getString(LayoutPaths.MODEL)) && tablePath.equals(row.getString(LayoutPaths.TABLE)))
				{
					return row;
				}
			}
		}
		finally
		{
			rows.close();
		}
		return null;
	}

	/** The active layout of this model and table, parsed; null when there is none or when it cannot be read. */
	public static JsonNode find(final Repository pRepository, final SchemaLocation pModel, final Path pTable)
	{
		final Adaptation row = LayoutStore.findRow(pRepository, pModel, pTable);
		if (row == null || !Boolean.TRUE.equals(row.get(LayoutPaths.ACTIVE)))
		{
			return null;
		}
		return LayoutStore.parse(pModel.format() + "|" + pTable.format(), row.getString(LayoutPaths.LAYOUT));
	}

	public static JsonNode parse(final String pKey, final String pText)
	{
		if (pText == null || pText.trim().isEmpty())
		{
			return null;
		}
		final Parsed known = LayoutStore.PARSED.get(pKey);
		if (known != null && known.text.equals(pText))
		{
			return known.json;
		}
		try
		{
			final JsonNode json = LayoutStore.JSON.readTree(pText);
			LayoutStore.PARSED.put(pKey, new Parsed(pText, json));
			return json;
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] layout " + pKey + " cannot be parsed: " + ex.getMessage());
			return null;
		}
	}

	public static ObjectMapper json()
	{
		return LayoutStore.JSON;
	}
}
