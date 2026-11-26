package com.on.ebx.layout.designer;

import com.on.ebx.layout.runtime.LayoutPaths;
import com.orchestranetworks.service.ServiceKey;
import com.orchestranetworks.ui.selection.RecordEntitySelection;
import com.orchestranetworks.userservice.UserService;
import com.orchestranetworks.userservice.declaration.ActivationContextOnRecord;
import com.orchestranetworks.userservice.declaration.UserServiceDeclaration;
import com.orchestranetworks.userservice.declaration.UserServicePropertiesDefinitionContext;
import com.orchestranetworks.userservice.declaration.WebComponentDeclarationContext;
import com.orchestranetworks.userservice.permission.ServicePermissionRule;
import com.orchestranetworks.userservice.permission.ServicePermissionRuleContext;
import com.orchestranetworks.userservice.permission.UserServicePermission;

/**
 * Launches a workflow on a record from a button of an "actions" block, in EBX's pop-up (1.14.0).
 * Never offered in a menu; opened only by the form. Registered from the module registration.
 */
public final class ActionServiceDeclaration implements UserServiceDeclaration.OnRecord
{
	private final ServiceKey serviceKey;

	public ActionServiceDeclaration(final String pModuleName)
	{
		this.serviceKey = ServiceKey.forModuleServiceName(pModuleName, LayoutPaths.ACTION_SERVICE);
	}

	@Override
	public ServiceKey getServiceKey()
	{
		return this.serviceKey;
	}

	@Override
	public UserService<RecordEntitySelection> createUserService()
	{
		return new ActionUserService();
	}

	@Override
	public void defineActivation(final ActivationContextOnRecord pContext)
	{
		pContext.includeAllDatasets();
		pContext.includeAllSchemaNodes();
		pContext.setPermissionRule(new ServicePermissionRule<RecordEntitySelection>()
		{
			@Override
			public UserServicePermission getPermission(final ServicePermissionRuleContext<RecordEntitySelection> pRule)
			{
				// never in a menu; the frame of a form opens it
				return pRule.isForDisplay() ? UserServicePermission.getDisabled() : UserServicePermission.getEnabled();
			}
		});
	}

	@Override
	public void defineProperties(final UserServicePropertiesDefinitionContext pContext)
	{
		pContext.setLabel(Messages.info("action.label"));
	}

	@Override
	public void declareWebComponent(final WebComponentDeclarationContext pContext)
	{
		pContext.setAvailableAsToolbarAction(false);
		pContext.setAvailableAsPerspectiveAction(false);
	}
}
