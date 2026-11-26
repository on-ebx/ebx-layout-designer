# Layout format `ebx-form-layout/1`

A layout is one JSON document, stored in field `layout` of `LayoutDesigner/FormLayout` (one row per model and table,
`version` increased at each save). The designer writes it; this page is for reviewing, diffing or generating one.

## Conventions

- **Paths** are relative to the record and start with `/`: `/Identification/Name`, `/Address/City`. An association is
  named by its path too (`/orders`).
- **Texts** are objects with one entry per locale: `{"en-US": "Overview", "fr-FR": "Vue d'ensemble"}`. A missing locale
  falls back on the instance's default locale, then on the first text written.
- `id` identifies a block inside the layout (used for anchors and "Go to field").

## Document

```json
{
  "format": "ebx-form-layout/1",
  "name": "Customers (B2C)",
  "model": "urn:ebx:publication:B2CCustomers",
  "table": "/root/Customer",
  "locales": ["en-US", "fr-FR", "it-IT"],
  "tabs": [ … ]
}
```

`model` is the schema location: `urn:ebx:module:<module name>:/WEB-INF/ebx/schemas/X.xsd` for a module model,
`urn:ebx:publication:<publication>` for a DMA model.

Since 1.10.0 the document may also hold `personal`, `adminSeesAll` and `variants`: see *Who sees what* below.

## Tab

| Key | Type | Meaning |
|---|---|---|
| `id` | string | since 1.10.0, stable id of the tab (a personal arrangement names tabs by it); absent: `t<index>` |
| `label` | text | tab title |
| `count` | array of paths | associations whose rows are summed into the title ("Orders (5)") |
| `roles`, `roRoles`, `locked` | | since 1.10.0, see *Who sees what* |
| `rows` | array of rows | |

## Row

| Key | Type | Meaning |
|---|---|---|
| `columns` | 1 or 2 | |
| `ratio` | `"11"`, `"21"`, `"12"` | widths of a two-column row (default `"11"`) |
| `section` | object | optional: `{"on": true, "title": text, "collapsed": false}` draws the row as a collapsible section |
| `blocks` | array of columns, each an array of blocks | `[[…left…], […right…]]` |

## Block: common keys

| Key | Meaning |
|---|---|
| `type` | `panel`, `hero`, `facts`, `validation`, `grid`, `map`, `tree`, `hours`, `duplicates`, `note`, `custom`; since 1.4.0 `timeline`, `history`, `completeness`, `stepper`, `checklist`, `chart`, `aggregates`, `pivot`, `linked`, `contacts`, `gallery`, `documents`, `links`, `validity`, `address`, `hierarchy`; since 1.5.0 `recordInfo`; since 1.6.0 `changes`, `usedBy`; since 1.8.0 `orgChart`; since 1.9.0 `valueHistory`, `sitesMap`, `sourceValues`, `workflowTasks`, `translations`, `inheritance`; since 1.22.0 `heatmap`, `gantt`, `compare`, `coverage`; since 1.24.0 `asOfTree`, `stateTimeline`, `delta` |
| `id` | unique in the layout |
| `title`, `note` | texts |
| `cond` | XPath predicate on the record; the block shows only when it is true (always shown on creation) |
| `hideCreate` | `true`: not shown when a record is created |
| `showIf` | a live condition (see *Behaviours*): the block shows or hides while the user types, without saving |
| `onHide` | on a panel with `showIf`: `keep` (default) or `clear` the values of its fields when it hides |
| `roles`, `roRoles`, `locked` | since 1.10.0, see *Who sees what* (a field of a panel also takes `roles` and `roRoles`) |
| `session` | since 1.23.0, a condition on the user's EBX session (tracking information, attributes), see *Conditions on the user's session*; a field of a panel and a button of an `actions` block take it too |

## Block types

**`panel`**: `fields` (array of fields), `ro` (whole panel read only), since 1.13.0 `tone` (`ok`, `warn`, `bad`, `info`,
`accent`, `muted`: the panel takes that colour as background and border, to stand out, e.g. a warning shown under a
`cond`), since 1.14.0 `toneIf` (`{"if": "<XPath predicate on the record>", "then": "bad", "else": "ok"}`: the colour
follows the record; a missing branch falls back to `tone`). A field:

| Key | Meaning |
|---|---|
| `p` | path |
| `label` | text replacing the model's label on this form |
| `w` | widget: `line`, `area`, `check`, `yesno`, `select`, `nocreate`, `radio`; read-only display forms `fig`, `badge`, `link`, `mono`, and the display widgets of 1.4.0 (below) |
| `showIf`, `mandatoryIf`, `readOnlyIf`, `onHide` | behaviours (below) |
| `session` | since 1.23.0, shown only when the user's session meets the condition (see *Conditions on the user's session*) |
| `ro` | read only (a computed field always is) |
| `wide` | label above, full width |

**`hero`**: `title` (a path here, not a text), `sub` (paths), `chips` (paths), `meter` (path of a 0–100 value),
`meterLabel` (text or locale map shown after the value; empty: "completeness", since 1.22.0), `status` (path).

**`facts`**: `items` (paths), `count` (path of an association whose rows are counted).

**`validation`**: `sev` (`{"error": true, "warning": true, "info": true}`), `group` (`severity`, `tab`, `none`), `max`
(messages before "more"), `links` (Go to field), `hideEmpty`, `actions` (array of `{"on": <path>, "btn": <text>,
"go": <path>}`: a button labelled `btn` shown when a message is about `on`, opening `go`), `covers` (paths of fields
this block stands for).

**`grid`**: `a` (association path), `style` (`native` or `cards`), `cardFields` (paths shown on each card), `addBtn`
(text). Since 1.14.0 a native grid outside the first tab is lazy unless `"lazy": false`. Since 1.11.0, `lazy: true` builds the native grid only when its tab is first opened, in a frame (module service
`LayoutGrid`), and `height` (pixels, default 460) is its height until the frame fits its content.

**`map`**: `lat`, `lng` (paths), `tall` (larger map), and since 1.7.0 `address` (paths, in order: number, street,
postcode, city, country…). When latitude or longitude is empty, the non-empty address values (a foreign key gives its label)
are joined with ", " and the EBX server asks a Nominatim search service for the position: OpenStreetMap's public one by
default (at most one call per 1.1 s, failures retried after ten minutes). Since 1.15.0 the form never waits for it:
an address not known yet is looked up in the background and the map shows at the next opening, or at once when the user
clicks *Locate now* (since 1.15.1: the browser asks the module's `/geocode` address, with a ticket signed by the form); positions found are kept
in `<ebx.home>/layout-designer-geocache.tsv` (property `ebx.layout.geocoder.cache`, `off` to keep them in memory only). The system property
`ebx.layout.geocoder.url` names another Nominatim (self-hosted), or `off` to never send an address out. The map then
says "position from the address …"; an unknown address shows a link to OpenStreetMap's search.
**Map computed from the address: each unknown address is looked up in a geocoding service (at most one request a second
on the public service) and kept in a cache. For many records, add latitude and longitude fields to the table and choose
them in the block.** A table of more than 1,000 rows placed by its address gets one WARN line in `kernel.log` (1.15.1).

**`tree`**: `parent` (path of the parent foreign key), `children` (association path).

**`hours`** (opening hours, read only): whether the record is open now in its own time zone, the current week (one card
per day, today highlighted) and the next exceptional days. Every table and field comes from the block; rows are read
through the record's associations.

| Key | Meaning |
|---|---|
| `week.a` | association of the regular hours (required) |
| `week.day`, `week.open`, `week.close` | fields of the association's table: day, opening time, closing time |
| `week.closed` | optional boolean field: the row means "closed" |
| `days` | optional, the day field's code for each ISO day: `{"1": "MON", …, "7": "SUN"}`. Empty: the code is recognised by itself — `1` to `7` (Monday = 1), English, French or Italian day names, full or abbreviated (`MON`, `MONDAY`, `LUN`, `LUNDI`, `LUNEDÌ`…), case ignored |
| `exceptions.a` | optional association of the exceptional hours; absent: no exception section, only the regular week counts |
| `exceptions.date`, `.open`, `.close`, `.closed`, `.reason` | fields of that table (date, times, optional closed flag, optional reason) |
| `max` | upcoming exceptions listed (default 3; 0 hides the section) |
| `tz` | optional field of the record holding a time-zone id (`Europe/Paris`); absent or unknown: the server's zone |
| `excTitle` | optional text: title of the exceptions section (default "Upcoming exceptional hours") |

Times: a text `HH:mm` (also `H:mm`, `HH:mm:ss`, `24:00`) or a time value. Dates: a date value or an ISO text. A day may
hold several rows (split hours); a slot whose closing time is before its opening time ends after midnight. A row marked
closed, or with a missing time, adds no slot; a day with no slot is shown "Closed". An exception replaces the regular
hours of its date. On creation the block says the hours are entered once the record exists.

```json
{"type": "hours", "id": "h1", "title": {"en-US": "Opening hours", "fr-FR": "Horaires"},
 "week": {"a": "/OpeningHoursTab", "day": "/DayOfWeek", "open": "/OpenTime", "close": "/CloseTime", "closed": "/IsClosed"},
 "days": {"1": "MON", "2": "TUE", "3": "WED", "4": "THU", "5": "FRI", "6": "SAT", "7": "SUN"},
 "exceptions": {"a": "/OpeningHoursExceptionTab", "date": "/ExceptionDate", "open": "/OpenTime", "close": "/CloseTime",
                "closed": "/IsClosed", "reason": "/Reason"},
 "max": 3, "tz": "/Timezone"}
```

**`duplicates`**: no other key.

**`note`**: `text`.

**`custom`**: `cls` (the name a `LayoutComponent` was registered under); any other key is passed to the component.


## Blocks added in 1.4.0

Every block below also takes `title`, `note`, `cond`, `hideCreate`, `showIf`. An association is read through the
record (`a`, its path); a field of its target table is a path of that table (`/amount`). Texts are localized objects.

| Block | Keys |
|---|---|
| `timeline` | `a`, `date` (date field of the rows, most recent first), `titleField` (default: the row's label), `detail`, `color: {field, map: {value: tone}}`, `max` (10) |
| `history` | `fields` (paths followed; empty: all), `max` (8), `author` (true), `summarizeAbove` (6: an entry changing more fields shows "N fields", the detail one click away; 0 never sums up, since 1.7.0), `creationDetails` (false: a creation shows who and when only, since 1.6.1). Reads `AdaptationTable.getHistory()`: history must be activated on the table, else the block says so |
| `completeness` | `fields: [{p, weight}]` (empty: every field placed on the form), `threshold` (80, %), `listMissing` (true, with "Go to field") |
| `stepper` | `field` (status), `steps` (codes in order; empty: the enumeration's values), `exits` (codes shown in red outside the steps), `labels: {code: text}` (optional) |
| `checklist` | `items: [{label, when, go}]`; `when` is `{"filled": path}`, a condition `{"field", "in"…}` or `{"xpath": "./Price > 0"}`; `go` (optional) the field opened by "Go to field" |
| `chart` | `a`, `by` (field of the rows), `measure: {fn: count / sum / avg, field}`, `shape` (`bars` or `donut`), `top` (6; the rest grouped as "Other") |
| `aggregates` | `a`, `items: [{label, fn: sum / avg / min / max / count, field, format: number / money / percent, currency, decimals}]` |
| `pivot` | `a`, `rows`, `cols` (fields of the rows), `value: {field}` (empty: a tick when the pair exists) |
| `linked` | `fk` (foreign key of the record), `fields` (paths of the target table), `open` (true: link to the record, `getURLForSelection`) |
| `contacts` | `a`, `name` (default: row label), `role`, `email`, `phone`, `main` (boolean: shown first, highlighted); since 1.6.2 `cardWidth` (minimum card width in px, default 320) and `max` (cards shown, default 60, a notice says when there are more) |
| `gallery` | `a` + `media` + `caption`, and/or `fields` (media or URL fields of the record), `perRow` (4), since 1.15.2 `fit` (`cover`, default: square thumbnails, cropped; `contain`: the whole image at its own proportions, for banners and logos) |
| `documents` | `a`, `file` (media or URL field), `name`, `docType`, `date`, `expiry` (true: a past date shows red) |
| `links` | `items: [{label, url, sameTab}]`; `${./path}` in `url` inserts the record's value, URL-encoded; a button whose value is empty is hidden; a `url` that is only `${./path}` (a website field) is used as it is, `https://` added when missing; only `http(s):`, `mailto:`, `tel:` and `/` addresses |
| `validity` | `from`, `to` (date fields), `warnDays` (30): upcoming, in force, ending soon, expired |
| `changes` | `against` (`creation`: the parent dataspace's snapshot the work dataspace was created from, default; `current`: the parent dataspace now), `max` (20 rows). Field by field, old value → new value, through `DifferenceHelper.compareOccurrences`; "created in this request" when the record is new; outside a child dataspace, or when the dataset has no copy in the parent dataspace, says there is nothing to compare |
| `usedBy` | `samples` (3 labels per relation), `max` (1000: referring rows looked at per block, "1000+" beyond; since 1.22.0), `showZero` (false), `otherDatasets` (true). The reverse relationships of the table (`SchemaNode.getRelationships(dataset, session)`), counted per referring table and field, filtered by the user's permissions |
| `valueHistory` | `fields` (paths, one tab each), `period` (`all`, `12m`, `90d`), `shape` (`step`, `line`), `listChanges` (true), `author` (true). Read in the table's history (`AdaptationTable.getHistory()`, the table must be historized): only the versions where the value changed are kept; a number gets a curve, the current value and the change since the first value; another value is listed only |
| `sitesMap` | `a` (association), `lat`, `lng` (optional, fields of the rows), `address` (fields in order: number, street, postcode, city, country), `label` (empty: the row label), `detail`, `hq` (yes/no field that highlights a row), `active` (yes/no field), `showInactive` (false), `tall` (false). Rows without coordinates are placed by the address through Nominatim (`ebx.layout.geocoder.url`); since 1.15.0 an address not known yet is looked up in the background and placed at the next display, and positions found are kept in `<ebx.home>/layout-designer-geocache.tsv` across restarts. For many rows, add latitude and longitude fields to the table and choose them in the block: each unknown address costs one request to the geocoding service, one a second at most; an unknown street falls back on its town and is flagged. The map is drawn with OpenStreetMap tiles loaded by the browser, with zoom and drag; up to 200 rows |
| `sourceValues` | `fields` (empty: all but the key), `source` (field naming the source system, as column header), `onlyDifferent` (true), `max` (6 sources). On a golden record of the Match and Merge add-on: the value each merged source gave, the value kept by the golden highlighted (public API `MetadataHelper`, `GroupOperations.getGroup`, `Group.getMatchedRecords`, called by reflection: without the add-on the block says so). On a merged record: a link to its golden |
| `workflowTasks` | `variables` (comma-separated names of the workflow variables holding the record's XPath or key). `pub` (since 1.22.0, optional: a publication name; the engine is asked about that publication only, which is the cheap way on an instance with many publications; empty: every publication). The running workflows found with `WorkflowEngine.getProcessInstanceKeysForDataContextVariableIncludingHistory` on every publication: label, start, creator, current tasks with who holds them and since when, a passed deadline. A task of the user (allocated to them, or offered to one of their roles) opens in EBX's pop-up inbox, anyone else's in the monitoring view |
| `translations` | Rows of a translation table: `a` (association), `lang` (language field), `fields` (translated fields), `expected` (languages, comma separated). Or fields of the record: `languages` = [{`code`, `fields`}]. Per language: share filled, missing fields; an expected language without a row is at 0 % |
| `inheritance` | `fields` (empty: all), `listInherited` (true). In a child dataset: the fields changed here with the parent's value beside them (`getValueWithoutResolution` = `AdaptationValue.INHERIT_VALUE` for an inherited one), the fields that follow the parent, or "created here" when the parent does not have the record; in a root dataset the block says it inherits nothing |
| `orgChart` | `parent` (foreign key to the parent, in the same table), `label` (empty: the record label), `sub` (line under the name), `color` + `colors` (tones by value; empty map: six automatic colours), `present` (`auto`: org chart, tree beyond `threshold` direct subsidiaries (24); `org`, `tree`, `sun`), `hideSwitch` (false), `openIn` (how a click opens an entity: `popup`, default, EBX's modal pop-up over this record, closing it comes back to the record and the tab; `replace`; `newTab`). The record's whole group (up to 2,000 entities) is drawn in the browser: org chart centred on the record (ancestors, the record, its direct subsidiaries, one more level on demand), collapsible tree with a search, or rings (one per level, slices sized by the entities below). The table is read once into a key → parent index, kept until `AdaptationTable.getLastModificationDate()` changes |
| `recordInfo` | `createdBy`, `createdOn`, `modifiedBy`, `modifiedOn` (booleans, true by default), `style` (`grid` or `line`), `relative` (true: adds "3 days ago"). Read from EBX's record API (`getCreator`, `getTimeOfCreation`, `getLastUser`, `getTimeOfLastModification`), user names through the directory; nothing to set up in the model |
| `address` | `lines` (street paths), `number` (street number, put on the first line; `numberPlace`: `auto` = after the street for DE, AT, CH, NL, BE, IT, ES and the other countries that write it so, before elsewhere; `numberBefore`; `numberAfter`), `zip`, `city`, `region`, `country`, `format` (`auto` by country, `zipFirst`, `cityFirst`, `zipAfter`), `mapLink` (true) |
| `hierarchy` | `parent` (foreign key), `up` (the parent's own parent key when the target is another table; same path by default on a self-referencing table), `label`, `depth` (6), `children` (association) |

A media field is read through the Digital Asset Manager's public API (`MediaType.getAttachment`,
`AssetManager.getDigitalAsset`, `DigitalAsset.getURL`), called by reflection so the form works without the add-on; a text
field holding an `http(s)` or `/` address works as well.

## Blocks added in 1.22.0

Each reads the rows of an association `a` of the record (5,000 at most). Every one accepts `only`:
`{"field": "status", "values": ["OPEN", "MITIGATED"], "not": false}` keeps the rows whose field holds one of the values
(`not: true` keeps the others).

| Type | Keys |
|---|---|
| `heatmap` | `x`, `y` (two fields of the rows, e.g. impact and probability), `xSteps`, `ySteps` (codes in order; empty: the field's enumeration, else the values met, sorted), `label` (shown on hover; empty: the row label). One cell per pair of values, the number of rows in it, green → orange → red by (x rank × y rank), highest `y` on top. Rows whose values are outside the axes are counted under the matrix |
| `gantt` | `start`, `end` (planned dates), `actualStart`, `actualEnd` (optional), `label`, `status` + `colors` (tone of the actual bar). One line per row sorted by start: a grey planned bar, a coloured actual bar (green when finished on time, red when late, purple while running, up to today), month ticks and a line on today. Each label opens the row |
| `compare` | `planned`, `actual` (number fields summed), `by` (optional: one line per value, else the total only), `plannedLabel`, `actualLabel` (texts; empty: the field labels), `unit`, `decimals` (0), `higherIsBetter` (false: an actual above planned is red, as for spending). Three figures (planned, actual, variance) and, with `by`, a pair of bars per value with its variance in % |
| `coverage` | `field` (the field each row should have), `label`, `target` (100: percentage under which the figure turns orange, under 80 % of it red), `max` (8: missing rows listed). Shows "N of M with <field>" and the rows without it, each a link |

Tones (`color.map`, `colors`): `ok` (green), `warn` (orange), `bad` (red), `accent` (purple), `info` (blue), `muted` (grey).

## Blocks added in 1.24.0: effective-dated models

For models where an entity (a fund, a contract, an organisation) is a stable identity whose values live in successive
states, each from an effective date to an end date (inclusive; empty = open-ended), with the link to the parent carried
by the state. Worked example: the ROSA fund repository (`Fund` → `FundState`, `SubFundState.fund`, `ShareState.subFund`).

Since 1.26.0 every link of these three blocks (an entity, a state, a row of a delta report) opens the record in EBX's own modal pop-up, a sub-session of the form (the table views' preview mechanism): the user stays on the form, on the chosen date, and comes back to it on close; a middle click still opens a new tab. Planned: an inline summary of the state under its line (fields configured per level), before opening the full record.

Permissions, as for every block: a state the user may not see (record permission) is left out, and so is a state whose
key to the parent, key to the entity or effective date the user may not read (field permission); every value shown goes
through the field permission. A `delta` report is one text field: whoever may read it reads the values it holds, whatever
the permissions on the fields it names. Cost: `asOfTree` runs one scan of each level's states table (an OR of the parents'
keys, 100 per query; 2,000 states per level at most, then a note), `stateTimeline` one association read or one scan,
`delta` none; every tab of a form is rendered when it opens, so keep these blocks on tables where a tree is a few hundred
states (ROSA: 6,572 sub-fund states, 15,100 share states, a scan of 20-25 ms).

| Type | Keys |
|---|---|
| `asOfTree` | `levels[]`, one per level under the root: `states` (the children's states table, `/root/SubFundState`), `parent` (its foreign key to the parent entity, `./fund`), `identity` (its foreign key to the child's own entity, `./subFund`), `effectiveDate`, `endDate`, `label` (a field of the state; empty: the state's label), `chip` (a field shown as a badge: status, code), `name` (text per locale: what the count calls the level's entities; empty: the label of the entity table the identity key points to, without a trailing parenthesis). `root`: a foreign key of the record to the root entity (empty: the record itself; on a state, `./fund`). `selfStates` + `selfIdentity`, `selfEffectiveDate`, `selfEndDate`, `selfLabel`, `selfChip`: the root's own states. `date`: a date field of the record giving the default date (empty: today). Every state of every entity is read (500 per table and per parent at most) and put in the page; the date input, the ◀ ▶ buttons (previous / next change among all effective and end dates) and *Today* decide which state each entity shows, with a count per level. Since 1.25.0 the date follows the user: without a `date` field, the block opens on the date last chosen in an explorer with an "in force on" date or in another block (`sessionStorage` key `ldfAsOf:<user>`), and a date chosen here is shared the same way. An entity with no state at the date is greyed, and hidden when none of its descendants has one either |
| `stateTimeline` | the states of one entity on a time scale: `a` (an association of the record to its states) or, when the record is itself a state, `via` (its foreign key to the entity: its siblings are drawn and the record's own state highlighted). `effectiveDate`, `endDate` (empty end: the band runs to the edge, faded), `label`, `badge` + `colors` (tone per value, e.g. the source or the project; without `colors`, one tone per value met), `date` (a date field of the record drawn as a dark marker, besides today's red line). Month, quarter or year ticks by span; each band and label opens the state |
| `delta` | a before/after report kept as JSON in a text field: `field` of the record, or of each row of `a` (one section per row, titled by `label`, with the count of changes). Shapes read: an object `{"name": {"before": "…", "after": "…"}}`; an array of `{"field"|"path"|"name", "before"|"old"|"from", "after"|"new"|"to"}`; either of them under a `changes`, `fields`, `items` or `delta` key. `labelsFrom`: the table whose field labels name the keys (empty: this table; a key may be `name`, `./name` or `/root/T/name`); `max` (50). A text that is not JSON says so; an empty report says "No change recorded" |

## Display widgets of a field (1.4.0)

Set `w` and the widget's keys on the field. A display widget makes the field read only on an existing record, with or
without `ro` (since 1.4.4; before, it applied only to a field read only by `ro`, a computed field or a read-only panel).
While a record is created, the field keeps EBX's input unless it is read only for another reason. The designer ticks
*Read only* when one is chosen.

| `w` | For | Keys |
|---|---|---|
| `badgeMap` | list, text, yes/no, key | `colors: {value: tone}`, `defaultTone` (`muted`) |
| `money` | number | `currency` (ISO code) or `currencyField`, `decimals` |
| `unit` | number | `unit` or `unitField`, `decimals` |
| `percent` | number | `scale` (100 or 1), `thresholds: [red below, orange below]` (50, 80) |
| `stars` | number | `max` (5) |
| `relativeDate` | date | `warnDays` (0: no colour; else past = red, within the days = orange) |
| `duration` | number | `unit`: `minute`, `hour`, `day`, `month`, `year` |
| `country` | text, list, key | `show` (`both`, `name`, `code`), `flag` (true); ISO 3166 alpha-2 or alpha-3 |
| `phone`, `email` | text | link (`tel:`, `mailto:`) and Copy |
| `url` | text | `show` (`domain` or `full`) |
| `color` | text, list | `colors: {value: css colour}` (optional), `showCode` (true) |
| `barcode` | text, number | `checkDigit` (true): EAN-13, EAN-8 and UPC-A drawn as bars, GS1 check digit verified (GTIN-14: digits only) |
| `copyId` | text, number, key | Copy button |
| `tags` | multiple, list | `max` (6), `colors` |
| `masked` | text | `visible` (4 last characters), `revealRoles` (roles that may show the whole value) |
| `recordLink` | foreign key | `sameTab` (false) |
| `thumbnail` | text, media | `size` (`small`, `medium`, `large`) |
| `geo` | number, text | `lng` (longitude field), `format` (`decimal` or `dms`) |
| `boolIcon` | yes/no | `labels: {true: text, false: text}` |

## Behaviours (1.4.0)

A **condition** is `{"field": path, "in": [values]}`, or `"notIn"`, or `{"field": path, "filled": true}`, or
`"empty": true`. Values are compared as stored: an enumeration's code, a foreign key's key, `true`/`false`.

| Where | Key | Effect |
|---|---|---|
| block | `showIf` | the block shows only while the condition holds, live; on a panel `onHide: "clear"` empties its fields when it hides |
| field | `showIf`, `onHide` | the same for one field of a panel |
| field | `mandatoryIf` | the mandatory asterisk follows the condition, live (`ebx_form_setMandatoryIndicator`) |
| field | `readOnlyIf` | read only when the condition holds, and/or for `roles`, never for `exceptRoles`; computed when the form opens |
| panel | `attributesBy` | `{table, key, familyCol, attributeCol, mandatoryCol, hideEmpty}`: a table of the same dataset says which fields go with each value of `key` (a family); a field the table lists shows only for its families (mandatory when `mandatoryCol` is true), a field it never lists always shows; `hideEmpty` (true) hides the panel with no field left. The attribute column holds a path (`/Dimensions/length`) or a field name. Families in a tree: `familyParent` (field of the families table holding the parent family) and `familyTable` (default: the table `key` points to); a sub-family then also gets its ancestors' attributes, the closest level deciding *mandatory* |

The live part uses the documented JavaScript API of EBX forms (`ebx_form_getValue`, `ebx_form_setValue`,
`ebx_form_setMandatoryIndicator`), called after each change of the controlling field
(`UIAtomicWidget.setActionOnAfterValueChanged`); the first state is computed on the server. A behaviour changes what is
shown, never what EBX validates: a value that must be mandatory needs a rule in the model.

```json
{"type": "panel", "title": {"en-US": "Wiper blade"}, "showIf": {"field": "/Family", "in": ["WIPER"]},
 "attributesBy": {"table": "/root/FamilyAttribute", "key": "/Family", "familyCol": "/family", "attributeCol": "/attribute", "mandatoryCol": "/mandatory"},
 "fields": [{"p": "/Length", "w": "unit", "unit": "cm"},
            {"p": "/Price", "w": "money", "ro": true, "currency": "EUR"},
            {"p": "/Mobile", "mandatoryIf": {"field": "/SmsOptIn", "in": ["true"]}}]}
```

## Who sees what (1.10.0)

By default everyone sees the whole form, as before 1.10.0. Three optional layers:

**Roles of a tab, block or field.** `roles`: visible only for these roles; `roRoles`: read only for these roles. Values
are role names as in the directory (`"Acheteur"`), `"administrator"` for the built-in one. A user who has none of the
`roles` does not get the item at all (a tab with it, a panel whose fields are all hidden, a row left empty); a user
who has one of the `roRoles` gets it read only (a panel or tab read only makes its fields read only). Administrators see
everything and keep their rights, unless the document says `"adminSeesAll": false`.

```json
{"id": "com", "label": {"fr-FR": "Conditions commerciales"}, "roles": ["Acheteur", "Comptabilite"], "rows": [ … ]}
{"type": "panel", "id": "pay", "roRoles": ["Acheteur"], "fields": [{"p": "/Iban", "roles": ["Comptabilite"]}]}
```

**Variants.** `variants` is an array of whole forms for some roles, in priority order; a user gets the first variant
one of whose `roles` they have, else the standard form (`tabs`). A variant holds `id`, `name` (text), `roles` and its
own `tabs`, which take the same keys as the standard ones. A variant without roles is never used.

```json
"variants": [{"id": "req", "name": {"fr-FR": "Demande de création"}, "roles": ["Demandeur"], "tabs": [ … ]}]
```

**Personal arrangement.** `"personal": true` lets every user arrange the form for themself ("Arrange my form", a button
on the form and an entry of the record's Actions menu). Each user's arrangement is one row of
`LayoutDesigner/UserFormPreference` (key: user, model, table, variant id — `std` for the standard form), its `prefs`
field a JSON object laid over the layout, never a copy of it:

| Key | Meaning |
|---|---|
| `tabs` | tab ids in the user's order; a tab not listed keeps its place |
| `defaultTab` | tab opened first |
| `hiddenTabs`, `hidden` | tabs and blocks (ids) not shown |
| `collapsed` | blocks folded under their title |
| `pinned` | blocks moved, in this order, to a first tab "My essentials" |

A tab or block with `"locked": true` can be neither hidden nor moved by a user. While a record is created nothing is
hidden or folded. A preference naming a tab or block that no longer exists is ignored, and a block added later appears at
its place for everyone.

None of this is security: a field left out of a form stays readable in the table view, an export or the APIs. EBX
permissions protect the data.

## Validation (1.17.1)

The `layout` field of `FormLayout` carries a constraint (`com.on.ebx.layout.designer.LayoutValidation`, bean property
`checks` = `all` or `off`) that never blocks a commit and is re-checked at every validation. It reports: invalid JSON
(error); the table no longer in the model (error); a path of a block, a field of a panel or the field of a
`showIf`/`mandatoryIf`/`readOnlyIf` found neither on the record nor on a linked table (error); a `component` no longer
registered, a `wf.pub` or a workflow button's publication no longer published, a model with no dataset on the instance
(warning). The runtime itself skips such references silently.

## Example

```json
{
  "format": "ebx-form-layout/1",
  "model": "urn:ebx:publication:B2CCustomers",
  "table": "/root/Customer",
  "locales": ["en-US", "fr-FR"],
  "tabs": [
    {
      "label": {"en-US": "Overview", "fr-FR": "Vue d'ensemble"},
      "count": [],
      "rows": [
        {"columns": 1, "blocks": [[{"type": "validation", "id": "v",
          "title": {"en-US": "To review", "fr-FR": "À traiter"},
          "sev": {"error": true, "warning": true, "info": true}, "group": "severity", "max": 5, "links": true}]]},
        {"columns": 2, "ratio": "11", "blocks": [
          [{"type": "panel", "id": "p1", "title": {"en-US": "Identity", "fr-FR": "Identité"},
            "fields": [{"p": "/Identity/firstName"}, {"p": "/Identity/lastName"}]}],
          [{"type": "panel", "id": "p2", "title": {"en-US": "Loyalty", "fr-FR": "Fidélité"},
            "fields": [{"p": "/Loyalty/tier", "w": "radio"}, {"p": "/Loyalty/points"}]}]
        ]}
      ]
    },
    {
      "label": {"en-US": "Orders", "fr-FR": "Commandes"},
      "count": ["/orders"],
      "rows": [{"columns": 1, "blocks": [[{"type": "grid", "id": "g1", "a": "/orders", "style": "native"}]]}]
    }
  ]
}
```

### `actions` (since 1.14.0)

Buttons on the record. `items`: a list of buttons, each with `label` (localized), and either `workflow` (a publication
to launch) with `var` (the data context variable that receives the record's XPath), or `service` (`module@Service`, opened
on the record in EBX's pop-up). Optional: `tone` (`ok`, `warn`, `bad`, `info`, `accent`, `muted`) and `cond` (an XPath
predicate on the record; false hides the button), `single` (`true`: while an instance launched with this record's XPath
in `var` is still running, the button opens its task, shown without being taken, instead of launching another). A workflow button shows only to a user who may launch the publication;
it opens the module service `LayoutAction` in EBX's pop-up, which asks for confirmation, launches as the user and opens
the first task, inside the perspective the form was opened in. The service reads the button again from the layout as the
user sees it (never from the address), and since 1.23.1 tests again, before launching, what decided its drawing: `cond`
on the record, `wf` and `session`, as well as the right to see the block and to launch the publication. A button hidden
by one of them cannot be launched by typing the address of the service: the user gets "this action is no longer available
for this record" and nothing is launched. The first task is looked for once, about a second after the launch; when it is
not there yet the user is sent to the inbox (the work appears there), the request never waits longer. Nothing is shown while a record is created, and a block with no button the user
may use is not drawn.

### Conditions tied to workflows (since 1.17.0)

Any block, and any button of an `actions` block, may carry `"wf"`:

| Key | Values | Effect |
|---|---|---|
| `ctx` | `outside`, `inside` | shown only when the form is NOT opened from a workflow task, or only when it is (`Session.isInWorkflowInteraction(true)`) |
| `running` | `hide`, `show` | hidden, or shown only, while an unfinished workflow instance holds the record's XPath or primary key in one of `vars` |
| `pub` | a publication name | restricts `running` to that publication; empty: every publication |
| `vars` | variable names, comma separated | the data context variables that point to the record (`tiersXPath`…) |

Example: the "Change bank details" button hidden inside any workflow task and while a bank-details change is running for
the supplier: `"wf": {"ctx": "outside", "running": "hide", "pub": "FournisseursChangementRIB", "vars": "tiersXPath"}`.
The running instances are looked up once per display and shared with the *Workflows in progress* block.

### Conditions on the user's session (since 1.23.0)

Any block, any field of a panel and any button of an `actions` block may carry `"session"`, read in the EBX session of
the user who opens the form: its tracking information (`Session.getTrackingInfo()`) and its attributes
(`Session.getAttribute(name)`), whatever set them in that session (a workflow task, a trigger, a user service).

| Key | Values | Effect |
|---|---|---|
| `trackingInfo.op` | `equals`, `contains`, `notEquals`, `notContains`, `filled`, `empty` | compares the tracking information with `trackingInfo.value`; no `op`: no condition on it |
| `attribute.name` | an attribute name | the session attribute to read; empty: no condition on attributes |
| `attribute.op` | the same operators, `filled` by default | compares the attribute's value with `attribute.value` |

**A session condition arranges the display, it protects nothing.** The tracking information is not only set by the
server side (a task, a trigger, a service): EBX's web component accepts a `trackingInfo` parameter in its address
(`UIHttpManagerComponent.setTrackingInfo`, public API), so a user may be able to choose the text a `trackingInfo`
condition compares. (That this parameter reaches `Session.getTrackingInfo()` inside the form has not been measured yet:
treat it as possible.) A session attribute (`attribute`) cannot be typed in an address, so prefer it for a condition that
must hold. What must really be refused belongs to EBX permissions, not to a display condition; since 1.23.1 a workflow
button does re-test its `session` condition when it is launched, which narrows but does not remove this limit.

Both parts must hold. Texts are trimmed and compared ignoring case; an attribute holding a collection or an array matches
when one of its values does, any other object is read through its text. The session is read when the form is drawn, like
the roles: a value set while the form is open shows at the next display. An item whose condition fails is not drawn at
all; for a field this is display only (EBX's own validation still applies).

Example: the purchasing panel shown only while the session carries the attribute `etape` = `validation_achat`, and the
"Reject" button hidden when the tracking information says the record comes from an import:
`"session": {"attribute": {"name": "etape", "op": "equals", "value": "validation_achat"}}` on the panel,
`"session": {"trackingInfo": {"op": "notContains", "value": "IMPORT"}}` on the button.
