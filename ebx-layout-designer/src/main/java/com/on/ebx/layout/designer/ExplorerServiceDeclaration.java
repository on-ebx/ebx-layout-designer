package com.on.ebx.layout.designer;

import com.on.ebx.layout.runtime.LayoutPaths;
import com.orchestranetworks.service.ServiceKey;
import com.orchestranetworks.ui.selection.TableViewEntitySelection;
import com.orchestranetworks.userservice.UserService;
import com.orchestranetworks.userservice.declaration.ActivationContextOnTableView;
import com.orchestranetworks.userservice.declaration.UserServiceDeclaration;
import com.orchestranetworks.userservice.declaration.UserServicePropertiesDefinitionContext;
import com.orchestranetworks.userservice.declaration.WebComponentDeclarationContext;

/**
 * "Explorer", the search page an explorer drawn in the designer describes, on the table view of every table. Not in
 * the Actions menus: a perspective action or a link opens it, with the explorer id in {@code ldxExplorer} (without it,
 * the first active explorer of the table). What a user sees is decided by the explorer's roles and by EBX permissions.
 */
public final class ExplorerServiceDeclaration implements UserServiceDeclaration.OnTableView
{
	private final ServiceKey serviceKey;

	public ExplorerServiceDeclaration(final String pModuleName)
	{
		this.serviceKey = ServiceKey.forModuleServiceName(pModuleName, LayoutPaths.EXPLORER_SERVICE);
	}

	@Override
	public ServiceKey getServiceKey()
	{
		return this.serviceKey;
	}

	@Override
	public UserService<TableViewEntitySelection> createUserService()
	{
		return new ExplorerUserService();
	}

	@Override
	public void defineActivation(final ActivationContextOnTableView pContext)
	{
		pContext.includeAllDatasets();
		pContext.includeAllSchemaNodes();
	}

	@Override
	public void defineProperties(final UserServicePropertiesDefinitionContext pContext)
	{
		pContext.setGroup(com.on.ebx.layout.designer.LayoutDesignerRegistration.SERVICE_GROUP);
		pContext.setLabel(Messages.info("explorer.label"));
		pContext.setDescription(Messages.info("explorer.description"));
	}

	@Override
	public void declareWebComponent(final WebComponentDeclarationContext pContext)
	{
		pContext.setAvailableAsToolbarAction(false);
		pContext.setAvailableAsPerspectiveAction(true);
		pContext.addInputParameter(LayoutPaths.EXPLORER_PARAM, Messages.info("explorer.param"), Messages.info("explorer.paramDescription"));
	}
}
