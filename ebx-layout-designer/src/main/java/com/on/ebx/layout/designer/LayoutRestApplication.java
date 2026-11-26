package com.on.ebx.layout.designer;

import jakarta.ws.rs.ApplicationPath;

import com.orchestranetworks.rest.RESTApplicationAbstract;

/**
 * REST application of the module on the EBX REST Toolkit (EBX authentication: HTTP Basic, token, SSO), declared in web.xml
 * through {@code RESTRegistrationServlet}: {@code /ebx-layout-designer/rest/mcp} (1.21.0).
 */
@ApplicationPath("/rest")
public final class LayoutRestApplication extends RESTApplicationAbstract
{
	public LayoutRestApplication()
	{
		super(pConfigurator -> pConfigurator.register(LayoutMcpEndpoint.class));
	}
}
