package com.on.ebx.layout.designer;

import jakarta.servlet.annotation.WebListener;

import com.orchestranetworks.module.ModuleContextOnRepositoryStartup;
import com.orchestranetworks.module.ModuleRegistrationListener;
import com.orchestranetworks.module.ModuleServiceRegistrationContext;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.OperationException;

/** Registers the module: its dataset at repository startup, the "Design the record form", "Record form layouts" and "About" services. */
@WebListener
public final class LayoutDesignerRegistration extends ModuleRegistrationListener
{
	@Override
	public void handleRepositoryStartup(final ModuleContextOnRepositoryStartup pContext) throws OperationException
	{
		super.handleRepositoryStartup(pContext);
		// the kernel log: a module's own logging category is written nowhere on a default installation
		Installer.install(pContext.getRepository(), pContext.createSystemUserSession("layout-designer"), pContext.getModuleName(), LoggingCategory.getKernel());
	}

	@Override
	public void handleServiceRegistration(final ModuleServiceRegistrationContext pContext)
	{
		super.handleServiceRegistration(pContext);
		pContext.registerUserService(new DesignerServiceDeclaration(pContext.getModuleName()));
		pContext.registerUserService(new LayoutsHomeServiceDeclaration(pContext.getModuleName()));
		pContext.registerUserService(new AboutServiceDeclaration(pContext.getModuleName()));
	}
}
