package com.on.ebx.layout.designer;

import java.util.Date;
import java.util.Locale;

import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
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
 * Writes a user's own arrangement of a form. Always the session's own user, never anyone else: the row's key is built
 * from the session, not from what the page posts. The user needs no permission on the designer's dataset (the procedure
 * runs with all privileges, on that one row).
 */
final class PrefsWriter
{
	private PrefsWriter()
	{
	}

	/** {@code pPrefs} null or empty: the row is deleted (back to the form as designed). */
	static void save(final Repository pRepository, final Session pSession, final SchemaLocation pModel, final Path pTable, final String pVariant, final String pPrefs)
		throws OperationException
	{
		final Adaptation dataset = LayoutStore.dataset(pRepository);
		if (dataset == null || pSession.getUserReference() == null)
		{
			throw OperationException.createError("The dataset " + LayoutPaths.DATASPACE + "/" + LayoutPaths.DATASET + " does not exist: the module is not installed.");
		}
		final String user = pSession.getUserReference().getUserId();
		final Adaptation existing = LayoutStore.findPrefsRow(pRepository, user, pModel, pTable, pVariant);
		final boolean clear = pPrefs == null || pPrefs.trim().isEmpty();
		if (clear && existing == null)
		{
			return;
		}
		final ProcedureResult result = ProgrammaticService.createForSession(pSession, dataset.getHome()).execute(new Procedure()
		{
			@Override
			public void execute(final ProcedureContext pContext) throws Exception
			{
				pContext.setAllPrivileges(true);
				if (clear)
				{
					pContext.doDelete(existing.getAdaptationName(), false);
					return;
				}
				final AdaptationTable table = dataset.getTable(LayoutPaths.USER_PREFS);
				final ValueContextForUpdate values = existing == null ? pContext.getContextForNewOccurrence(table) : pContext.getContext(existing.getAdaptationName());
				if (existing == null)
				{
					values.setValue(user, LayoutPaths.PREFS_USER);
					values.setValue(pModel.format(), LayoutPaths.MODEL);
					values.setValue(pTable.format(), LayoutPaths.TABLE);
					values.setValue(pVariant, LayoutPaths.PREFS_VARIANT);
				}
				values.setValue(pPrefs, LayoutPaths.PREFS);
				values.setValue(new Date(), LayoutPaths.UPDATED_ON);
				if (existing == null)
				{
					pContext.doCreateOccurrence(values, table);
				}
				else
				{
					pContext.doModifyContent(existing, values);
				}
			}
		});
		if (result.hasFailed())
		{
			throw OperationException.createError(result.getExceptionFullMessage(Locale.ENGLISH));
		}
	}
}
