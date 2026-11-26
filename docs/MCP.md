# MCP server (1.21.0)

The module serves an MCP server so that an agent (Claude Code, the EBX assistant, any MCP client) can design record
forms and explorers the way an administrator does in the designer pages, with the same storage and the same checks.

- Endpoint: `POST /ebx-layout-designer/rest/mcp` — MCP over streamable HTTP, JSON answers (JSON-RPC 2.0), no server-sent
  events; `GET` and `DELETE` answer `405` with `Allow: POST,OPTIONS`, which MCP clients need to fall back to plain
  request/response. A custom module cannot register a native EBX MCP server (`/ebx-assistant/mcp/v1/…`, core and add-on
  modules only), hence this endpoint on the EBX REST Toolkit.
- Authentication: any EBX REST authentication (Basic, token, session). **Administrators only**, like the designer pages:
  anyone else gets an empty tool list. A request whose `Origin` names another site is refused (a browser signed in to EBX
  cannot be made to post a layout).
- Turned off with the JVM option `-Debx.layoutDesigner.mcp=false` (the endpoint then answers 404).

Client configuration (Claude Code, `~/.claude.json` or `.mcp.json`):

```json
"ebx-layout-designer": {
  "type": "http",
  "url": "http://localhost:8080/ebx-layout-designer/rest/mcp",
  "headers": {"Authorization": "Basic <base64 of login:password>"}
}
```

## Tools

| Tool | Writes | What it does |
|---|---|---|
| `layout_help` | | the workflow; `topic: forms` / `explorers` returns the full formats ([LAYOUT-FORMAT.md](LAYOUT-FORMAT.md), [EXPLORER-FORMAT.md](EXPLORER-FORMAT.md)) |
| `layout_describe_table` | | fields (path, label, type, group, enumeration values, foreign key target), groups, associations with their table and fields, the dataset's tables, locales, registered components, published workflows, the stored form and the table's explorers, whether the model declares the designed form; Match and Merge's technical nodes flagged |
| `layout_get` | | the stored record form layout, version, who saved it |
| `layout_starter` | | the designer's starting layout (one panel per group, one tab per association), technical associations left out |
| `layout_validate` | | shape, block types and ids, then every field, association, table, workflow and component named (`LayoutCheck`, the same check as the `FormLayout` table's validation) |
| `layout_put` | yes | checks, then saves (version + 1); refused on an error or when `expected_version` differs from the stored one; `dry_run` |
| `explorer_list`, `explorer_get` | | the explorers (all, or a table's), one explorer's JSON |
| `explorer_validate` | | every path, computed value, facet, flag, tile, sort, group, card part and action, and whether EBX can evaluate each filter (`ExplorerCheck`) |
| `explorer_put` | yes | checks, then creates or replaces an explorer; `label`, `active`, `expected_version`, `dry_run`; answers its id and address |

Every answer is a JSON object (`structuredContent`); a refused call carries `error` and `code` (`FORBIDDEN`, `NOT_FOUND`,
`MISSING_ARGUMENT`, `INVALID_JSON`, `INVALID`, `VERSION_CONFLICT`, `WRONG_TABLE`) and `isError: true`.

## What the checks add to the pages

The pages never refuse a stored layout or explorer: they skip what they cannot draw, with at most a line in `kernel.log`.
The MCP checks say it before saving:

- a path the table (or the linked table) does not define;
- an explorer filter EBX cannot evaluate as one query (a media sub-field such as `/Logo/attachment`, a `provided` value);
  the page would drop the flag and its tile;
- a facet, sort or group on something EBX cannot filter or sort (a group, an association, a `provided` value);
- tiles naming a flag or facet that does not exist, duplicate ids, unknown kinds, widgets, tones and block types;
- a workflow button whose publication is not published on this instance (warning).

The record form is used once the model declares `com.on.ebx.layout.runtime.ConfigurableForm`: `activation` in the
answers says whether it does, and how (DMA: `set_form_layout` + `publish_data_model` on the `data-model` server; module
model: the designer's *Activation*, then one restart).

Worked example: [examples/companies-explorer.json](examples/companies-explorer.json), the FastTrack Companies explorer
(632 companies, six tiles, eleven facets, groups by sector) rebuilt from the Java `CompanyExplorer` service.
