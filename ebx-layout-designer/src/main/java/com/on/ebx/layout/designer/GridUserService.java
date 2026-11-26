package com.on.ebx.layout.designer;

import com.fasterxml.jackson.databind.JsonNode;
import com.on.ebx.layout.runtime.Audience;
import com.on.ebx.layout.runtime.BlockAccess;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.ui.selection.RecordEntitySelection;
import com.orchestranetworks.userservice.ObjectKey;
import com.orchestranetworks.userservice.UserService;
import com.orchestranetworks.userservice.UserServiceDisplayConfigurator;
import com.orchestranetworks.userservice.UserServiceEventOutcome;
import com.orchestranetworks.userservice.UserServiceObjectContextBuilder;
import com.orchestranetworks.userservice.UserServicePane;
import com.orchestranetworks.userservice.UserServicePaneContext;
import com.orchestranetworks.userservice.UserServicePaneWriter;
import com.orchestranetworks.userservice.UserServiceProcessEventOutcomeContext;
import com.orchestranetworks.userservice.UserServiceSetupDisplayContext;
import com.orchestranetworks.userservice.UserServiceSetupObjectContext;
import com.orchestranetworks.userservice.UserServiceValidateContext;

/**
 * Draws one association of the record with EBX's own grid (toolbar, "+", filter, paging), nothing else: no header, no
 * bottom bar. The block is looked up again in the layout as this user sees it, so a grid the user may not see (role,
 * EBX permission) is not drawn here either.
 */
public final class GridUserService implements UserService<RecordEntitySelection>
{
	private static final ObjectKey RECORD = ObjectKey.forName("record");

	private Adaptation record;
	private String blockId;

	@Override
	public void setupObjectContext(final UserServiceSetupObjectContext<RecordEntitySelection> pContext, final UserServiceObjectContextBuilder pBuilder)
	{
		if (this.record == null)
		{
			this.record = pContext.getEntitySelection().getRecord();
			this.blockId = pContext.getParameter(LayoutPaths.GRID_BLOCK_PARAM);
		}
		if (this.record != null)
		{
			pBuilder.registerRecordOrDataSet(GridUserService.RECORD, this.record);
		}
	}

	@Override
	public void setupDisplay(final UserServiceSetupDisplayContext<RecordEntitySelection> pContext, final UserServiceDisplayConfigurator pConfigurator)
	{
		final Session session = pContext.getSession();
		pConfigurator.setHeaderDisplayed(false);
		pConfigurator.setBottomBarDisplayed(false);
		pConfigurator.setDetectionOfLostModificationDisabled(true);
		pConfigurator.setContent(new UserServicePane()
		{
			@Override
			public void writePane(final UserServicePaneContext pPaneContext, final UserServicePaneWriter pWriter)
			{
				GridUserService.this.write(pWriter, session);
			}
		});
	}

	private void write(final UserServicePaneWriter pWriter, final Session pSession)
	{
		final String a = this.association(pSession);
		if (a == null)
		{
			LoggingCategory.getKernel().info("[layout-designer] lazy grid " + this.blockId + ": nothing to draw for this user");
			return;
		}
		pWriter.add("<style>body{background:#FFFFFF}</style>");
		pWriter.setCurrentObject(GridUserService.RECORD);
		pWriter.addWidget(LayoutPaths.relative(a));
	}

	/** The association of the block, if the user may see it; null otherwise. */
	private String association(final Session pSession)
	{
		if (this.record == null || this.blockId == null || this.blockId.isEmpty())
		{
			return null;
		}
		try
		{
			final JsonNode layout = LayoutStore.find(this.record.getHome().getRepository(), this.record.getContainer().getSchemaLocation(),
				this.record.getContainerTable().getTablePath());
			if (layout == null)
			{
				return null;
			}
			final boolean seesAll = layout.path("adminSeesAll").asBoolean(true) && pSession.isUserInRole(Profile.ADMINISTRATOR);
			final JsonNode resolved = Audience.resolve(layout, roles -> PersonalizeUserService.inAnyRole(pSession, roles), seesAll);
			for (final java.util.List<JsonNode> blocks : Audience.blocksByTab(resolved).values())
			{
				for (final JsonNode b : blocks)
				{
					if (this.blockId.equals(b.path("id").asText()) && "grid".equals(b.path("type").asText()))
					{
						final String a = b.path("a").asText("");
						final Path p = LayoutPaths.relative(a);
						if (p == null || this.record.getSchemaNode().getNode(p) == null || !BlockAccess.readable(b, pSession, this.record))
						{
							return null;
						}
						return a;
					}
				}
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().error("[layout-designer] lazy grid " + this.blockId + " not drawn: " + ex, ex);
		}
		return null;
	}

	@Override
	public void validate(final UserServiceValidateContext<RecordEntitySelection> pContext)
	{
		// nothing posted
	}

	@Override
	public UserServiceEventOutcome processEventOutcome(final UserServiceProcessEventOutcomeContext<RecordEntitySelection> pContext, final UserServiceEventOutcome pOutcome)
	{
		return pOutcome;
	}
}
