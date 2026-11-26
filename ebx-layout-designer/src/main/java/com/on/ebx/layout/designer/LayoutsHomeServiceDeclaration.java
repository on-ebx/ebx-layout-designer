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
 * "Record form layouts": the entry point of the designer, on the module's own {@code FormLayout} table — the saved
 * layouts, and a choice of dataspace, dataset and table to lay out a new one. Meant for a perspective action.
 */
public final class LayoutsHomeServiceDeclaration implements UserServiceDeclaration.OnTableView
{
	public static final String SERVICE_NAME = "LayoutsHome";

	private final ServiceKey serviceKey;

	public LayoutsHomeServiceDeclaration(final String pModuleName)
	{
		this.serviceKey = ServiceKey.forModuleServiceName(pModuleName, LayoutsHomeServiceDeclaration.SERVICE_NAME);
	}

	@Override
	public ServiceKey getServiceKey()
	{
		return this.serviceKey;
	}

	@Override
	public UserService<TableViewEntitySelection> createUserService()
	{
		return new LayoutsHomeUserService();
	}

	@Override
	public void defineActivation(final ActivationContextOnTableView pContext)
	{
		pContext.includeAllDatasets();
		pContext.includeSchemaNodesMatching(LayoutPaths.FORM_LAYOUT);
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
		pContext.setLabel(Messages.info("home.label"));
		pContext.setDescription(Messages.info("home.description"));
	}

	@Override
	public void declareWebComponent(final WebComponentDeclarationContext pContext)
	{
		pContext.setAvailableAsToolbarAction(true);
		pContext.setAvailableAsPerspectiveAction(true);
	}
}
