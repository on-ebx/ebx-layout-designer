package com.on.ebx.layout.designer;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.on.ebx.layout.runtime.LayoutCheck;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.base.text.UserMessage;
import com.onwbp.base.text.UserMessageLocalized;
import com.orchestranetworks.instance.ValueContextForValidation;
import com.orchestranetworks.schema.Constraint;
import com.orchestranetworks.schema.ConstraintContext;
import com.orchestranetworks.schema.InvalidSchemaException;
import com.orchestranetworks.schema.Path;
import com.onwbp.base.text.Severity;

/**
 * Validation of a FormLayout row (1.17.1): what its layout refers to that no longer exists, through {@link LayoutCheck}.
 * Never blocks a save (a layout must stay editable while its model changes). It declares no dependency, so EBX checks it
 * again at each validation: a field removed from another data model shows at the next validation of this table.
 */
public final class LayoutValidation implements Constraint<String>
{
	private static final Path MODEL = Path.parse("../model");
	private static final Path TABLE = Path.parse("../table");
	private static final Locale[] LOCALES = { Locale.US, Locale.FRANCE, Locale.ITALY };

	private String checks = "all";

	public String getChecks()
	{
		return this.checks;
	}

	public void setChecks(final String pChecks)
	{
		this.checks = pChecks;
	}

	@Override
	public void setup(final ConstraintContext pContext)
	{
		pContext.setBlocksCommitToNever();
	}

	@Override
	public void checkOccurrence(final String pValue, final ValueContextForValidation pContext) throws InvalidSchemaException
	{
		if (pValue == null || pValue.trim().isEmpty() || "off".equals(this.checks))
		{
			return;
		}
		final JsonNode layout;
		try
		{
			layout = LayoutStore.json().readTree(pValue);
		}
		catch (final Exception ex)
		{
			pContext.addError("JSON: " + ex.getMessage());
			return;
		}
		final Object model = pContext.getValue(LayoutValidation.MODEL);
		final Object table = pContext.getValue(LayoutValidation.TABLE);
		for (final LayoutCheck.Issue issue : LayoutCheck.check(pContext.getHome().getRepository(), model == null ? null : String.valueOf(model),
			table == null ? null : String.valueOf(table), layout, Locale.US))
		{
			final UserMessageLocalized m = new UserMessageLocalized();
			for (final Locale l : LayoutValidation.LOCALES)
			{
				m.setMessage(l, issue.text(l));
			}
			m.setMessage(issue.text(Locale.US));
			pContext.addMessage(issue.error ? LayoutValidation.severity(m, Severity.ERROR) : LayoutValidation.severity(m, Severity.WARNING));
		}
	}

	private static UserMessage severity(final UserMessageLocalized pMessage, final Severity pSeverity)
	{
		pMessage.setSeverity(pSeverity);
		return pMessage;
	}

	@Override
	public String toUserDocumentation(final Locale pLocale, final com.orchestranetworks.instance.ValueContext pContext) throws InvalidSchemaException
	{
		return Locale.FRENCH.getLanguage().equals(pLocale.getLanguage()) ? "Les champs, associations, workflows et composants cités par la mise en page existent."
			: "The fields, associations, workflows and components the layout refers to exist.";
	}
}
