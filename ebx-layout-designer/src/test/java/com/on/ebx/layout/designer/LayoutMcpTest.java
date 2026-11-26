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
		LayoutMcpTest.limits();
		LayoutMcpTest.failureText();
		LayoutMcpTest.apps();
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

	private static String deep(final int pLevels)
	{
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < pLevels; i++)
		{
			b.append("{\"a\":");
		}
		b.append("1");
		for (int i = 0; i < pLevels; i++)
		{
			b.append('}');
		}
		return b.toString();
	}

	private static java.io.InputStream stream(final String pText)
	{
		return new java.io.ByteArrayInputStream(pText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	/** L2-04: size and depth bounds, each refusal naming its JVM property; a non-administrator never reaches the parser. */
	private static void limits() throws Exception
	{
		try
		{
			LayoutMcpEndpoint.parseBody(LayoutMcpTest.stream("{\"x\":\"" + new String(new char[3000]).replace('\0', 'x') + "\"}"), 2048, 64);
			LayoutMcpTest.check("big body refused", false);
		}
		catch (final LayoutMcpEndpoint.BodyRefused ex)
		{
			LayoutMcpTest.check("big body: 413", ex.status == 413);
			LayoutMcpTest.check("big body names the property", ex.getMessage().contains(LayoutMcpEndpoint.PROP_MAX_BODY));
		}
		try
		{
			LayoutMcpEndpoint.parseBody(LayoutMcpTest.stream(LayoutMcpTest.deep(5000)), 2 * 1024 * 1024, 64);
			LayoutMcpTest.check("deep body refused", false);
		}
		catch (final LayoutMcpEndpoint.BodyRefused ex)
		{
			LayoutMcpTest.check("deep body: 400", ex.status == 400);
			LayoutMcpTest.check("deep body names the property", ex.getMessage().contains(LayoutMcpEndpoint.PROP_MAX_DEPTH));
		}
		LayoutMcpTest.check("brackets inside strings do not count",
			LayoutMcpEndpoint.parseBody(LayoutMcpTest.stream("{\"s\":\"[[[[[[[[\\\"[[[[\"}"), 2048, 3).path("s").isTextual());
		LayoutMcpTest.check("a normal body parses", LayoutMcpEndpoint.parseBody(LayoutMcpTest.stream(LayoutMcpTest.deep(10)), 2048, 64).isObject());
		try
		{
			LayoutMcpEndpoint.parseBody(LayoutMcpTest.stream("{oops"), 2048, 64);
			LayoutMcpTest.check("bad JSON refused", false);
		}
		catch (final LayoutMcpEndpoint.BodyRefused ex)
		{
			LayoutMcpTest.check("bad JSON: coded, no parser text", ex.status == 400 && ex.getMessage().indexOf("oops") < 0 && ex.getMessage().indexOf("Unexpected") < 0);
		}
		// no session: 401 before anything is read; a stream that explodes when touched proves the body is not read
		final java.io.InputStream bomb = new java.io.InputStream()
		{
			@Override
			public int read()
			{
				throw new IllegalStateException("body was read");
			}
		};
		final com.orchestranetworks.service.Session plain = (com.orchestranetworks.service.Session) java.lang.reflect.Proxy.newProxyInstance(
			LayoutMcpTest.class.getClassLoader(), new Class<?>[] { com.orchestranetworks.service.Session.class }, new java.lang.reflect.InvocationHandler()
			{
				public Object invoke(final Object pProxy, final java.lang.reflect.Method pMethod, final Object[] pArgs)
				{
					return "isUserInRole".equals(pMethod.getName()) ? Boolean.FALSE : null;
				}
			});
		final com.orchestranetworks.rest.inject.SessionContext context = (com.orchestranetworks.rest.inject.SessionContext) java.lang.reflect.Proxy.newProxyInstance(
			LayoutMcpTest.class.getClassLoader(), new Class<?>[] { com.orchestranetworks.rest.inject.SessionContext.class }, new java.lang.reflect.InvocationHandler()
			{
				public Object invoke(final Object pProxy, final java.lang.reflect.Method pMethod, final Object[] pArgs)
				{
					return "getSessionOrNull".equals(pMethod.getName()) ? plain : null;
				}
			});
		final jakarta.ws.rs.core.Response r = new LayoutMcpEndpoint(context).handle(bomb, null, null, null);
		LayoutMcpTest.check("not an administrator: 403 without reading the body", r.getStatus() == 403);
	}

	/** L2-12: a tool that fails answers a coded message, never the exception's class or text. */
	private static void failureText() throws Exception
	{
		final com.orchestranetworks.service.Session admin = (com.orchestranetworks.service.Session) java.lang.reflect.Proxy.newProxyInstance(
			LayoutMcpTest.class.getClassLoader(), new Class<?>[] { com.orchestranetworks.service.Session.class }, new java.lang.reflect.InvocationHandler()
			{
				public Object invoke(final Object pProxy, final java.lang.reflect.Method pMethod, final Object[] pArgs)
				{
					return "isUserInRole".equals(pMethod.getName()) ? Boolean.TRUE : null;
				}
			});
		final com.fasterxml.jackson.databind.node.ObjectNode a = LayoutMcpTest.JSON.createObjectNode();
		a.put("dataspace", "X").put("dataset", "Y").put("table", "/root/T");
		final String text = new LayoutMcpTools(admin, null, Locale.ENGLISH).invoke("layout_get", a).toString();
		LayoutMcpTest.check("failure is coded", text.contains("\"code\":\"FAILED\"") && text.contains("kernel.log"));
		LayoutMcpTest.check("failure shows no exception", !text.contains("Exception") && !text.contains("java."));
		final java.lang.reflect.Method document = LayoutMcpTools.class.getDeclaredMethod("document", com.fasterxml.jackson.databind.JsonNode.class, String.class);
		document.setAccessible(true);
		String doc = "";
		try
		{
			document.invoke(null, LayoutMcpTest.JSON.createObjectNode().put("layout", "{not json"), "layout");
		}
		catch (final java.lang.reflect.InvocationTargetException ex)
		{
			doc = ex.getCause().getClass().getSimpleName() + " " + ex.getCause().getMessage();
		}
		LayoutMcpTest.check("bad layout text: coded, no parser text", doc.startsWith("Refusal") && doc.contains("fix its syntax") && !doc.contains("Unexpected"));
		try
		{
			document.invoke(null, LayoutMcpTest.JSON.createObjectNode().put("layout", deep(5000)), "layout");
			LayoutMcpTest.check("stringified deep layout refused", false);
		}
		catch (final java.lang.reflect.InvocationTargetException ex)
		{
			LayoutMcpTest.check("stringified deep layout refused", ex.getCause().getMessage().contains(LayoutMcpEndpoint.PROP_MAX_DEPTH));
		}
	}

	private static void check(final String pWhat, final boolean pOk)
	{
		if (!pOk)
		{
			LayoutMcpTest.failures++;
			System.out.println("FAILED: " + pWhat);
		}
	}

	/** MCP Apps (1.27.0): the form preview resource, its declaration, and the tools that point at it. */
	private static void apps() throws Exception
	{
		final LayoutMcpTools tools = new LayoutMcpTools(null, null, Locale.ENGLISH);
		final JsonNode init = LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree(
			"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}"), tools, true);
		final JsonNode caps = init.path("result").path("capabilities");
		LayoutMcpTest.check("apps: extension declared", LayoutMcpEndpoint.UI_MIME_TYPE.equals(
			caps.path("extensions").path(LayoutMcpEndpoint.UI_EXTENSION).path("mimeTypes").path(0).asText()));
		LayoutMcpTest.check("apps: resources capability", caps.has("resources") && caps.has("tools"));
		final String html = LayoutMcpEndpoint.previewHtml();
		LayoutMcpTest.check("apps: preview embedded", html.contains("ui/initialize") && html.contains("layout_describe_table"));
		LayoutMcpTest.check("apps: nothing loaded from outside", !html.matches("(?s).*<script[^>]+src=.*") && !html.contains("<link ") && !html.contains("@import"));
		final JsonNode list = LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"), tools, true);
		int withUi = 0;
		boolean right = true;
		for (final JsonNode t : list.path("result").path("tools"))
		{
			final String uri = t.path("_meta").path("ui").path("resourceUri").asText("");
			final boolean preview = LayoutMcpEndpoint.PREVIEW_TOOLS.contains(t.path("name").asText());
			if (!uri.isEmpty()) withUi++;
			right &= preview == LayoutMcpEndpoint.PREVIEW_UI_URI.equals(uri);
			right &= preview == LayoutMcpEndpoint.PREVIEW_UI_URI.equals(t.path("_meta").path("ui/resourceUri").asText(""));
		}
		LayoutMcpTest.check("apps: three read-only tools carry the preview", withUi == 3 && right);
		final JsonNode res = LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/list\"}"), tools, true);
		LayoutMcpTest.check("apps: resource listed", res.path("result").path("resources").size() == 1
			&& LayoutMcpEndpoint.PREVIEW_UI_URI.equals(res.path("result").path("resources").path(0).path("uri").asText()));
		final JsonNode none = LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"resources/list\"}"), tools, false);
		LayoutMcpTest.check("apps: no resource for anyone else", none.path("result").path("resources").size() == 0);
		final String read = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/read\",\"params\":{\"uri\":\"" + LayoutMcpEndpoint.PREVIEW_UI_URI + "\"}}";
		final JsonNode content = LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree(read), tools, true).path("result").path("contents").path(0);
		LayoutMcpTest.check("apps: resource read", LayoutMcpEndpoint.UI_MIME_TYPE.equals(content.path("mimeType").asText()) && html.equals(content.path("text").asText()));
		LayoutMcpTest.check("apps: read refused to anyone else", LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree(read), tools, false).path("error").path("code").asInt() == -32002);
		LayoutMcpTest.check("apps: unknown resource", LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree(
			"{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"resources/read\",\"params\":{\"uri\":\"ui://other\"}}"), tools, true).path("error").path("code").asInt() == -32002);
		LayoutMcpTest.check("apps: no templates", LayoutMcpEndpoint.dispatch(LayoutMcpTest.JSON.readTree(
			"{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"resources/templates/list\"}"), tools, true).path("result").path("resourceTemplates").size() == 0);
		LayoutMcpTest.check("apps: no base without a request", "".equals(LayoutMcpEndpoint.serverBase(null)));
	}
}
