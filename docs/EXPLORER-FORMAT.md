# Explorer format (1.16.0)

An **explorer** is a search page over one table: a search box, facets, tiles, a sort, and one card per record. It is
drawn in the Layout Designer (*Design the explorer*) and stored as JSON in the table `ExplorerLayout` of the dataset
`LayoutDesigner/LayoutDesigner`, one row per explorer (several explorers may target the same table). The module service
**Explorer** (`ebx-layout-designer@Explorer`, on the table view of any table) reads the row at each opening: a saved change
shows at the next opening, no restart.

Paths are written like the form layouts: relative to the record, with a leading slash (`/raisonSociale`,
`/commercial/familleAchat`). Every text is an object with one entry per locale (`{"fr-FR": "…", "en-US": "…"}`).

## Row

| Field | Content |
|---|---|
| `id` | auto-increment; the web component parameter `ldxExplorer` |
| `label` | name shown in the designer and as the page title when the JSON has no `title` |
| `model`, `table` | schema location (`urn:ebx:…`) and table path, as for `FormLayout` |
| `active` | false: the service answers "This explorer is turned off" |
| `layout` | the JSON below |
| `version`, `updatedBy`, `updatedOn` | written at each save |

Opening an explorer: `/ebx/?branch=<dataspace>&instance=<dataset>&xpath=/root/<Table>&service=ebx-layout-designer@Explorer&ldxExplorer=<id>`
(add `&perspectiveName=<key>&scope=full` to keep a perspective's menu). Without `ldxExplorer`, the first active explorer
of the table is opened.

## JSON

```json
{
  "format": "ebx-layout-designer/explorer", "version": 1,
  "title": {"fr-FR": "Fournisseurs", "en-US": "Suppliers"},
  "openTo": [],
  "derived": {"etab": {"type": "count", "association": "/etablissements"}},
  "variants": [{"id": "std", "name": {"fr-FR": "Standard"}, "roles": [], "layout": {
    "search": {"fields": ["/raisonSociale", "/siren"]},
    "facets": [], "tiles": [], "sorts": [], "pageSize": 48, "card": {}
  }}]
}
```

`openTo`: role names allowed to open the explorer; empty = every user who may read the table. `variants`: the first one
(after the first) whose `roles` the user holds is used, else the first. Any facet, tile, card part or action may carry
`roles` (shown only to these roles). Roles only arrange the page: EBX permissions decide what a user reads (below).

### Conditions

Used by flags, tiles, `case` values and actions' `cond`:

| Form | True when |
|---|---|
| `{"field": "/p", "in": ["A","B"]}`, `"notIn"` | the stored value (enumeration code, foreign key, `true`/`false`) is / is not in the list |
| `{"field": "/p", "filled": true}`, `{"field": "/p", "empty": true}` | the value is / is not empty |
| `{"field": "/p", "gt": 70}`, `lt`, `ge`, `le`, `eq`, `ne` | numeric comparison (`eq`/`ne` compare as text when not numbers) |
| `{"derived": "name", …}` | same tests on a computed value |
| `{"all": [c, …]}`, `{"any": [c, …]}`, `{"not": c}` | combinations |

### Computed values (`derived`)

Computed once per index build, never per card shown.

| `type` | Keys | Value |
|---|---|---|
| `count` | `association` (an association of the table), or `table` + `fk` (field of that table pointing here); optional `where` (condition on the linked row) | number of linked rows |
| `lookup` | `association` or `table` + `fk`; `field` (path in the linked row); optional `prefer` (condition) | value of the first linked row (the one matching `prefer` first) |
| `sameValue` | `field` | number of records holding the same non-empty value (2 or more = duplicates) |
| `firstOf` | `items`: `[{"field", "prefix", "when"}]` | first non-empty item, with its prefix |
| `case` | `cases`: `[{"when", "value"}]`, `else` | value of the first case whose condition holds |
| `test` | `when` | `true` / `false` |
| `suspects` | — | records the Match and Merge add-on holds as suspects of this one (metadataset `<dataset>_<table>_MDS`, tables `Suspected` and `MatchingResult`) |
| `provided` | `name` | value given by a module through `com.on.ebx.layout.runtime.ExplorerValues.register` |

### Search

`search.fields`: paths (and `derived:<name>`) searched, accent- and case-insensitive, on the index text; `placeholder`.

### Facets

| Key | Content |
|---|---|
| `id` | unique in the variant |
| `kind` | `field` (values of a list field: enumeration, foreign key, boolean, short text), `flags`, `ranges`, `tree`, `favorites` |
| `field` | the path (`field`, `ranges`, `tree`) or `derived:<name>` |
| `title`, `open` (true), `roles` | |
| `widget` | `checks`, `chips`, `select` (`select` is refused beyond 50 distinct values) |
| `top` | values shown first (default 10); then "Show 10 more" up to 50, then only a search |
| `flags` | for `flags`: `[{"id", "label", "tone", "when"}]`; a record can raise several |
| `ranges` | for `ranges`: `[{"code", "label", "min", "max"}]` (`min` included, `max` excluded) |
| `parent` | for `tree`: path of the parent in the target table of `field` |

A facet over a field with many values never sends its whole list: the server counts and returns the most frequent values
(and the selected ones), or the values matching a search typed in the facet.

### Tiles

`[{"id", "label", "tone", "roles", "when": condition | "flag": "<flag id>" | "set": {"<facet id>": ["v", …]}}]`.
A click applies the tile as a filter. Tones: `ok`, `warn`, `bad`, `info`, `accent`, `muted`.

### Sorts

`[{"field": "/p" | "derived": "name", "dir": "asc" | "desc", "label"}]`; the first is the default.

### Card

```json
{"layout": "vertical", "minWidth": 260, "edge": "pill",
 "image": {"field": "/logo", "shape": "logo", "fit": "contain", "whenMissing": "initials"},
 "title": "/raisonSociale",
 "overline": [part], "pill": {"field": "/statut", "colors": {"ACTIF": "ok"}, "default": "muted"},
 "subtitle": [part], "lines": [part], "tags": [part], "kpis": [part],
 "gauge": {"field": "/scoreQualite", "widget": "bar", "max": 100, "green": 80, "amber": 50},
 "actions": [action]}
```

`layout`: `vertical`, `horizontal` (image on the left), `compact` (one line per record). `edge: "pill"` colours the top
edge like the pill. `image.shape`: `cover`, `side`, `logo`, `avatar`; `whenMissing`: `initials` or `hide`. `title` is a
path or `{"derived": "name"}`; without it the record's label is used.

A **part**: `{"field": "/p" | "derived": "name" | "flags": "<facet id>", "widget": "…", "options": {…}, "label", "roles"}`.

| Widget | For | Options |
|---|---|---|
| `text`, `bold`, `mono`, `copy`, `tag` | any | `prefix`, `suffix` (texts, one per locale, or a plain string) |
| `link` | foreign key | opens the linked record |
| `pill`, `dot` | list | `colors: {value: tone}`, `default` |
| `country` | country code | `show`: `both`, `flag`, `code` |
| `num` | number | |
| `money` | number | `unit`, `compact` (k, M) |
| `bar`, `ring` | number | `max`, `green`, `amber` |
| `level` | number | `green`, `amber` |
| `stars` | number | `max` |
| `alert` | number | amber when greater than 0 |
| `date`, `relative` | date | `warnDays`, `pastBad` |
| `flags` | — | the raised flags of a flags facet, `none` (text shown when none is raised) |

`lines` show `label : value` unless `options.nolabel`. A **long list colour** (`colors` on a field with many values)
only names the values that get a colour; every other value takes `default`.

An **action**: `{"action": "open"}`, `{"action": "favorite"}`, `{"workflow": "<publication>", "var": "<variable>", "single": true,
"label", "cond", "roles"}` (asks for confirmation, launches as the user with the record's XPath in `var`, opens the first
task; same rules as the form's Actions block), `{"service": "<module>@<Service>", "label"}`.

## EBX permissions

The index is built once per dataset and explorer; every answer is then filtered for the user:

- **records**: the user's visible records come from a request run with the user's session (`Request.setSession`:
  record access rules, model permissions, scripted record rules); counts, tiles, facet values and pages only cover them;
- **fields**: a field the user may not read (`SessionPermissions.getNodeAccessPermission`, tested per record since
  `setSession` does not apply record-dependent rules on fields) is neither shown, searched, sorted on nor counted; a
  facet, flag, tile, computed value or card part that reads it is removed for that user;
- **actions**: a workflow button shows only to who may launch the publication, a service button only to who may run it;
- every AJAX call re-checks the record a page names (favorite, action).

The visible records of a user are kept 60 s (and until the table changes).
