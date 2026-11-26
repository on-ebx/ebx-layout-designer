# Layout API — a layout service for programs and agents

Lot opened 2026-10-12 for the MDM Factory's domain accelerators (`ebx-mdm-factory/docs/ACCELERATORS.md`, section 12).
**Status (2026-10-15): the JSON-RPC endpoint is built in 1.21.0, for record forms and explorers — see [MCP.md](MCP.md).**
The in-JVM contract for the Factory (MBean) and `layout_declare` are not built yet; the rest of this page is the
original design.

## Why

A layout is one row of `LayoutDesigner/FormLayout` and a declaration of `ConfigurableForm` on the table. Today only the
designer page writes the row, and only the page checks a layout against the model. Two callers need the same without the page:

- the **MDM Factory**, which generates a domain and its record forms, and lets a language model propose the layout — a proposal
  that must be checked against the model before a person sees it, and written without touching this module's table directly;
- **agents** (Claude Code, the EBX assistant, any MCP client), to propose or retouch a form of an existing table.

## Shape

One class, `com.on.ebx.layout.designer.api.LayoutService`, with no UI dependency, exposed two ways:

| Surface | For | How |
|---|---|---|
| in-JVM contract | sibling modules (the Factory) | a Standard MBean `com.on.ebx.layout:type=Contract`, operations `handle(Object session, String jsonRpc)`, the caller's `Session` passed as `Object` — the pattern of `ebx-connectors`' `Contract` (`ebx-connectors/docs/CONTRACT.md`): versioned (`layout-designer.sibling` 1.0.0), `contract.describe` first |
| JSON-RPC endpoint | agents | `POST /ebx-layout-designer/rest/mcp` (Streamable HTTP, JSON answers; `GET`/`DELETE` answer `405` + `Allow: POST,OPTIONS`, as the MCP Streamable HTTP transport requires for a client to fall back to plain request/response), EBX authentication, the same operations as `tools/list` / `tools/call` |

A custom module cannot register a real EBX MCP server (`ModuleName.isCoreOrAddonModule`), hence the hand-rolled endpoint, copied
from `ebx-perspective-admin`'s `McpEndpoint`.

## Operations

Common arguments: `dataspace`, `dataset`, `table` (the table is resolved through a dataset, as the designer does: the model's
`SchemaLocation` and the instance locales come from it).

| Operation | Arguments | Answer |
|---|---|---|
| `layout_schema` | — | the format version, the block catalogue (type, keys, which take paths / texts / associations), the widgets, the behaviours, the registered components — what the palette knows, generated from the same tables the page uses |
| `layout_starting` | table | the starting layout `DefaultLayout` builds from the model, **without the add-ons' technical associations** (`Association_Reverse`, `Linked_Field_*`), ready to send to a model as the point of departure |
| `layout_validate` | table, `layout` (JSON) | `{valid, messages[{severity, path, text}]}`: every `p`, `a`, `title`, `sub`, `chips`… resolved against the model; unknown block types and keys; missing locales (filled from the default locale, reported as warnings); duplicate `id`s; never writes |
| `layout_get` | table | the `FormLayout` row: `layout`, `active`, `version`, `label`, `previousForm`, `updatedBy`, `updatedOn`; `found: false` when none |
| `layout_put` | table, `layout`, `label?`, `active?` (default true) | validates first (refused on an error, warnings tolerated), then writes or updates the row, `version` incremented, `updatedBy` = the session's user; answers the stored row |
| `layout_declare` | table, `mode` = `dma` \| `module` | DMA: calls `set_form_layout` + `publish_data_model`? **No** — the Designer does not drive the data-model server; it answers what to declare (`class`, `osd:table/recordForm`) and, for a module model, performs the XSD edit `XsdActivator` already does. The DMA declaration stays the caller's (`data-model` server), as measured on 2026-10-12 |

`layout_put` on a table whose model does not declare `ConfigurableForm` is accepted (the row waits for the declaration) and the
answer says `declared: false`.

## What is reused

- `DefaultLayout` (starting layout), `ModelExport` (fields, groups, associations with labels per locale), `LayoutWriter` /
  `LayoutStore` (the row), `XsdActivator` (module XSD) — the service wraps them; the page is re-pointed to the service afterwards so
  both keep one behaviour.
- The validation of paths exists in the page's JavaScript only (palette, "placed" markers): it is rewritten once in Java
  (`LayoutPaths` resolves a path against a `SchemaNode`), and the page may call `layout_validate` too.

## Not in this lot

- Driving the `data-model` server (declaration on a DMA model) from the Designer.
- A `layout_render` (HTML preview) — the Factory shows the proposal as the decision rows, the real form is opened in EBX.
- Permissions beyond EBX's: the service runs under the caller's session; writing the row needs write access on
  `LayoutDesigner/FormLayout`, as the page requires today.

## Order of work

1. `LayoutService` + `layout_schema`, `layout_starting`, `layout_validate` with unit tests on a scratch model (the probe `AccelProbe`
   of the accelerators' measurements, publication `urn:ebx:publication:AccelProbe`, `/root/Supplier`).
2. `layout_get` / `layout_put` / `layout_declare` (module mode).
3. The MBean contract, `contract.describe`, `docs/CONTRACT.md`.
4. The JSON-RPC endpoint, tested with a raw `curl` AND a real MCP client handshake (the `405` rule).
5. The page re-pointed to the service.
