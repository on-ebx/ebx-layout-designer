# Operating the Layout Designer

For whoever runs the server: what can be set, what is written where, what to read in the logs and what the codes mean.
Everything here was read in the code of 1.23.3 (`grep` of `System.getProperty`, `Integer.getInteger`, `Boolean.getBoolean`
and of the log calls). Installation is in [INSTALL.md](INSTALL.md).

## JVM properties (`-D...`, read by the server JVM)

| Property | Default | Read | Effect |
|---|---|---|---|
| `ebx.layout.geocoder.url` | `https://nominatim.openstreetmap.org/search` | at each lookup | Address of the geocoding service of the map blocks. `off` (any case) turns geocoding off: no call leaves the server, rows with no latitude/longitude fields are simply not placed. Set it to an internal Nominatim, or to `off` when the proxy blocks the public one (otherwise cards show "locating" until the 60 attempts of the page end). |
| `ebx.layout.geocoder.cache` | `<ebx.home>/layout-designer-geocache.tsv` | at the first lookup | File of the positions found. `off` keeps them in memory only (lost at restart). Empty and no `ebx.home`: memory only. |
| `ebx.layout.geocoder.cacheDays` | `90` | when the file is read and compacted | Age after which a position is forgotten. |
| `ebx.layoutDesigner.dataspace` | `LayoutDesigner` | at class load (restart) | Dataspace holding the layouts, explorers, favorites. |
| `ebx.layoutDesigner.dataset` | `LayoutDesigner` | at class load (restart) | Dataset of those tables. |
| `ebx.layoutDesigner.xsdActivation` | `false` | at each display and action | `true` lets the designer write the `recordForm` line into the XSD of a module on this server (*Activate* / *Deactivate*). `false`: no file of a deployed module is ever changed; the designer describes the line to add to the module's source (1.30.0). |
| `ebx.layoutDesigner.mcp` | `true` | at each call | `false` closes `/ebx-layout-designer/rest/mcp` (answers 403 / -32001). |
| `ebx.layoutDesigner.mcp.maxBodyBytes` | `2097152` | at each call | Largest MCP request body; beyond it HTTP 413 (-32600). |
| `ebx.layoutDesigner.mcp.maxDepth` | `64` | at each call | Deepest JSON nesting accepted by the MCP endpoint. |
| `ebx.layoutDesigner.countCap` | `1000` | at each display | Largest exact count of an association (then "1000+"). |
| `ebx.layoutDesigner.orgChart.maxIndexLinks` | `500000` | at each build | Most parent links one hierarchy chart index keeps (then a "large group" note and a `kernel.log` line). |
| `ebx.layoutDesigner.loadTest` | `false` | at each call | `true` enables the administrators' load test of the explorer (bench for a TEST instance, never in production). |

`ebx.home` and `catalina.base` / `catalina.home` are EBX's and Tomcat's own: the cache file default and the search for the
XSD to activate depend on them.

## Administrators' options of an explorer page (URL parameters, administrators only)

| Parameter | Effect |
|---|---|
| `explain=1` | The page answers with each statement sent to EBX and its time (what to read when an explorer is slow). |
| `explain=1&route=sql` | With `explain`: counts of values by one SQL `GROUP BY` instead of one XPath count per value, to compare. |
| `explain=1&parallel=0` | With `explain`: the statements of the page run one after the other, to compare with the usual parallel run. |
| load test (`ebx.layoutDesigner.loadTest=true`) | `n` threads (1-300) each ask the same page `rounds` times; answers the times and errors. |

## Logs (`ebxLog/kernel.log`)

Every line the module writes starts with `[layout-designer]`.

| Line | Level | Meaning |
|---|---|---|
| `LDF-RENDER` ... | ERROR | A tab or a whole layout could not be drawn: the tab is replaced by an error note, or the standard EBX form is shown. The exception and its stack follow. |
| `block <type> not drawn: ...`, `... not read: ...`, `... not counted: ...` | ERROR / WARN | One block failed; the others are drawn. |
| `... failed (LDD-xxxxxxx)` | ERROR | Unexpected failure in a designer page, the layouts home or personalization. The user saw the same code. |
| `explorer ... failed (LDX-xxxxxxx)` | ERROR | Unexpected failure in an explorer. Same code on screen. |
| `explorer SQL refused by EBX: ...` | ERROR | A statement of an explorer is not valid EBX SQL (the user sees only the code); the statement is in the line. |
| `explorer: ...` (no pop-up, no creation button, facet, link, no SQL count) | WARN | The explorer's configuration refers to something the model no longer has; the explorer works without that part. |
| `action ...` | WARN / ERROR | A button of an Actions block or the designer's launch of a workflow failed or was refused. |
| `geocoder: N positions read from <file>` | INFO | Cache file read at the first lookup. |
| `geocoder cache not read / not compacted / not written` | WARN | The cache file cannot be used (rights, disk). The memory cache still works. |
| `geocoder answered HTTP n`, `geocoder unreachable: ...` | WARN | The service does not answer (proxy, quota, network). See `ebx.layout.geocoder.url`. |
| `block ... places the rows of ... by their address ...` | WARN | A map block without latitude/longitude fields: each unknown address goes to the service, one a second. Add the two fields. |
| `hierarchy index of <table> built in N ms (...)` | INFO | A chart index took more than 500 ms to build. |
| `explorer load test: ...` | INFO | The load test ran. |
| `<what> <where> N ms` | DEBUG | Form timing lines (`form built`, `tab n written`, `block <type> of tab n`) for any step over 200 ms; only when the `kernel` category is at DEBUG. Read them to find the block that makes a form slow. |
| `explorer query on ...` | DEBUG | One explorer page with its time. |

## Codes

| Code | Where | Meaning, what to do |
|---|---|---|
| `LD-DENIED` | any designer page | The user is not an administrator: ask for the Administrator role. |
| `LD-INVALID`, `LD-INVALID-JSON`, `LD-TOO-LARGE`, `LD-TOO-DEEP`, `LD-EMPTY` | save of a layout (designer, MCP `layout_put`) | The layout is refused before saving; the message lists the first errors. |
| `LD-WRITE-OFF` | activation | The server does not let the designer change module files (`ebx.layoutDesigner.xsdActivation` is not `true`). Nothing was changed: add the line to the module's source. |
| `LD-NOFILE`, `LD-NOTABLE` | activation | The model's XSD is not on this server / the table is not declared in it. Nothing was changed. |
| `LD-BUSY` | activation | Another activation of the same XSD is running: retry. |
| `LD-MALFORMED`, `LD-XSD-INVALID` | activation | The change would make the XSD invalid XML / the XSD was invalid before. The file is untouched, the copy is kept. |
| `LDD-...` | designer, layouts home, personalization | Unexpected failure; find the same code in `kernel.log` for the stack. |
| `LDX-...` | explorer | Same, for explorers. |
| `LDG-429` | geocoding address (HTTP 429) | The queue of 500 addresses is full: retry in a few minutes. |
| MCP -32700 / -32600 / -32601 / -32602 / -32001 | `/rest/mcp` | Parse error, invalid request or body too large, unknown method, unknown tool, module's MCP closed or caller not an administrator. |

## Files the module writes

| File | Where | Content / care |
|---|---|---|
| `layout-designer-geocache.tsv` | `<ebx.home>/` or `ebx.layout.geocoder.cache` | Positions found: SHA-256 of the normalised address, latitude, longitude, date (no address text). Created readable by its owner only, compacted every 5 000 additions, entries older than `cacheDays` dropped. Safe to delete (positions are looked up again, one a second). An old-format file (address in clear) is rewritten by hash at the first start. |
| `<xsd>.bak-<time>-layout-designer` | next to a module XSD | Only where `ebx.layoutDesigner.xsdActivation` is `true` (as the two rows below). Copy made before each activation / deactivation. Keep it until the restart has been checked; delete freely afterwards. |
| `<xsd>.layout-designer.lock` | next to a module XSD | Lock of the activation, zero bytes, left in place. Harmless; do not delete while an activation runs. |
| the XSD itself | module folder | Gets or loses the line `<recordForm osd:class="com.on.ebx.layout.runtime.ConfigurableForm"/>`; written by a temporary file moved over it. EBX reads it at the next start. |
| Data (not files) | dataset `LayoutDesigner` | Tables of layouts, explorers, favorites, personal arrangements. Back them up with the repository. |

## Tasks, threads and caches

| Name | What | Bounds |
|---|---|---|
| `layout-designer-geocoder` | ONE daemon thread that calls the geocoding service, one request every 1.1 s. | Queue of 500 addresses (beyond: LDG-429); a miss is remembered 10 minutes. |
| `layout-designer-explorer-N` | Daemon work-stealing pool of an explorer's statements (counts, page, facets). Stopped when the module is unloaded. | 2 to 8 threads (CPU count); the ceiling for the whole server. |
| `layout-designer-load-test` | Daemon threads of the load test. | Only with `ebx.layoutDesigner.loadTest=true`; 1-300. |
| Explorer answers | Cached about a minute, retired by the table's modification date. | Dropped when the module is unloaded. |
| Layout rows | Found by key, kept until the FormLayout table's modification date changes (a miss too). | A save shows at the next opening. |
| Geocoder memory cache | 5 000 entries, least recently used leaves. | |
| Hierarchy indexes | Key to parent of a table, rebuilt when the table changes, by one thread. | 8 indexes, `maxIndexLinks` links each. |

The module starts no scheduled task and no workflow of its own.

## Upgrading the shared runtime jar

`ebx-layout-designer-runtime.jar` sits in Tomcat's `common.loader`, so every module that declares `ConfigurableForm` uses it
and a running server keeps the old classes. Stop the server, replace the jar and the war together (the war's page scripts and
the jar's block names go together), delete `webapps/ebx-layout-designer/` if it was exploded, start. Check `kernel.log` for
`[layout-designer]` lines at the start and open one form and one explorer. Never reload the module's context alone after a jar
change.

## Texts and languages

The words the form writes itself are in `RuntimeText.properties` (English), `_fr`, `_it` inside the runtime jar; the module's
pages use `Messages*.properties`; the explorer and layouts home scripts carry French, English and Italian. Any other language
(es-ES, de-DE...) reads English until its file is added to the jar (a new `RuntimeText_<lang>.properties` and a column in
the two scripts).
