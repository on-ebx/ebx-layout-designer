package com.on.ebx.layout.designer;

import java.net.URI;
import java.util.Locale;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.on.ebx.layout.runtime.LayoutStore;
import com.orchestranetworks.rest.inject.SessionContext;
import com.orchestranetworks.service.Session;

/**
 * The Layout Designer's MCP endpoint (1.21.0), {@code POST /ebx-layout-designer/rest/mcp}: MCP over streamable HTTP
 * (JSON-RPC 2.0, one JSON answer per request, no server-sent events), authenticated like any EBX REST call (Basic, token,
 * session), serving {@link LayoutMcpTools}. A custom module cannot register a native EBX MCP server (only core and add-on
 * modules may), hence this endpoint, modelled on the data catalog's one. Disabled with {@code -Debx.layoutDesigner.mcp=false}.
 */
@Path("/mcp")
@Produces(MediaType.APPLICATION_JSON)
public final class LayoutMcpEndpoint
{
	public static final String PROTOCOL_VERSION = "2025-06-18";
	private final SessionContext sessionContext;

	public LayoutMcpEndpoint(@Context final SessionContext pSessionContext)
	{
		this.sessionContext = pSessionContext;
	}

	static boolean enabled()
	{
		return !"false".equalsIgnoreCase(System.getProperty("ebx.layoutDesigner.mcp", "true").trim());
	}

	/**
	 * Streamable HTTP without server-sent events: a client probing {@code GET} (to open a stream) or {@code DELETE} (to end a
	 * session) must get {@code 405} with {@code Allow: POST, OPTIONS} to fall back to plain request/response; any other answer
	 * makes Claude Code drop the connection silently.
	 */
	@GET
	@Produces(MediaType.WILDCARD)
	public Response get()
	{
		return LayoutMcpEndpoint.methodNotAllowed();
	}

	@DELETE
	@Produces(MediaType.WILDCARD)
	public Response delete()
	{
		return LayoutMcpEndpoint.methodNotAllowed();
	}

	@OPTIONS
	@Produces(MediaType.WILDCARD)
	public Response options()
	{
		return Response.ok().header("Allow", "POST,OPTIONS").build();
	}

	static Response methodNotAllowed()
	{
		return LayoutMcpEndpoint.enabled() ? Response.status(405).header("Allow", "POST,OPTIONS").build() : Response.status(404).build();
	}

	@POST
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.WILDCARD })
	public Response handle(final String pBody, @HeaderParam("Origin") final String pOrigin, @HeaderParam("Host") final String pHost,
		@HeaderParam("X-Forwarded-Host") final String pForwardedHost)
	{
		if (!LayoutMcpEndpoint.enabled())
		{
			return Response.status(404).build();
		}
		// EBX accepts its session cookie here: a page of another site must not be able to make a signed-in browser post a layout
		if (!LayoutMcpEndpoint.sameOrigin(pOrigin, pHost, pForwardedHost))
		{
			return Response.status(403).entity("{\"error\":\"Cross-site request refused: the Origin header does not name this server.\"}").build();
		}
		final Session session = this.sessionContext.getSessionOrNull();
		if (session == null)
		{
			return Response.status(401).header("WWW-Authenticate", "Basic realm=\"EBX\"").entity("{\"error\":\"Authentication required\"}").build();
		}
		final JsonNode request;
		try
		{
			request = LayoutStore.json().readTree(pBody == null ? "" : pBody);
		}
		catch (final Exception ex)
		{
			return Response.status(400).entity(LayoutMcpEndpoint.error(null, -32700, "Parse error: " + ex.getMessage()).toString()).build();
		}
		if (request == null || request.isMissingNode())
		{
			return Response.status(400).entity(LayoutMcpEndpoint.error(null, -32600, "Invalid request").toString()).build();
		}
		final Locale locale = this.sessionContext.getLocale() == null ? Locale.ENGLISH : this.sessionContext.getLocale();
		final LayoutMcpTools tools = new LayoutMcpTools(session, this.sessionContext.getRepository(), locale);
		final boolean allowed = LayoutMcpTools.allowed(session);
		if (request.isArray())
		{
			final ArrayNode answers = LayoutStore.json().createArrayNode();
			for (final JsonNode one : request)
			{
				final JsonNode answer = LayoutMcpEndpoint.dispatch(one, tools, allowed);
				if (answer != null)
				{
					answers.add(answer);
				}
			}
			return answers.size() == 0 ? Response.status(202).build() : Response.ok(answers.toString()).build();
		}
		final JsonNode answer = LayoutMcpEndpoint.dispatch(request, tools, allowed);
		return answer == null ? Response.status(202).build() : Response.ok(answer.toString()).build();
	}

	/** No Origin (a program), or one naming the host the request was sent to. Pure. */
	static boolean sameOrigin(final String pOrigin, final String pHost, final String pForwardedHost)
	{
		if (pOrigin == null || pOrigin.trim().isEmpty())
		{
			return true;
		}
		try
		{
			final URI o = URI.create(pOrigin.trim());
			final String authority = o.getPort() < 0 ? o.getHost() : o.getHost() + ":" + o.getPort();
			for (final String h : new String[] { pHost, pForwardedHost })
			{
				if (h != null && !h.trim().isEmpty() && (h.trim().equalsIgnoreCase(authority) || h.trim().equalsIgnoreCase(o.getRawAuthority())))
				{
					return true;
				}
			}
			return false;
		}
		catch (final RuntimeException ex)
		{
			return false;
		}
	}

	/** One JSON-RPC message; null for a notification (no answer). A caller who is not an administrator sees no tool. */
	static JsonNode dispatch(final JsonNode pMessage, final LayoutMcpTools pTools, final boolean pAllowed)
	{
		final JsonNode id = pMessage.get("id");
		final String method = pMessage.path("method").asText("");
		final JsonNode params = pMessage.has("params") ? pMessage.get("params") : LayoutStore.json().createObjectNode();
		if (method.startsWith("notifications/"))
		{
			return null;
		}
		if ("initialize".equals(method))
		{
			final ObjectNode result = LayoutStore.json().createObjectNode();
			final JsonNode wanted = params.get("protocolVersion");
			result.put("protocolVersion", wanted == null || wanted.isNull() ? LayoutMcpEndpoint.PROTOCOL_VERSION : wanted.asText());
			result.putObject("capabilities").putObject("tools").put("listChanged", false);
			final ObjectNode info = result.putObject("serverInfo");
			info.put("name", LayoutMcpTools.SERVER_NAME);
			info.put("title", "ON EBX Layout Designer");
			info.put("version", ModuleVersion.version());
			result.put("instructions", LayoutMcpTools.SERVER_DESCRIPTION + " Start with layout_help.");
			return LayoutMcpEndpoint.result(id, result);
		}
		if ("ping".equals(method))
		{
			return LayoutMcpEndpoint.result(id, LayoutStore.json().createObjectNode());
		}
		if ("tools/list".equals(method))
		{
			final ObjectNode result = LayoutStore.json().createObjectNode();
			final ArrayNode tools = result.putArray("tools");
			if (pAllowed)
			{
				for (final LayoutMcpTools.Tool tool : LayoutMcpTools.CATALOG)
				{
					tools.add(LayoutMcpEndpoint.toolJson(tool));
				}
			}
			return LayoutMcpEndpoint.result(id, result);
		}
		if ("tools/call".equals(method))
		{
			final String name = params.path("name").asText("");
			if (LayoutMcpTools.tool(name) == null)
			{
				return LayoutMcpEndpoint.error(id, -32602, "Unknown tool: " + name);
			}
			final ObjectNode data = pTools.invoke(name, params.get("arguments"));
			final ObjectNode result = LayoutStore.json().createObjectNode();
			final ObjectNode text = result.putArray("content").addObject();
			text.put("type", "text");
			text.put("text", data.has("markdown") ? data.get("markdown").asText() : data.toString());
			result.set("structuredContent", data);
			result.put("isError", data.has("error"));
			return LayoutMcpEndpoint.result(id, result);
		}
		if ("resources/list".equals(method) || "prompts/list".equals(method))
		{
			final ObjectNode result = LayoutStore.json().createObjectNode();
			result.putArray("resources/list".equals(method) ? "resources" : "prompts");
			return LayoutMcpEndpoint.result(id, result);
		}
		return LayoutMcpEndpoint.error(id, -32601, "Method not found: " + method);
	}

	static ObjectNode toolJson(final LayoutMcpTools.Tool pTool)
	{
		final ObjectNode t = LayoutStore.json().createObjectNode();
		t.put("name", pTool.name);
		t.put("title", pTool.title);
		t.put("description", pTool.description);
		final ObjectNode schema = t.putObject("inputSchema");
		schema.put("type", "object");
		final ObjectNode properties = schema.putObject("properties");
		final ArrayNode required = schema.putArray("required");
		for (final LayoutMcpTools.Param param : pTool.params)
		{
			final ObjectNode p = properties.putObject(param.name);
			if ("object".equals(param.type))
			{
				// an object, or the same as a JSON string (some clients stringify nested objects)
				p.putArray("type").add("object").add("string");
			}
			else
			{
				p.put("type", param.type);
			}
			p.put("description", param.description);
			if (param.required)
			{
				required.add(param.name);
			}
		}
		if (required.size() == 0)
		{
			schema.remove("required");
		}
		final ObjectNode annotations = t.putObject("annotations");
		annotations.put("readOnlyHint", pTool.readOnly);
		annotations.put("destructiveHint", false);
		annotations.put("idempotentHint", pTool.readOnly);
		annotations.put("openWorldHint", false);
		return t;
	}

	static ObjectNode result(final JsonNode pId, final JsonNode pResult)
	{
		final ObjectNode out = LayoutStore.json().createObjectNode();
		out.put("jsonrpc", "2.0");
		out.set("id", pId == null ? NullNode.getInstance() : pId);
		out.set("result", pResult);
		return out;
	}

	static ObjectNode error(final JsonNode pId, final int pCode, final String pMessage)
	{
		final ObjectNode out = LayoutStore.json().createObjectNode();
		out.put("jsonrpc", "2.0");
		out.set("id", pId == null ? NullNode.getInstance() : pId);
		final ObjectNode error = out.putObject("error");
		error.put("code", pCode);
		error.put("message", pMessage);
		return out;
	}
}
