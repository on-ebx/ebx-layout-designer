package com.on.ebx.layout.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationHome;
import com.onwbp.adaptation.AdaptationName;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.PrimaryKey;
import com.onwbp.adaptation.Request;
import com.onwbp.adaptation.XPathExpressionHelper;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.HomeKey;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;
import com.orchestranetworks.service.LoggingCategory;

/**
 * Reads the layout rows. A form reads its layout at each display (and EBX asks the "Arrange my form" permission rule for every
 * record whose Actions menu opens): the table holds one row per (model, table), found by a predicate on those two fields, its
 * key kept for as long as the table's modification date is unchanged (audit L2-15), and the parsed JSON is kept as long as the
 * stored text does not change.
 */
public final class LayoutStore
{
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Map<String, Parsed> PARSED = new ConcurrentHashMap<String, Parsed>();
	/** (model, table) to the primary key of its row, "" for no row; valid for one modification date of the FormLayout table. */
	private static final StampedCache<String> ROW_KEYS = new StampedCache<String>(2000);

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
		final String key = LayoutStore.rowKey(model + "|" + tablePath, table.getLastModificationDate(), new StampedCache.Loader<String>()
		{
			@Override
			public String load()
			{
				return LayoutStore.scanKey(table, model, tablePath);
			}
		});
		if (key.isEmpty())
		{
			return null;
		}
		return table.lookupAdaptationByPrimaryKey(PrimaryKey.parseString(key));
	}

	/** The cached key of a row, or what {@code pLoader} reads when the table changed since (visible to the tests). */
	static String rowKey(final String pId, final Object pStamp, final StampedCache.Loader<String> pLoader)
	{
		return LayoutStore.ROW_KEYS.get(pId, pStamp, pLoader);
	}

	/** One request with a predicate on model and table: the key of the row, "" when there is none. */
	private static String scanKey(final AdaptationTable pTable, final String pModel, final String pTablePath)
	{
		final Request request = pTable.createRequest();
		request.setXPathFilter("./model = " + XPathExpressionHelper.encodeLiteralStringWithDelimiters(pModel) + " and ./table = "
			+ XPathExpressionHelper.encodeLiteralStringWithDelimiters(pTablePath));
		final RequestResult rows = request.execute();
		try
		{
			final Adaptation row = rows.nextAdaptation();
			return row == null ? "" : row.getOccurrencePrimaryKey().format();
		}
		finally
		{
			rows.close();
		}
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

	/** The user's own arrangement of this form (model, table, variant), parsed; null when they have none. */
	public static JsonNode findPrefs(final Repository pRepository, final String pUser, final SchemaLocation pModel, final Path pTable, final String pVariant)
	{
		final Adaptation row = LayoutStore.findPrefsRow(pRepository, pUser, pModel, pTable, pVariant);
		if (row == null)
		{
			return null;
		}
		return LayoutStore.parse("prefs|" + pUser + "|" + pModel.format() + "|" + pTable.format() + "|" + pVariant, row.getString(LayoutPaths.PREFS));
	}

	public static Adaptation findPrefsRow(final Repository pRepository, final String pUser, final SchemaLocation pModel, final Path pTable, final String pVariant)
	{
		final Adaptation dataset = LayoutStore.dataset(pRepository);
		if (dataset == null || pUser == null)
		{
			return null;
		}
		try
		{
			final AdaptationTable table = dataset.getTable(LayoutPaths.USER_PREFS);
			return table.lookupAdaptationByPrimaryKey(table.computePrimaryKey(new Object[] { pUser, pModel.format(), pTable.format(), pVariant }));
		}
		catch (final RuntimeException ex)
		{
			// the table appears with the module's model of 1.10.0: an older model has none
			return null;
		}
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
