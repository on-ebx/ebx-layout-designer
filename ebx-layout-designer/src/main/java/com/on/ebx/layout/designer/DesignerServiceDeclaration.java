package com.on.ebx.layout.designer;

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
 * "Design the record form", a module-level service offered on the table view of every table of every model
 * (module XSD or DMA publication), to administrators only. Registered from the module registration: a change here
 * needs a server restart.
 */
public final class DesignerServiceDeclaration implements UserServiceDeclaration.OnTableView
{
	public static final String SERVICE_NAME = "LayoutDesigner";

	private final ServiceKey serviceKey;

	public DesignerServiceDeclaration(final String pModuleName)
	{
		this.serviceKey = ServiceKey.forModuleServiceName(pModuleName, DesignerServiceDeclaration.SERVICE_NAME);
	}

	@Override
	public ServiceKey getServiceKey()
	{
		return this.serviceKey;
	}

	@Override
	public UserService<TableViewEntitySelection> createUserService()
	{
		return new DesignerUserService();
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
		pContext.setLabel(Messages.info("service.label"));
		pContext.setDescription(Messages.info("service.description"));
	}

	@Override
	public void declareWebComponent(final WebComponentDeclarationContext pContext)
	{
		pContext.setAvailableAsToolbarAction(true);
		pContext.setAvailableAsPerspectiveAction(true);
	}
}
