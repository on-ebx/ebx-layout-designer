package com.on.ebx.layout.designer;

import java.util.Date;
import java.util.Locale;

import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.service.ValueContextForUpdate;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;
import com.orchestranetworks.service.OperationException;
import com.orchestranetworks.service.Procedure;
import com.orchestranetworks.service.ProcedureContext;
import com.orchestranetworks.service.ProcedureResult;
import com.orchestranetworks.service.ProgrammaticService;
import com.orchestranetworks.service.Session;

/** Writes a layout row: creates it for a (model, table) seen for the first time, else replaces its layout. */
final class LayoutWriter
{
	private LayoutWriter()
	{
	}

	/**
	 * Saves the layout and answers the new version. {@code pPreviousForm} is written only when not null (the class
	 * the model declared before the designer's).
	 */
	static int save(final Repository pRepository, final Session pSession, final SchemaLocation pModel, final Path pTable, final String pLabel,
		final String pLayout, final String pPreviousForm) throws OperationException
	{
		final Adaptation dataset = LayoutStore.dataset(pRepository);
		if (dataset == null)
		{
			throw OperationException.createError("The dataset " + LayoutPaths.DATASPACE + "/" + LayoutPaths.DATASET + " does not exist: the module is not installed.");
		}
		final Adaptation existing = LayoutStore.findRow(pRepository, pModel, pTable);
		final int version = existing == null || existing.get(LayoutPaths.VERSION) == null ? 1 : ((Integer) existing.get(LayoutPaths.VERSION)).intValue() + 1;
		final String user = pSession.getUserReference() == null ? "" : pSession.getUserReference().getUserId();
		final ProcedureResult result = ProgrammaticService.createForSession(pSession, dataset.getHome()).execute(new Procedure()
		{
			@Override
			public void execute(final ProcedureContext pContext) throws Exception
			{
				pContext.setAllPrivileges(true);
				final AdaptationTable table = dataset.getTable(LayoutPaths.FORM_LAYOUT);
				final ValueContextForUpdate values = existing == null ? pContext.getContextForNewOccurrence(table) : pContext.getContext(existing.getAdaptationName());
				if (existing == null)
				{
					values.setValue(pModel.format(), LayoutPaths.MODEL);
					values.setValue(pTable.format(), LayoutPaths.TABLE);
					values.setValue(Boolean.TRUE, LayoutPaths.ACTIVE);
				}
				values.setValue(pLabel, LayoutPaths.LABEL);
				values.setValue(pLayout, LayoutPaths.LAYOUT);
				values.setValue(Integer.valueOf(version), LayoutPaths.VERSION);
				values.setValue(user, LayoutPaths.UPDATED_BY);
				values.setValue(new Date(), LayoutPaths.UPDATED_ON);
				if (pPreviousForm != null)
				{
					values.setValue(pPreviousForm, LayoutPaths.PREVIOUS_FORM);
				}
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
		return version;
	}

	/** Records the class the model declared before the designer's, on the existing row. */
	static void setPreviousForm(final Repository pRepository, final Session pSession, final SchemaLocation pModel, final Path pTable, final String pPreviousForm)
		throws OperationException
	{
		final Adaptation existing = LayoutStore.findRow(pRepository, pModel, pTable);
		if (existing == null)
		{
			return;
		}
		final ProcedureResult result = ProgrammaticService.createForSession(pSession, existing.getHome()).execute(new Procedure()
		{
			@Override
			public void execute(final ProcedureContext pContext) throws Exception
			{
				pContext.setAllPrivileges(true);
				final ValueContextForUpdate values = pContext.getContext(existing.getAdaptationName());
				values.setValue(pPreviousForm, LayoutPaths.PREVIOUS_FORM);
				pContext.doModifyContent(existing, values);
			}
		});
		if (result.hasFailed())
		{
			throw OperationException.createError(result.getExceptionFullMessage(Locale.ENGLISH));
		}
	}
}
