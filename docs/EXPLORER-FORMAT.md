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
  "freshness": {"mode": "onDemand", "maxAgeMinutes": 60},
  "derived": {"etab": {"type": "count", "association": "/etablissements"}},
  "variants": [{"id": "std", "name": {"fr-FR": "Standard"}, "roles": [], "layout": {
    "search": {"fields": ["/raisonSociale", "/siren"]},
    "facets": [], "tiles": [], "sorts": [], "pageSize": 48, "card": {}
  }}]
}
```

`freshness` (1.19.0): `mode` = `onDemand` (default) or `auto`; `maxAgeMinutes` (default 60) for `onDemand`. Set in the
designer (explorer settings). See *Kept answers* below.

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
| `{"field": "/d", "days": {"from": 0, "to": 90}}` | (1.20.0) a date from today + `from` days (included) to today + `to` days (excluded); either bound may be left out (`{"to": 0}` = before today) |
| `{"derived": "name", …}` | same tests on a computed value |
| a Match and Merge linked field (`/Linked_Field_State`, `GroupId`, `GoldenId`, `AutoCreated`, `HasSuspects`) | read in SQL through a JOIN of the metadataset's `RecordMetadata` (1.20.0, JOIN since 1.21.1), so it combines with computed values; alone, counted in `RecordMetadata` itself for a user who sees every record (1.21.1) |
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

`[{"field": "/p" | "derived": "name", "dir": "asc" | "desc", "label"}]`; the first is the default. Since 1.20.0 the page
shows a ↑/↓ button that reverses the chosen sort for the user.

### Groups (1.20.0)

`"group": {"field": "/p" | "derived": "name", "dir": "asc" | "desc"}`: the records come in groups of that value (a list
field: enumeration, foreign key, boolean), each under a heading with its number of matching records; the chosen sort
applies inside each group. One count per group shown on the page. `emptyLabel` (texts per locale): the heading of the
records without a value ("(empty)" by default).

### Header (1.20.0)

```json
"header": {"create": true,
  "links": [{"label": {"en-US": "All contacts"}, "table": "/root/Contact"},
            {"label": "Location map", "service": "my-module@Map"},
            {"label": "E-invoicing", "table": "/root/EInvoicingAddress", "predicate": "./active = true", "roles": ["Finance"]},
            {"label": "Help", "url": "https://example.com/help"}]}
```

`create`: a button opening EBX's creation form for the table, shown only to users EBX lets create a record
(`true`, or `{"label": …}`). `links`: another table of the dataset (`table`, empty = this one; optional XPath `predicate`
and `service`), opened in the current perspective when there is one, shown only when the user may read that table; or an
address (`url`: this server's `/…` in the same tab, `https://…` in a new one, `newTab` to choose).

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

`layout`: `vertical`, `horizontal` (image on the left), `compact` (one line per record). `layouts` (1.20.0): the layouts
the user may switch between (`["vertical", "compact"]`); with two or more the page shows a switch and remembers the choice
per user in the browser. `edge: "pill"` colours the top
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
task; same rules as the form's Actions block), `{"service": "<module>@<Service>", "label"}`,
`{"action": "linked", "association": "/contacts" | "table" + "fk", "count": "<count computed value>", "hideWhenNone": true, "label", "cond"}`
(1.20.0: opens EBX's own view of the linked table filtered on this record, shows the count on the button and hides the
button when there is none — `hideWhenNone` is `true` unless set to `false`; only for users who may read that table).

## How the answers are computed (1.18.0)

Every answer is EBX's own: the explorer keeps no copy of the data.

- **Search, facets, tiles, page, counts**: each choice of the page becomes an EBX XPath predicate (on the table's own
  index: `osd:contains-case-insensitive`, `=`, `osd:is-null`, comparisons, the Match and Merge `Linked_Field_HasSuspects`
  field) run by a `Request` with the user's session, sorted by EBX (`RequestSortCriteria`) — or, when a condition reads a
  computed value, an EBX SQL statement (`Adaptation.createQueryBuilder`, also with the user's session).
- **Computed values in SQL**: `count` and `lookup` are correlated sub-queries on the linked table's foreign key
  (`FK_AS_STRING(c.fk) = t."$pk"`); `suspects` reads the add-on's metadataset (`Suspected`, or `MatchingResult` with
  `lastResult = 1` before 6.3), registered in the statement as `mds`; `sameValue`, `test`, `case`, `firstOf` are SQL
  expressions. A `lookup` with `prefer` takes the smallest value among the preferred linked records, else among all.
  A `provided` value (Java) is shown on cards only: a facet, flag, tile or sort reading it is left out (logged).
- **Facet counts**: a short list of values (static enumeration, boolean, referenced table of 30 records or less) is
  counted one value at a time (one XPath count each when possible); a longer one by one SQL `GROUP BY … ORDER BY
  COUNT(*) DESC` for the values shown, plus `COUNT(DISTINCT …)` without filter only (under a filter the list says "more
  than N", since that count takes seconds on a large table); up to 5,000 matching records, the records are read
  through EBX's index (`Request`) and their values counted; flags and ranges by one statement summing `CASE`s when
  SQL is needed.
- **Search text**: case-insensitive substring, as EBX compares (`osd:contains-case-insensitive`, `LOWER(…) LIKE`):
  accents count. A static enumeration and a small referenced table are also found by their labels.
- **Text filters in SQL** are written `LOWER(t.field) LIKE '%text%'` on text fields, with no `CAST` and an `ESCAPE` clause
  only when the text holds `%`, `_` or `\`: EBX's index then serves them (4 s against 0.1-0.4 s on 576,692 records).
- **At the same time**: the statements of one page (count, page of records, each facet, each value of a short list) are
  sent to EBX in parallel on the explorer's own pool (as many threads as cores, 8 at most, for the whole server), the
  user's session shared by the threads as Insight's profiling does. Answers are identical to one-after-another.
- **Kept answers** (1.19.0, chosen per explorer by its configurator, `freshness`):
  - *On demand* (default): the answers computed are kept until a user presses **Refresh** or until the maximum age
    (`maxAgeMinutes`, 60 by default), whatever changes in the data meanwhile. The page shows when they were computed
    ("Computed at 10:42 (3 min ago)") and, when a table read has changed since, "Data changed since." with the
    Refresh button highlighted. Refresh starts a new computation for every user sharing those answers. This is the
    setting for large tables or many users: a hundred users opening the page cost one computation per question.
  - *Automatic*: answers kept one minute, invalidated as soon as a table they read changes (1.18.0 behaviour); the
    page says "Updated automatically". For small volumes or few users.
  - Cards (computed values and flags of the records shown) are always read fresh.
  - A user who sees every record of every table the explorer reads (EBX counts them with the user's session and
    without) shares answers with every other such user; anyone else keeps answers of their own. Users asking the same
    question at the same moment wait for one computation. Measured on 576,692 records, 200 simultaneous users on the
    same page: median 2.5-4 s with the answers kept, 4.7-11 s right after a change; 47-519 s without sharing.
- **Two steps** (1.19.0): the page first asks the count, the records and the tiles, then the facets in a second request;
  facets whose box is collapsed are not counted until opened. EBX answers a page's requests one after the other: while one
  runs, the page sends nothing and keeps only the latest new request waiting (1.21.1); the server still drops a request
  that a newer one of the same page has overtaken (`{"cancelled": true}`).
- **Administrators' options** on the page's query: `explain=1` returns every statement sent to EBX and its time;
  with it, `route=sql` asks SQL even where XPath could answer and `parallel=0` sends the statements one after another
  (comparisons). `LDX_RUN.ajax.load` runs a load test: `n` simulated users (1-300) asking the same page at the same time,
  `rounds`, `cache=1` to let them share answers; it answers the response times.

## EBX permissions

- **records**: every request and query runs with the user's session (`Request.setSession`, `Query.setSession`):
  record access rules, model permissions, scripted record rules; counts, tiles, facet values and pages only cover them;
  a linked table's records are counted under the same rules;
- **fields**: a field the user may not read (`SessionPermissions.getNodeAccessPermission` on the dataset) is neither
  shown, searched, sorted on nor counted; a facet, flag, tile, computed value or card part that reads it is removed for
  that user; a computed value reading a linked table (or the metadataset) the user may not read is removed too;
- **record-dependent rules on a field** are not applied by EBX's `Request` and SQL filters (their Javadoc says so):
  the explorer follows EBX here. A card still reads each field with `getNodeAccessPermission(node, record)` and leaves
  out a value hidden on that record;
- **actions**: a workflow button shows only to who may launch the publication, a service button only to who may run it;
- every AJAX call re-checks the records a page names (cards, favorite, suspects named on a card, action).
