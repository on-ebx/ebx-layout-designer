package com.on.ebx.layout.designer;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The MCP endpoint's transport, without a server: same-origin rule, initialize, tools/list for an administrator and for
 * anyone else, tool schemas, notifications, unknown methods and tools. Run by tools/test.sh.
 */
public final class LayoutMcpTest
{
	private static final ObjectMapper JSON = new ObjectMapper();
	private static int failures;

	public static void main(final String[] pArgs) throws Exception
	{
		LayoutMcpTest.origin();
		LayoutMcpTest.protocol();
		if (LayoutMcpTest.failures > 0)
		{
			System.out.println(LayoutMcpTest.failures + " MCP check(s) failed");
			System.exit(1);
		}
		System.out.println("MCP checks passed");
	}

	private static void origin()
	{
		LayoutMcpTest.check("no origin (a program)", LayoutMcpEndpoint.sameOrigin(null, "localhost:8080", null));
		LayoutMcpTest.check("same origin", LayoutMcpEndpoint.sameOrigin("http://localhost:8080", "localhost:8080", null));
		LayoutMcpTest.check("other port refused", !LayoutMcpEndpoint.sameOrigin("http://localhost:9999", "localhost:8080", null));
		LayoutMcpTest.check("other site refused", !LayoutMcpEndpoint.sameOrigin("https://evil.example", "localhost:8080", null));
		LayoutMcpTest.check("behind a proxy", LayoutMcpEndpoint.sameOrigin("https://ebx.example.com", "10.0.0.2:8080", "ebx.example.com"));
		LayoutMcpTest.check("garbage refused", !LayoutMcpEndpoint.sameOrigin("::::", "localhost:8080", null));
	}

	private static void protocol() throws Exception
	{
		final LayoutMcpTools tools = new LayoutMcpTools(null, null, Locale.ENGLISH);
		final JsonNode init = LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree(
			"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\"}}"), tools, true);
		LayoutMcpTest.check("initialize echoes the version", "2025-03-26".equals(init.path("result").path("protocolVersion").asText()));
		LayoutMcpTest.check("initialize names the server", "ebx-layout-designer".equals(init.path("result").path("serverInfo").path("name").asText()));
		final JsonNode admin = LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"), tools, true);
		LayoutMcpTest.check("ten tools for an administrator", admin.path("result").path("tools").size() == 10);
		final JsonNode other = LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}"), tools, false);
		LayoutMcpTest.check("no tool for anyone else", other.path("result").path("tools").size() == 0);
		boolean schemas = true;
		for (final JsonNode t : admin.path("result").path("tools"))
		{
			schemas &= "object".equals(t.path("inputSchema").path("type").asText()) && t.path("description").asText().length() > 20;
			if ("layout_put".equals(t.path("name").asText()))
			{
				schemas &= !t.path("annotations").path("readOnlyHint").asBoolean(true);
				schemas &= t.path("inputSchema").path("properties").path("layout").path("type").isArray();
				schemas &= t.path("inputSchema").path("required").toString().contains("layout");
			}
		}
		LayoutMcpTest.check("tool schemas", schemas);
		LayoutMcpTest.check("notification: no answer",
			LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"), tools, true) == null);
		LayoutMcpTest.check("unknown method", LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"x/y\"}"), tools, true)
			.path("error").path("code").asInt() == -32601);
		LayoutMcpTest.check("unknown tool", LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree(
			"{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"}}"), tools, true).path("error").path("code").asInt() == -32602);
		final JsonNode refused = LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree(
			"{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"layout_help\"}}"), tools, false);
		LayoutMcpTest.check("a call without a session is refused", refused.path("result").path("isError").asBoolean()
			&& "FORBIDDEN".equals(refused.path("result").path("structuredContent").path("code").asText()));
	}

	private static void check(final String pWhat, final boolean pOk)
	{
		if (!pOk)
		{
			LayoutMcpTest.failures++;
			System.out.println("FAILED: " + pWhat);
		}
	}
}
