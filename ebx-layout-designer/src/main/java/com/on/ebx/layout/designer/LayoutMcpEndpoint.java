package com.on.ebx.layout.designer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import jakarta.ws.rs.core.UriInfo;

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
 * <p>
 * MCP Apps (1.27.0, extension {@code io.modelcontextprotocol/ui}, spec 2026-01-26): the server lists one UI resource,
 * {@value #PREVIEW_UI_URI} (an HTML page embedded in the module, {@code mcp/form-preview.html}), and the read-only tools
 * {@code layout_get}, {@code layout_starter} and {@code layout_validate} point at it through {@code _meta.ui.resourceUri}. A host
 * that supports the extension (the Claude desktop chat) draws the record form the layout describes, as a mock-up, next to
 * the answer; the page reads the table's labels with {@code layout_describe_table} through the host, under the caller's
 * session. Nothing is written from the page. A host without the extension reads the text as before.
 */
@Path("/mcp")
@Produces(MediaType.APPLICATION_JSON)
public final class LayoutMcpEndpoint
{
	public static final String PROTOCOL_VERSION = "2025-06-18";
	/** The MCP Apps extension (SEP-1865), its identifier and the only content type of its first version. */
	public static final String UI_EXTENSION = "io.modelcontextprotocol/ui";
	public static final String UI_MIME_TYPE = "text/html;profile=mcp-app";
	/** The form preview: the UI resource of the read-only layout tools. */
	public static final String PREVIEW_UI_URI = "ui://ebx-layout-designer/form-preview";
	static final String PREVIEW_UI_FILE = "/com/on/ebx/layout/designer/mcp/form-preview.html";
	/** The tools the preview draws: they answer, or are given, a layout of one table. */
	static final java.util.Set<String> PREVIEW_TOOLS = new java.util.HashSet<String>(java.util.Arrays.asList("layout_get", "layout_starter", "layout_validate"));
	private static volatile String previewHtml;
	private final SessionContext sessionContext;
	@Context
	UriInfo uriInfo;

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
	public Response handle(final InputStream pBody, @HeaderParam("Origin") final String pOrigin, @HeaderParam("Host") final String pHost,
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
		// the administrator test comes BEFORE the body is read or parsed (audit L2-04)
		if (!LayoutMcpTools.allowed(session))
		{
			return Response.status(403).entity(LayoutMcpEndpoint.error(null, -32001, "The Layout Designer is for administrators.").toString()).build();
		}
		final JsonNode request;
		try
		{
			request = LayoutMcpEndpoint.parseBody(pBody, LayoutMcpEndpoint.maxBodyBytes(), LayoutMcpEndpoint.maxDepth());
		}
		catch (final BodyRefused ex)
		{
			return Response.status(ex.status).entity(LayoutMcpEndpoint.error(null, ex.code, ex.getMessage()).toString()).build();
		}
		if (request == null || request.isMissingNode())
		{
			return Response.status(400).entity(LayoutMcpEndpoint.error(null, -32600, "Invalid request").toString()).build();
		}
		final Locale locale = this.sessionContext.getLocale() == null ? Locale.ENGLISH : this.sessionContext.getLocale();
		final LayoutMcpTools tools = new LayoutMcpTools(session, this.sessionContext.getRepository(), locale);
		tools.setServerBase(LayoutMcpEndpoint.serverBase(this.uriInfo));
		final boolean allowed = LayoutMcpTools.allowed(session);
		if (request.isArray() && request.size() > LayoutMcpEndpoint.MAX_BATCH)
		{
			return Response.status(400).entity(LayoutMcpEndpoint.error(null, -32600,
				"Batch too large: at most " + LayoutMcpEndpoint.MAX_BATCH + " requests per call. Send them in several calls.").toString()).build();
		}
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

	// ----- bounds on what a caller may send (audit L2-04) -----------------------------------------------------------------
	// Jackson 2.14 (the version of the server's lib) has no StreamReadConstraints (2.15+): the size is bounded while reading
	// and the nesting by a scan of the text before the parser sees it. The number of nodes follows from the size.

	static final String PROP_MAX_BODY = "ebx.layoutDesigner.mcp.maxBodyBytes";
	static final String PROP_MAX_DEPTH = "ebx.layoutDesigner.mcp.maxDepth";
	static final int DEFAULT_MAX_BODY = 2 * 1024 * 1024;
	static final int DEFAULT_MAX_DEPTH = 64;
	static final int MAX_BATCH = 50;

	static int maxBodyBytes()
	{
		return LayoutMcpEndpoint.intProperty(LayoutMcpEndpoint.PROP_MAX_BODY, LayoutMcpEndpoint.DEFAULT_MAX_BODY);
	}

	static int maxDepth()
	{
		return LayoutMcpEndpoint.intProperty(LayoutMcpEndpoint.PROP_MAX_DEPTH, LayoutMcpEndpoint.DEFAULT_MAX_DEPTH);
	}

	private static int intProperty(final String pName, final int pDefault)
	{
		try
		{
			final int v = Integer.parseInt(System.getProperty(pName, "").trim());
			return v > 0 ? v : pDefault;
		}
		catch (final NumberFormatException ex)
		{
			return pDefault;
		}
	}

	/** A body refused before or while parsing: HTTP status, JSON-RPC code and a message that says what to do. */
	static final class BodyRefused extends Exception
	{
		private static final long serialVersionUID = 1L;
		final int status;
		final int code;

		BodyRefused(final int pStatus, final int pCode, final String pMessage)
		{
			super(pMessage);
			this.status = pStatus;
			this.code = pCode;
		}
	}

	/** Reads at most pMaxBytes (413 beyond), refuses a nesting deeper than pMaxDepth (400), then parses. */
	static JsonNode parseBody(final InputStream pBody, final int pMaxBytes, final int pMaxDepth) throws BodyRefused
	{
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		if (pBody != null)
		{
			final byte[] chunk = new byte[8192];
			try
			{
				for (int n = pBody.read(chunk); n >= 0; n = pBody.read(chunk))
				{
					if (buffer.size() + n > pMaxBytes)
					{
						throw new BodyRefused(413, -32600, "Request body larger than " + pMaxBytes + " bytes: send less, or ask an administrator to raise -D"
							+ LayoutMcpEndpoint.PROP_MAX_BODY + " on the server.");
					}
					buffer.write(chunk, 0, n);
				}
			}
			catch (final IOException ex)
			{
				throw new BodyRefused(400, -32700, "The request body could not be read: send it again.");
			}
		}
		final String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
		LayoutMcpEndpoint.checkDepth(text, pMaxDepth);
		try
		{
			return LayoutStore.json().readTree(text);
		}
		catch (final Exception ex)
		{
			throw new BodyRefused(400, -32700, "Parse error: the body is not valid JSON.");
		}
	}

	/** Refuses JSON text nested deeper than pMaxDepth (brackets outside strings), without parsing it. */
	static void checkDepth(final String pText, final int pMaxDepth) throws BodyRefused
	{
		int depth = 0;
		boolean inString = false;
		for (int i = 0; i < pText.length(); i++)
		{
			final char c = pText.charAt(i);
			if (inString)
			{
				if (c == '\\')
				{
					i++;
				}
				else if (c == '"')
				{
					inString = false;
				}
			}
			else if (c == '"')
			{
				inString = true;
			}
			else if (c == '{' || c == '[')
			{
				if (++depth > pMaxDepth)
				{
					throw new BodyRefused(400, -32600, "JSON nested deeper than " + pMaxDepth + " levels: flatten it, or ask an administrator to raise -D"
						+ LayoutMcpEndpoint.PROP_MAX_DEPTH + " on the server.");
				}
			}
			else if (c == '}' || c == ']')
			{
				depth--;
			}
		}
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
			final ObjectNode capabilities = result.putObject("capabilities");
			capabilities.putObject("tools").put("listChanged", false);
			// MCP Apps: the preview is a resource; the extension is declared back to the host (SEP-1724 extension capabilities)
			final ObjectNode resources = capabilities.putObject("resources");
			resources.put("listChanged", false);
			resources.put("subscribe", false);
			capabilities.putObject("extensions").putObject(LayoutMcpEndpoint.UI_EXTENSION).putArray("mimeTypes").add(LayoutMcpEndpoint.UI_MIME_TYPE);
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
		if ("resources/list".equals(method))
		{
			final ObjectNode result = LayoutStore.json().createObjectNode();
			final ArrayNode resources = result.putArray("resources");
			// listed to whoever may call the tools it draws (administrators); the HTML itself holds no data
			if (pAllowed && !LayoutMcpEndpoint.previewHtml().isEmpty())
			{
				resources.add(LayoutMcpEndpoint.previewResource());
			}
			return LayoutMcpEndpoint.result(id, result);
		}
		if ("resources/read".equals(method))
		{
			final String uri = params.path("uri").asText("");
			final String html = LayoutMcpEndpoint.previewHtml();
			if (!pAllowed || !LayoutMcpEndpoint.PREVIEW_UI_URI.equals(uri) || html.isEmpty())
			{
				final ObjectNode error = LayoutMcpEndpoint.error(id, -32002, "Resource not found: " + uri);
				((ObjectNode) error.get("error")).putObject("data").put("uri", uri);
				return error;
			}
			final ObjectNode result = LayoutStore.json().createObjectNode();
			final ObjectNode content = result.putArray("contents").addObject();
			content.put("uri", LayoutMcpEndpoint.PREVIEW_UI_URI);
			content.put("mimeType", LayoutMcpEndpoint.UI_MIME_TYPE);
			content.put("text", html);
			content.putObject("_meta").putObject("ui").put("prefersBorder", true);
			return LayoutMcpEndpoint.result(id, result);
		}
		if ("prompts/list".equals(method) || "resources/templates/list".equals(method))
		{
			final ObjectNode result = LayoutStore.json().createObjectNode();
			result.putArray("prompts/list".equals(method) ? "prompts" : "resourceTemplates");
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
		// MCP Apps: the layout is drawn by the preview; the flat key is the pre-GA spelling some hosts still read
		if (LayoutMcpEndpoint.PREVIEW_TOOLS.contains(pTool.name) && !LayoutMcpEndpoint.previewHtml().isEmpty())
		{
			final ObjectNode meta = t.putObject("_meta");
			final ObjectNode ui = meta.putObject("ui");
			ui.put("resourceUri", LayoutMcpEndpoint.PREVIEW_UI_URI);
			ui.putArray("visibility").add("model").add("app");
			meta.put("ui/resourceUri", LayoutMcpEndpoint.PREVIEW_UI_URI);
		}
		return t;
	}

	/** {@code http://host:8080}, as the caller reached the server (behind a proxy, the proxy's address): the base of the links the tools answer. */
	static String serverBase(final UriInfo pUriInfo)
	{
		if (pUriInfo == null) return "";
		try
		{
			final java.net.URI base = pUriInfo.getBaseUri();
			if (base == null || base.getScheme() == null || base.getRawAuthority() == null) return "";
			return base.getScheme() + "://" + base.getRawAuthority();
		}
		catch (final RuntimeException ex)
		{
			return "";
		}
	}

	/** The HTML of the form preview, read once from the module's classes; empty when the file is missing. */
	static String previewHtml()
	{
		String html = LayoutMcpEndpoint.previewHtml;
		if (html == null)
		{
			html = "";
			try (InputStream in = LayoutMcpEndpoint.class.getResourceAsStream(LayoutMcpEndpoint.PREVIEW_UI_FILE))
			{
				if (in != null)
				{
					final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
					final byte[] buffer = new byte[8192];
					for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) bytes.write(buffer, 0, n);
					html = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
				}
			}
			catch (final java.io.IOException ex)
			{
				html = "";
			}
			LayoutMcpEndpoint.previewHtml = html;
		}
		return html;
	}

	/** The UI resource as listed: uri, name, description, mime type and its rendering preference (no external domain: the default CSP). */
	static ObjectNode previewResource()
	{
		final ObjectNode r = LayoutStore.json().createObjectNode();
		r.put("uri", LayoutMcpEndpoint.PREVIEW_UI_URI);
		r.put("name", "form_preview");
		r.put("title", "Record form preview");
		r.put("description", "Mock-up of the record form a layout describes (tabs, rows, panels with their fields, blocks), drawn from the answer "
			+ "of layout_get, layout_starter or layout_validate with the table's labels (layout_describe_table); check findings shown on the "
			+ "blocks they concern; a link opens the table in the designer.");
		r.put("mimeType", LayoutMcpEndpoint.UI_MIME_TYPE);
		r.putObject("_meta").putObject("ui").put("prefersBorder", true);
		return r;
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
