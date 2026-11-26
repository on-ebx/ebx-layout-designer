package com.on.ebx.layout.designer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationHome;
import com.onwbp.adaptation.AdaptationName;
import com.orchestranetworks.instance.HomeKey;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.instance.ValueContext;
import com.orchestranetworks.instance.ValueContextForValidation;
import com.orchestranetworks.schema.ConstraintContext;
import com.orchestranetworks.schema.ConstraintEnumeration;
import com.orchestranetworks.schema.InvalidSchemaException;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.LoggingCategory;

/**
 * The tables of the dataset chosen in the "new layout" form: reads the sibling fields {@code dataspace} and
 * {@code dataset} of the form's own bean.
 */
public final class TableEnumeration implements ConstraintEnumeration<String>
{
	static final Path DATASPACE = Path.parse("../dataspace");
	static final Path DATASET = Path.parse("../dataset");

	@Override
	public void setup(final ConstraintContext pContext)
	{
		// values depend on the two sibling fields
	}

	@Override
	public void checkOccurrence(final String pValue, final ValueContextForValidation pContext)
	{
		// the choice comes from the list
	}

	@Override
	public String toUserDocumentation(final Locale pLocale, final ValueContext pContext) throws InvalidSchemaException
	{
		return null;
	}

	@Override
	public List<String> getValues(final ValueContext pContext) throws InvalidSchemaException
	{
		final List<String> out = new ArrayList<String>();
		final Adaptation dataset = TableEnumeration.dataset(pContext.getValue(TableEnumeration.DATASPACE), pContext.getValue(TableEnumeration.DATASET));
		if (dataset != null)
		{
			TableEnumeration.tables(dataset.getSchemaNode(), out);
		}
		return out;
	}

	@Override
	public String displayOccurrence(final String pValue, final ValueContext pContext, final Locale pLocale) throws InvalidSchemaException
	{
		try
		{
			final Adaptation dataset = TableEnumeration.dataset(pContext.getValue(TableEnumeration.DATASPACE), pContext.getValue(TableEnumeration.DATASET));
			final SchemaNode node = dataset == null ? null : dataset.getSchemaNode().getNode(Path.parse(pValue));
			return node == null ? pValue : node.getLabel(pLocale) + " — " + pValue;
		}
		catch (final RuntimeException ex)
		{
			return pValue;
		}
	}

	static Adaptation dataset(final Object pDataspace, final Object pDataset)
	{
		if (pDataspace == null || pDataset == null)
		{
			return null;
		}
		try
		{
			final AdaptationHome home = Repository.getDefault().lookupHome(TableEnumeration.homeKey(String.valueOf(pDataspace)));
			return home == null ? null : home.findAdaptationOrNull(AdaptationName.forName(String.valueOf(pDataset)));
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] dataset " + pDataspace + "/" + pDataset + " not found: " + ex.getMessage());
			return null;
		}
	}

	/** A dataspace field holds the formatted key ({@code BCompanies}); accept a bare name too. */
	static HomeKey homeKey(final String pValue)
	{
		try
		{
			return HomeKey.parse(pValue);
		}
		catch (final RuntimeException ex)
		{
			return HomeKey.forBranchName(pValue);
		}
	}

	private static void tables(final SchemaNode pNode, final List<String> pOut)
	{
		for (final SchemaNode child : pNode.getNodeChildren())
		{
			if (child.isTableNode())
			{
				pOut.add(child.getPathInSchema().format());
			}
			else if (child.isComplex() && !child.isTerminalValue())
			{
				TableEnumeration.tables(child, pOut);
			}
		}
	}
}
