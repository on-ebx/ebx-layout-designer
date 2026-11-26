package com.on.ebx.layout.runtime;

import com.orchestranetworks.schema.Path;

/**
 * Where the Layout Designer keeps its layouts: one dataset holding the table {@code /root/FormLayout}, one row per
 * (data model, table). The dataspace key can be moved with {@code -Debx.layoutDesigner.dataspace=<key>}.
 */
public final class LayoutPaths
{
	public static final String MODULE_NAME = "ebx-layout-designer";
	public static final String DATASPACE = System.getProperty("ebx.layoutDesigner.dataspace", "LayoutDesigner");
	public static final String DATASET = System.getProperty("ebx.layoutDesigner.dataset", "LayoutDesigner");
	public static final String MODEL_PATH = "/WEB-INF/ebx/schemas/LayoutDesigner.xsd";

	public static final Path FORM_LAYOUT = Path.parse("/root/FormLayout");
	public static final Path ID = Path.parse("./id");
	public static final Path MODEL = Path.parse("./model");
	public static final Path TABLE = Path.parse("./table");
	public static final Path LABEL = Path.parse("./label");
	public static final Path ACTIVE = Path.parse("./active");
	public static final Path LAYOUT = Path.parse("./layout");
	public static final Path VERSION = Path.parse("./version");
	public static final Path PREVIOUS_FORM = Path.parse("./previousForm");
	public static final Path UPDATED_BY = Path.parse("./updatedBy");
	public static final Path UPDATED_ON = Path.parse("./updatedOn");

	/** One user's own arrangement of a form ({@link Personal}); key: user, model, table, variant. */
	public static final Path USER_PREFS = Path.parse("/root/UserFormPreference");
	public static final Path PREFS_USER = Path.parse("./user");
	public static final Path PREFS_VARIANT = Path.parse("./variant");
	public static final Path PREFS = Path.parse("./prefs");

	/** The module service that lets a user arrange a form for themself. */
	public static final String PERSONALIZE_SERVICE = "PersonalizeForm";

	/** The module service that draws one native association grid of a record alone, for a grid loaded when its tab opens. */
	public static final String GRID_SERVICE = "LayoutGrid";
	/** Its parameter: the id of the grid block in the layout. */
	public static final String GRID_BLOCK_PARAM = "ldfBlock";

	/** The module service an "actions" block opens to launch a workflow on the record (1.14.0). */
	public static final String ACTION_SERVICE = "LayoutAction";
	/** Its parameters: the index of the button in the block (the block id goes in {@link #GRID_BLOCK_PARAM}), the perspective. */
	public static final String ACTION_PARAM = "ldfAction";
	public static final String PERSPECTIVE_PARAM = "ldfPerspective";

	/** Explorers (1.16.0): one row per explorer, several per table possible; key {@code /id}. */
	public static final Path EXPLORER_LAYOUT = Path.parse("/root/ExplorerLayout");
	/** A user's starred records in an explorer; key: user, explorer, dataspace, dataset, record. */
	public static final Path EXPLORER_FAVORITE = Path.parse("/root/ExplorerFavorite");
	public static final Path FAV_EXPLORER = Path.parse("./explorer");
	public static final Path FAV_DATASPACE = Path.parse("./dataspace");
	public static final Path FAV_DATASET = Path.parse("./dataset");
	public static final Path FAV_RECORD = Path.parse("./record");
	public static final Path FAV_ADDED_ON = Path.parse("./addedOn");
	/** The module service that shows an explorer, and the one that designs it. */
	public static final String EXPLORER_SERVICE = "Explorer";
	public static final String EXPLORER_DESIGNER_SERVICE = "ExplorerDesigner";
	/** Web component parameter: the explorer id. */
	public static final String EXPLORER_PARAM = "ldxExplorer";

	/** The class a data model declares as its record form to use a layout of this designer. */
	public static final String FORM_CLASS = "com.on.ebx.layout.runtime.ConfigurableForm";

	private LayoutPaths()
	{
	}

	/** A path stored in a layout ({@code /Group/field}) as a path relative to the record. */
	public static Path relative(final String pStoredPath)
	{
		if (pStoredPath == null || pStoredPath.isEmpty())
		{
			return null;
		}
		return Path.parse(pStoredPath.startsWith("/") ? "." + pStoredPath : pStoredPath);
	}

	/** A path of the record ({@code ./Group/field} or {@code /Group/field}) as stored in a layout. */
	public static String stored(final Path pPath)
	{
		final String s = pPath.format();
		return s.startsWith("./") ? s.substring(1) : s.startsWith("/") ? s : "/" + s;
	}
}
