package com.on.ebx.layout.designer;

import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.on.ebx.layout.runtime.Personal;
import com.onwbp.adaptation.Adaptation;
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
 * "Arrange my form", offered on a record whose table has a designed form that lets users arrange it
 * ({@code "personal": true}). Every user may use it, for themself only. Registered from the module registration: a
 * change here needs a server restart.
 */
public final class PersonalizeServiceDeclaration implements UserServiceDeclaration.OnRecord
{
	private final ServiceKey serviceKey;

	public PersonalizeServiceDeclaration(final String pModuleName)
	{
		this.serviceKey = ServiceKey.forModuleServiceName(pModuleName, LayoutPaths.PERSONALIZE_SERVICE);
	}

	@Override
	public ServiceKey getServiceKey()
	{
		return this.serviceKey;
	}

	@Override
	public UserService<RecordEntitySelection> createUserService()
	{
		return new PersonalizeUserService();
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
				try
				{
					final Adaptation record = pRule.getEntitySelection().getRecord();
					if (record != null && pRule.getSession().getUserReference() != null && Personal.allowed(LayoutStore.find(record.getHome().getRepository(),
						record.getContainer().getSchemaLocation(), record.getContainerTable().getTablePath())))
					{
						return UserServicePermission.getEnabled();
					}
				}
				catch (final RuntimeException ex)
				{
					// no layout to arrange
				}
				return UserServicePermission.getDisabled();
			}
		});
	}

	@Override
	public void defineProperties(final UserServicePropertiesDefinitionContext pContext)
	{
		pContext.setLabel(Messages.info("personal.label"));
		pContext.setDescription(Messages.info("personal.description"));
	}

	@Override
	public void declareWebComponent(final WebComponentDeclarationContext pContext)
	{
		pContext.setAvailableAsToolbarAction(true);
	}
}
