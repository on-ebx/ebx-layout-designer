package com.on.ebx.layout.designer;

import jakarta.servlet.annotation.WebListener;

import com.orchestranetworks.module.ModuleContextOnRepositoryStartup;
import com.orchestranetworks.module.ModuleRegistrationListener;
import com.orchestranetworks.module.ModuleServiceRegistrationContext;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.OperationException;

/** Registers the module: its dataset at repository startup, the "Design the record form", "Record form layouts", "About", "Arrange my form", "Explorer" and "Design the explorer" services. */
@WebListener
public final class LayoutDesignerRegistration extends ModuleRegistrationListener
{
	/** The Actions submenu that holds this module's services. */
	public static final com.orchestranetworks.service.ServiceGroupKey SERVICE_GROUP = com.orchestranetworks.service.ServiceGroupKey.forServiceGroupInModule("ebx-layout-designer", "LayoutDesigner");

	@Override
	public void handleRepositoryStartup(final ModuleContextOnRepositoryStartup pContext) throws OperationException
	{
		super.handleRepositoryStartup(pContext);
		// the kernel log: a module's own logging category is written nowhere on a default installation
		Installer.install(pContext.getRepository(), pContext.createSystemUserSession("layout-designer"), pContext.getModuleName(), LoggingCategory.getKernel());
	}

	/**
	 * Stops the explorer's thread pool (L2-18). Called after the web application is stopped: no class that was not loaded
	 * yet may be needed here (no lambda, no anonymous class), and a class that cannot load means the pool never started.
	 */
	@Override
	public void handleRepositoryShutdown()
	{
		try
		{
			com.on.ebx.layout.explorer.Engine.shutdown();
		}
		catch (final LinkageError ex)
		{
			// the explorer was never used in this run, or its class loader is already stopped: there is no pool to stop
			ex.getClass();
		}
		super.handleRepositoryShutdown();
	}

	@Override
	public void handleServiceRegistration(final ModuleServiceRegistrationContext pContext)
	{
		super.handleServiceRegistration(pContext);
		// one submenu per module in the Actions menus of business tables, registered before the services that use it
		final com.onwbp.base.text.UserMessageString groupLabel = new com.onwbp.base.text.UserMessageString();
		groupLabel.setString(java.util.Locale.FRENCH, "Layout Designer");
		groupLabel.setStringForDefault("Layout Designer");
		final com.onwbp.base.text.UserMessageString groupDescription = new com.onwbp.base.text.UserMessageString();
		groupDescription.setString(java.util.Locale.FRENCH, "Fiches, explorateurs et leur conception.");
		groupDescription.setStringForDefault("Record forms, explorers and their design.");
		pContext.registerServiceGroup(SERVICE_GROUP, groupLabel, groupDescription);
		pContext.registerUserService(new DesignerServiceDeclaration(pContext.getModuleName()));
		pContext.registerUserService(new LayoutsHomeServiceDeclaration(pContext.getModuleName()));
		pContext.registerUserService(new AboutServiceDeclaration(pContext.getModuleName()));
		pContext.registerUserService(new PersonalizeServiceDeclaration(pContext.getModuleName()));
		pContext.registerUserService(new GridServiceDeclaration(pContext.getModuleName()));
		pContext.registerUserService(new ActionServiceDeclaration(pContext.getModuleName()));
		pContext.registerUserService(new ExplorerServiceDeclaration(pContext.getModuleName()));
		pContext.registerUserService(new ExplorerDesignerServiceDeclaration(pContext.getModuleName()));
	}
}
