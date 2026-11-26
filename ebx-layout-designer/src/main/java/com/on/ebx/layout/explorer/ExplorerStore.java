package com.on.ebx.layout.explorer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.Request;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;
import com.orchestranetworks.service.OperationException;
import com.orchestranetworks.service.Procedure;
import com.orchestranetworks.service.ProcedureContext;
import com.orchestranetworks.service.ProcedureResult;
import com.orchestranetworks.service.ProgrammaticService;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.service.ValueContextForUpdate;

/**
 * The explorer rows ({@code /root/ExplorerLayout}) and the favorites ({@code /root/ExplorerFavorite}) of the module's
 * dataset. Writes go through a Procedure with the module's own rights: the designer is for administrators, and a
 * favorite only ever names the user's own key.
 */
public final class ExplorerStore
{
	/** One explorer row, read. */
	public static final class Row
	{
		public final int id;
		public final String label, model, table;
		public final boolean active;
		public final JsonNode json;
		public final int version;

		Row(final Adaptation pRow)
		{
			this.id = ((Integer) pRow.get(LayoutPaths.ID)).intValue();
			this.label = pRow.getString(LayoutPaths.LABEL);
			this.model = pRow.getString(LayoutPaths.MODEL);
			this.table = pRow.getString(LayoutPaths.TABLE);
			this.active = Boolean.TRUE.equals(pRow.get(LayoutPaths.ACTIVE));
			this.json = LayoutStore.parse("explorer|" + this.id, pRow.getString(LayoutPaths.LAYOUT));
			final Object v = pRow.get(LayoutPaths.VERSION);
			this.version = v instanceof Integer ? ((Integer) v).intValue() : 0;
		}
	}

	private ExplorerStore()
	{
	}

	private static AdaptationTable table(final Repository pRepository, final Path pTable)
	{
		final Adaptation dataset = LayoutStore.dataset(pRepository);
		if (dataset == null)
		{
			return null;
		}
		try
		{
			return dataset.getSchemaNode().getNode(pTable) == null ? null : dataset.getTable(pTable);
		}
		catch (final RuntimeException ex)
		{
			// the tables appear with the module's model of 1.16.0
			return null;
		}
	}

	/** Every explorer of a table, in id order; every explorer of the instance when the model and the table are null. */
	public static List<Row> list(final Repository pRepository, final SchemaLocation pModel, final Path pTable)
	{
		final List<Row> out = new ArrayList<Row>();
		final AdaptationTable t = ExplorerStore.table(pRepository, LayoutPaths.EXPLORER_LAYOUT);
		if (t == null)
		{
			return out;
		}
		final String model = pModel == null ? null : pModel.format(), table = pTable == null ? null : pTable.format();
		final RequestResult rows = t.createRequest().execute();
		try
		{
			for (Adaptation r; (r = rows.nextAdaptation()) != null;)
			{
				if ((model == null || model.equals(r.getString(LayoutPaths.MODEL))) && (table == null || table.equals(r.getString(LayoutPaths.TABLE))))
				{
					out.add(new Row(r));
				}
			}
		}
		finally
		{
			rows.close();
		}
		out.sort((a, b) -> Integer.compare(a.id, b.id));
		return out;
	}

	public static Row find(final Repository pRepository, final int pId)
	{
		final Adaptation r = ExplorerStore.record(pRepository, pId);
		return r == null ? null : new Row(r);
	}

	private static Adaptation record(final Repository pRepository, final int pId)
	{
		final AdaptationTable t = ExplorerStore.table(pRepository, LayoutPaths.EXPLORER_LAYOUT);
		return t == null ? null : t.lookupAdaptationByPrimaryKey(t.computePrimaryKey(new Object[] { Integer.valueOf(pId) }));
	}

	/** Creates an explorer (id &lt; 0) or replaces its label and JSON; answers its id. */
	public static int save(final Repository pRepository, final Session pSession, final int pId, final SchemaLocation pModel, final Path pTable,
		final String pLabel, final String pJson) throws OperationException
	{
		final AdaptationTable t = ExplorerStore.require(pRepository);
		final Adaptation existing = pId < 0 ? null : ExplorerStore.record(pRepository, pId);
		final int version = existing == null || existing.get(LayoutPaths.VERSION) == null ? 1 : ((Integer) existing.get(LayoutPaths.VERSION)).intValue() + 1;
		final int[] id = { pId };
		ExplorerStore.run(pSession, t, pContext -> {
			final ValueContextForUpdate v = existing == null ? pContext.getContextForNewOccurrence(t) : pContext.getContext(existing.getAdaptationName());
			if (existing == null)
			{
				v.setValue(pModel.format(), LayoutPaths.MODEL);
				v.setValue(pTable.format(), LayoutPaths.TABLE);
				v.setValue(Boolean.TRUE, LayoutPaths.ACTIVE);
			}
			v.setValue(pLabel, LayoutPaths.LABEL);
			if (pJson != null)
			{
				v.setValue(pJson, LayoutPaths.LAYOUT);
			}
			v.setValue(Integer.valueOf(version), LayoutPaths.VERSION);
			v.setValue(pSession.getUserReference() == null ? "" : pSession.getUserReference().getUserId(), LayoutPaths.UPDATED_BY);
			v.setValue(new Date(), LayoutPaths.UPDATED_ON);
			final Adaptation saved = existing == null ? pContext.doCreateOccurrence(v, t) : pContext.doModifyContent(existing, v);
			id[0] = ((Integer) saved.get(LayoutPaths.ID)).intValue();
		});
		Views.forget(id[0]);
		return id[0];
	}

	public static void setActive(final Repository pRepository, final Session pSession, final int pId, final boolean pActive) throws OperationException
	{
		final AdaptationTable t = ExplorerStore.require(pRepository);
		final Adaptation existing = ExplorerStore.record(pRepository, pId);
		if (existing == null)
		{
			return;
		}
		ExplorerStore.run(pSession, t, pContext -> {
			final ValueContextForUpdate v = pContext.getContext(existing.getAdaptationName());
			v.setValue(Boolean.valueOf(pActive), LayoutPaths.ACTIVE);
			pContext.doModifyContent(existing, v);
		});
		Views.forget(pId);
	}

	/** Deletes the explorer and its users' favorites. */
	public static void delete(final Repository pRepository, final Session pSession, final int pId) throws OperationException
	{
		final AdaptationTable t = ExplorerStore.require(pRepository);
		final Adaptation existing = ExplorerStore.record(pRepository, pId);
		final AdaptationTable fav = ExplorerStore.table(pRepository, LayoutPaths.EXPLORER_FAVORITE);
		ExplorerStore.run(pSession, t, pContext -> {
			if (fav != null)
			{
				final List<Adaptation> gone = new ArrayList<Adaptation>();
				final RequestResult rows = fav.createRequest().execute();
				try
				{
					for (Adaptation r; (r = rows.nextAdaptation()) != null;)
					{
						if (Integer.valueOf(pId).equals(r.get(LayoutPaths.FAV_EXPLORER)))
						{
							gone.add(r);
						}
					}
				}
				finally
				{
					rows.close();
				}
				for (final Adaptation r : gone)
				{
					pContext.doDelete(r.getAdaptationName(), false);
				}
			}
			if (existing != null)
			{
				pContext.doDelete(existing.getAdaptationName(), false);
			}
		});
		Views.forget(pId);
	}

	/** A user's favorites of one explorer and dataset, as read at the table's modification date {@code at}. */
	private static final class Starred
	{
		final Date at;
		final Set<String> keys;

		Starred(final Date pAt, final Set<String> pKeys)
		{
			this.at = pAt;
			this.keys = pKeys;
		}
	}

	/** Short cache of the favorites read: a star or unstar changes the table's date, which retires the entry (L2-16). */
	private static final java.util.concurrent.ConcurrentHashMap<String, Starred> STARRED = new java.util.concurrent.ConcurrentHashMap<String, Starred>();

	private static final int STARRED_MAX = 2000;

	/** The XPath predicate of one user's favorites of an explorer on a dataset: EBX serves it by key, not by a sweep of the table. */
	static String favoritesFilter(final String pUser, final int pExplorer, final String pDataspace, final String pDataset)
	{
		return LayoutPaths.PREFS_USER.format() + " = " + XPath.lit(pUser) + " and " + LayoutPaths.FAV_EXPLORER.format() + " = " + pExplorer + " and "
			+ LayoutPaths.FAV_DATASPACE.format() + " = " + XPath.lit(pDataspace) + " and " + LayoutPaths.FAV_DATASET.format() + " = " + XPath.lit(pDataset);
	}

	/** The keys of the records a user starred in an explorer on a dataset. */
	public static Set<String> favorites(final Repository pRepository, final String pUser, final int pExplorer, final Adaptation pDataset)
	{
		final AdaptationTable fav = ExplorerStore.table(pRepository, LayoutPaths.EXPLORER_FAVORITE);
		if (fav == null || pUser == null)
		{
			return new TreeSet<String>();
		}
		final String ds = pDataset.getHome().getKey().getName(), set = pDataset.getAdaptationName().getStringName();
		final String cacheKey = pUser + "\u0000" + pExplorer + "\u0000" + ds + "\u0000" + set;
		final Date at = fav.getLastModificationDate();
		final Starred known = at == null ? null : ExplorerStore.STARRED.get(cacheKey);
		if (known != null && known.at.equals(at))
		{
			return new TreeSet<String>(known.keys);
		}
		final Set<String> out = new TreeSet<String>();
		final Request request = fav.createRequest();
		request.setXPathFilter(ExplorerStore.favoritesFilter(pUser, pExplorer, ds, set));
		final RequestResult rows = request.execute();
		try
		{
			for (Adaptation r; (r = rows.nextAdaptation()) != null;)
			{
				out.add(r.getString(LayoutPaths.FAV_RECORD));
			}
		}
		finally
		{
			rows.close();
		}
		if (at != null)
		{
			if (ExplorerStore.STARRED.size() >= ExplorerStore.STARRED_MAX)
			{
				ExplorerStore.STARRED.clear();
			}
			ExplorerStore.STARRED.put(cacheKey, new Starred(at, new TreeSet<String>(out)));
		}
		return out;
	}

	public static boolean favoritesAvailable(final Repository pRepository)
	{
		return ExplorerStore.table(pRepository, LayoutPaths.EXPLORER_FAVORITE) != null;
	}

	/** Stars or unstars a record for the user; answers true when it is now a favorite. */
	public static boolean toggleFavorite(final Repository pRepository, final Session pSession, final int pExplorer, final Adaptation pDataset, final String pRecord)
		throws OperationException
	{
		final AdaptationTable fav = ExplorerStore.table(pRepository, LayoutPaths.EXPLORER_FAVORITE);
		if (fav == null)
		{
			throw OperationException.createError("No favorites table: the module's data model predates 1.16.0.");
		}
		final String user = pSession.getUserReference().getUserId();
		final Object[] key = { user, Integer.valueOf(pExplorer), pDataset.getHome().getKey().getName(), pDataset.getAdaptationName().getStringName(), pRecord };
		final Adaptation existing = fav.lookupAdaptationByPrimaryKey(fav.computePrimaryKey(key));
		ExplorerStore.run(pSession, fav, pContext -> {
			if (existing != null)
			{
				pContext.doDelete(existing.getAdaptationName(), false);
				return;
			}
			final ValueContextForUpdate v = pContext.getContextForNewOccurrence(fav);
			v.setValue(user, LayoutPaths.PREFS_USER);
			v.setValue(Integer.valueOf(pExplorer), LayoutPaths.FAV_EXPLORER);
			v.setValue(key[2], LayoutPaths.FAV_DATASPACE);
			v.setValue(key[3], LayoutPaths.FAV_DATASET);
			v.setValue(pRecord, LayoutPaths.FAV_RECORD);
			v.setValue(new Date(), LayoutPaths.FAV_ADDED_ON);
			pContext.doCreateOccurrence(v, fav);
		});
		return existing == null;
	}

	private static AdaptationTable require(final Repository pRepository) throws OperationException
	{
		final AdaptationTable t = ExplorerStore.table(pRepository, LayoutPaths.EXPLORER_LAYOUT);
		if (t == null)
		{
			throw OperationException.createError("No table " + LayoutPaths.EXPLORER_LAYOUT.format() + " in " + LayoutPaths.DATASPACE + "/" + LayoutPaths.DATASET
				+ ": the module's data model predates 1.16.0 (a server restart loads it).");
		}
		return t;
	}

	private interface Work
	{
		void run(ProcedureContext pContext) throws Exception;
	}

	private static void run(final Session pSession, final AdaptationTable pTable, final Work pWork) throws OperationException
	{
		final ProcedureResult result = ProgrammaticService.createForSession(pSession, pTable.getContainerAdaptation().getHome()).execute(new Procedure()
		{
			@Override
			public void execute(final ProcedureContext pContext) throws Exception
			{
				pContext.setAllPrivileges(true);
				pWork.run(pContext);
			}
		});
		if (result.hasFailed())
		{
			throw OperationException.createError(result.getExceptionFullMessage(Locale.ENGLISH));
		}
	}
}
