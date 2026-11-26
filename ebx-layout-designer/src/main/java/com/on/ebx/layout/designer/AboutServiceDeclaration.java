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
 * "About": the version of the module and its documentation, on the module's own {@code FormLayout} table, for every
 * user who can open it. Meant for a perspective action next to "Record form layouts".
 */
public final class AboutServiceDeclaration implements UserServiceDeclaration.OnTableView
{
	public static final String SERVICE_NAME = "About";

	private final ServiceKey serviceKey;

	public AboutServiceDeclaration(final String pModuleName)
	{
		this.serviceKey = ServiceKey.forModuleServiceName(pModuleName, AboutServiceDeclaration.SERVICE_NAME);
	}

	/** The user guide served by the module, French for a French session. */
	static String guideUrl(final java.util.Locale pLocale)
	{
		final boolean french = pLocale != null && "fr".equals(pLocale.getLanguage());
		return "/" + LayoutPaths.MODULE_NAME + "/www/help/" + (french ? "guide-utilisateur.html" : "user-guide.html");
	}

	@Override
	public ServiceKey getServiceKey()
	{
		return this.serviceKey;
	}

	@Override
	public UserService<TableViewEntitySelection> createUserService()
	{
		return new AboutUserService();
	}

	@Override
	public void defineActivation(final ActivationContextOnTableView pContext)
	{
		pContext.includeAllDatasets();
		pContext.includeSchemaNodesMatching(LayoutPaths.FORM_LAYOUT);
	}

	@Override
	public void defineProperties(final UserServicePropertiesDefinitionContext pContext)
	{
		pContext.setLabel(Messages.info("about.label"));
		pContext.setDescription(Messages.info("about.description"));
	}

	@Override
	public void declareWebComponent(final WebComponentDeclarationContext pContext)
	{
		pContext.setAvailableAsToolbarAction(true);
		pContext.setAvailableAsPerspectiveAction(true);
	}
}
