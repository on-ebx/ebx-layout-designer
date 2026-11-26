# Layout Designer for ON EBX

> This repository is published from ON EBX's development repository. A change made here is overwritten by the next
> publication: report issues and propose changes to ON EBX.

Lay out the record form of any EBX table by drag and drop: tabs, one- or two-column rows, panels of native field
rows, association grids, a readable validation report, an identity band, key figures, a map, the group structure, the
Match and Merge state. Every text you type is stored in each locale of the instance. A saved layout shows at the next
opening of a record: no restart, no compilation.

## How it works

| Piece | Where | What it does |
|---|---|---|
| `ebx-layout-designer-runtime.jar` | `<server>/lib` (shared by every module) | `ConfigurableForm`, a `UIForm` that reads the table's layout at each display and draws it with EBX's own form rows and widgets |
| `ebx-layout-designer` module | its own web application | the dataset `LayoutDesigner/LayoutDesigner` (table `FormLayout`, one row per model and table), and the service **Design the record form** on every table, for administrators |

A data model uses a designed form once its table declares the runtime class:

```xml
<osd:table>
    ...
    <recordForm osd:class="com.on.ebx.layout.runtime.ConfigurableForm"/>
</osd:table>
```

The designer writes that line itself for a model packaged in a module (*Activation* in the top bar, with a copy of
the XSD next to it and the previous class kept to put it back). EBX reads it at the next start: **one restart, once**.
For a DMA model, declare the class in the model screen and republish. Without an active layout the standard EBX form
is shown.

The model is read through the EBX Java API only (`SchemaNode`, `Repository.getLocales()`, `getValidationReport()`):
no MCP server is needed, so the module is not tied to 6.3.

## The designer

Open a table, then **Actions › Design the record form** (or the web component
`/ebx/?branch=<dataspace>&instance=<dataset>&xpath=/root/<Table>&service=ebx-layout-designer@LayoutDesigner`).

- palette: the table's fields (by group), its associations, the blocks, the registered components;
- the form itself, with a record of the table as preview, in any locale of the instance;
- an inspector for the selected tab, row, block or field; every text has one input per locale;
- *Translations*: every text of the form, with what is missing per locale;
- what is saved (JSON), the declaration in the model, and an optional frozen Java export.

## Blocks

| Block | Settings |
|---|---|
| Panel | title, note, fields (widget, label override, read only, full width), whole panel read only |
| Identity band | title field, subtitle fields, chips, gauge (0–100 field), status field |
| Key figures | fields, plus a row count of an association |
| Validation report | the record's validation report: severities, grouping (severity, tab, none), number shown, "Go to field" links that open the right tab, action buttons tied to a field, a group or an association |
| Association | EBX's native grid (toolbar, filter, paging) or cards with chosen fields |
| Map | latitude and longitude fields; OpenStreetMap in a fixed-height box |
| Group structure | parent foreign key and the association of children |
| Duplicates | Match and Merge state and group (read by reflection: works without the add-on) |
| Help text | a localized paragraph |
| Opening hours | open now, the week, the next exceptional days (associations and fields chosen in the inspector) |
| Timeline, change history | an association's rows on a timeline; who changed what and when (historized table) |
| Completeness, checklist | share of filled fields with weights; points ticked by a condition |
| Lifecycle steps, validity period | the steps of a status field; upcoming / in force / ending / expired |
| Values by source, workflows in progress, translations, inheritance | golden record vs its sources; tasks on the record; language completeness; what a child dataset changes |
| Value over time, sites on a map | a value's curve from the history; an association's rows on a map |
| Hierarchy chart | the record's group as an org chart, a tree or rings |
| Changes in this request, used by | what a workflow request changes (old → new); who refers to this record |
| Record information | created by / on, last modified by / on, kept by EBX for every record |
| Breakdown chart, association totals, matrix | an association's rows grouped (bars or ring), summed or crossed on two fields |
| Linked record, contacts, hierarchy | fields of a foreign key's target; people as cards; breadcrumb of a parent key |
| Image gallery, documents | media (Digital Asset Manager public API) or URL fields; files with type and date |
| Links and actions, formatted address | buttons to addresses built from the record; an address laid out by country |
| Registered component | a Java `LayoutComponent` registered by name with `LayoutComponents.register` |

Every block can be shown only when an XPath predicate is true on the record, or live while another field has some
values, and hidden when a record is created. Fields take input or display widgets (amount, unit, stars, relative date,
country, barcode, masked value…) and live behaviours (show if, mandatory if, read only if, fields driven by a
configuration table). Rows can be collapsible sections. The palette is searchable and grouped by category.

## Explorers (1.16.0)

*Actions › Design the explorer* draws a search page over the table: search, facets (values, flags, ranges, tree,
favorites), tiles, sorts and cards whose every attribute takes a widget, plus an image from DAMA and actions. The service
*Explorer* shows it; the server counts, filters and pages over an index per dataset, and each user only sees what EBX
lets them read. Format: [docs/EXPLORER-FORMAT.md](docs/EXPLORER-FORMAT.md); a complete example:
[docs/examples/tiers-explorer.json](docs/examples/tiers-explorer.json).

## MCP server (1.21.0)

`POST /ebx-layout-designer/rest/mcp` lets an agent describe a table, start, check and save its record form layout and its
explorers, with the designer's own storage and checks (administrators only). Tools and configuration: [docs/MCP.md](docs/MCP.md).
Since 1.27.0 (prototype), a host supporting MCP Apps draws a preview of the form next to the layout tools' answers
([docs/MCP.md](docs/MCP.md#form-preview-mcp-apps-1270-prototype)).

## Documentation

| Document | For |
|---|---|
| [docs/INSTALL.md](docs/INSTALL.md) | installing, declaring a table's form, upgrading, uninstalling |
| [docs/USER-GUIDE.md](docs/USER-GUIDE.md), [docs/USER-GUIDE.fr.md](docs/USER-GUIDE.fr.md) | administrators who design forms; limitations |
| [docs/LAYOUT-FORMAT.md](docs/LAYOUT-FORMAT.md) | the JSON a layout is saved as |
| [docs/EXPLORER-FORMAT.md](docs/EXPLORER-FORMAT.md) | the JSON an explorer is saved as |
| [docs/MCP.md](docs/MCP.md) | the MCP server: tools, checks, client configuration |
| [RELEASE-NOTES.md](RELEASE-NOTES.md) | what each version brings |

The same documents are served by the module as HTML (`/ebx-layout-designer/www/help/`) and linked from its **About**
page (service `ebx-layout-designer@About` on the `FormLayout` table: version, commit, build date, ON EBX version).
After editing a Markdown document, run `python3 tools/build_help.py` (the release script checks the pages are current).

## Build and release

```bash
EBX_LIB_DIR=<server>/lib ./build.sh            # compile: dist/ebx-layout-designer-runtime.jar + target/classes
EBX_LIB_DIR=<server>/lib tools/package.sh      # release: dist/ebx-layout-designer-<version>.zip (+ .sha256)
```

The version is in `VERSION`; a release is tagged `v<version>`. The zip holds the war, the runtime jar, Jackson for a
server without it, the documentation and `SHA256SUMS`. Java 11 bytecode (`JAVA_RELEASE` to change it); the war uses
the Jakarta servlet API.

Runtime dependencies: the EBX jars and Jackson 2.x (`jackson-annotations`, `jackson-core`, `jackson-databind`) on the
server classpath. A FastTrack server has Jackson in its `lib`; a plain EBX distribution does not, hence the copy in the
release. No FastTrack or toolbox class is used.

## Status (1.0.0, 2026-10-07)

Seen running on FT 6.3 RC (EBX 6.3.0-rc2):

- **Companies** (module model, 73 fields, 11 associations): designed, declared in `Companies.xsd` by the designer,
  drawn by `ConfigurableForm` after one restart: tabs with counts, band, key figures, native widgets and grids, map,
  Match and Merge state; validation messages located and "Go to field" opening another tab.
- **B2C customers** (DMA model `B2CCustomers`: Customer, Order, Interaction; declared with `set_form_layout`): layout
  composed by drag and drop, three tabs with counts ("Orders (5)"), native order grid, interaction cards, an error, a
  warning and an information shown in the validation block, the information located on *Points* with "Go to field".
- The *Record form layouts* page in the MultiDomain perspective (*Configuration*): EBX's dataspace and dataset
  selectors, table list following the dataset, the designer opened inside the perspective.

Limitations: [docs/USER-GUIDE.md](docs/USER-GUIDE.md#9-limitations-100).
