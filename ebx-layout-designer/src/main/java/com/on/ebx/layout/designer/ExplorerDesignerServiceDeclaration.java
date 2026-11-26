package com.on.ebx.layout.designer;

import com.on.ebx.layout.runtime.LayoutPaths;

import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.ServiceKey;
import com.orchestranetworks.ui.selection.TableViewEntitySelection;
import com.orchestranetworks.userservice.UserService;
import com.orchestranetworks.userservice.declaration.ActivationContextOnTableView;
import com.orchestranetworks.userservice.declaration.UserServiceDeclaration;
import com.orchestranetworks.userservice.declaration.UserServicePropertiesDefinitionContext;
import com.orchestranetworks.userservice.declaration.WebComponentDeclarationContext;
import com.orchestranetworks.userservice.permission.ServicePermissionRule;
import com.orchestranetworks.userservice.permission.ServicePermissionRuleContext;
import com.orchestranetworks.userservice.permission.UserServicePermission;

/**
 * "Design the explorer", a module-level service offered on the table view of every table of every model
 * (module XSD or DMA publication), to administrators only. Registered from the module registration: a change here
 * needs a server restart.
 */
public final class ExplorerDesignerServiceDeclaration implements UserServiceDeclaration.OnTableView
{
	public static final String SERVICE_NAME = LayoutPaths.EXPLORER_DESIGNER_SERVICE;

	private final ServiceKey serviceKey;

	public ExplorerDesignerServiceDeclaration(final String pModuleName)
	{
		this.serviceKey = ServiceKey.forModuleServiceName(pModuleName, ExplorerDesignerServiceDeclaration.SERVICE_NAME);
	}

	@Override
	public ServiceKey getServiceKey()
	{
		return this.serviceKey;
	}

	@Override
	public UserService<TableViewEntitySelection> createUserService()
	{
		return new ExplorerDesignerUserService();
	}

	@Override
	public void defineActivation(final ActivationContextOnTableView pContext)
	{
		pContext.includeAllDatasets();
		pContext.includeAllSchemaNodes();
		pContext.setPermissionRule(new ServicePermissionRule<TableViewEntitySelection>()
		{
			@Override
			public UserServicePermission getPermission(final ServicePermissionRuleContext<TableViewEntitySelection> pRule)
			{
				return pRule.getSession().isUserInRole(Profile.ADMINISTRATOR) ? UserServicePermission.getEnabled() : UserServicePermission.getDisabled();
			}
		});
	}

	@Override
	public void defineProperties(final UserServicePropertiesDefinitionContext pContext)
	{
		pContext.setLabel(Messages.info("explorerDesigner.label"));
		pContext.setDescription(Messages.info("explorerDesigner.description"));
	}

	@Override
	public void declareWebComponent(final WebComponentDeclarationContext pContext)
	{
		pContext.setAvailableAsToolbarAction(true);
		pContext.setAvailableAsPerspectiveAction(true);
	}
}
