# Layout Designer for ON EBX: release notes

## 1.30.0 (2026-11-23)

Record forms on models packaged in a module ([INSTALL.md](docs/INSTALL.md), [OPERATIONS.md](docs/OPERATIONS.md)).
Module classes and static files only: no runtime jar change.

- **The designer no longer changes a deployed module by default.** Until 1.29.0 *Activate* wrote the `recordForm` line
  into the module's XSD on the server. It now describes the declaration, as it does for a DMA model: the *Activation*
  panel gives the line, the table and the file, to be added to the module's source and delivered with the module (EBX
  reads an XSD at its start). The XSD on the server is still read, when it can be reached, so the banner says whether
  the table already declares the form. A table that declares it and has no active layout shows the standard form: the
  line can be put on every table of a module at build, and layouts then go live as data, with no restart.
- **`-Debx.layoutDesigner.xsdActivation=true` gives the former behaviour** (*Activate* / *Put the previous form back*
  write the XSD, with the backup, the lock and the well-formedness check of 1.23.3). **A server that relied on the
  button needs this property after the upgrade**: without it the button is not shown, and a request that asks for the
  write is refused with `LD-WRITE-OFF`, nothing written.
- **MCP**: `activation` of a module model carries `xsdLine` and `designerWritesXsd`, and `howTo` describes the
  declaration to make instead of sending to the designer's button.
- Layouts already activated are untouched: their tables keep declaring the form.

## 1.29.0 (2026-11-16)

Explorers ([EXPLORER-FORMAT.md](docs/EXPLORER-FORMAT.md)). Module classes and static files only: no runtime jar change,
no restart (reload the module's context).

- **Fix: a card with a banner image broke the list and wide-card views.** With `image.shape: "cover"` and several
  `layouts`, switching from cards to *List* or *Wide cards* gave the image the whole row and left the text one character
  per line (a card over 2,000 px high). The banner becomes a side image in wide cards and a thumbnail in the list; wide
  cards are at least 420 px wide.
- **Previous / next record from an explorer.** A record opened from a card now opens inside the table as the page filters
  it (`UIHttpManagerComponent.setFirstCallDisplayRecord`), so EBX draws the same previous / next arrows as from a table
  view. They follow the page's filter, in the table view's order (not the explorer's sort). Closing the record leaves the
  filtered table in the pop-up. `card.recordNavigation: false` (designer: explorer settings) opens the record alone as
  before.
- **Actions menu.** `header.actions` lists services of the table (a module's, or EBX's own such as `@exportToCSV`); the
  page shows them in an *Actions* menu, each only to the users EBX lets run it on the table, on the records the page
  filters. Set in the designer (explorer settings, *Actions menu*), checked by `explorer_validate`.

## 1.28.0 (2026-11-02)

- **Changes in this request follows linked objects** ([LAYOUT-FORMAT.md](docs/LAYOUT-FORMAT.md)): the block compared the
  record alone; it now also compares the rows of the associations it is given (`follow`) and, with `businessObject`, every
  relationship of the business object declared on the table (strong ones, 3 levels down; `light` adds the light ones).
  On a product's form, the recipes the request changes, adds or removes show under the product's own changes, each
  changed row openable field by field (old → new). Rows are read with the user's session on both sides and paired by
  primary key; a new record's linked rows are only counted; beyond 2,000 rows a relation is counted but not compared.
  Settings in the designer (associations to follow, business object, light relationships, rows shown per relation).

## 1.27.0 (2026-11-02)

- **Breaking:** `asOfTree`, `stateTimeline` and `delta` leave the Layout Designer. They were written for one project
  (ROSA) and are now registered components of its module `ebx-rosa`, unchanged: write `{"type": "custom", "cls":
  "rosa.asOfTree", …}` (and `rosa.stateTimeline`, `rosa.delta`) with the same keys. A layout that still names the old
  types shows unknown blocks until it is converted; `layout_validate` names the replacement. The explorer's "in force
  on" date (`asOf`) and the computed value `stateAt` stay: they are generic, and the `ldfAsOf:<user>` key the explorer
  writes is still read by `rosa.asOfTree`. (The module was renamed `ebx-effective-dated` shortly after and its components
  `dated.asOfTree`, `dated.stateTimeline`, `dated.delta`; the message of `layout_validate` still quotes the first names.)
- Registered components get what the built-in blocks use: `ComponentSupport.current()` (record, association rows,
  linked record, displayed values, field and record permissions, links that open a record in EBX's modal pop-up,
  once-per-display style sheets and scripts), `LayoutComponent.designerSpec()` (the component's settings in the
  designer's inspector, same controls as the built-in blocks; carried by `layout_describe_table` and the model export)
  and `LayoutComponent.title()` (a default panel title). A component that does not use them works as before.
- Runtime jar changed: installing 1.27.0 needs a Tomcat restart, like every runtime change.
- **MCP Apps form preview** (prototype, merged into main on 2026-11-04) ([MCP.md](docs/MCP.md#form-preview-mcp-apps-1270-prototype)): `layout_get`, `layout_starter` and
  `layout_validate` point at the UI resource `ui://ebx-layout-designer/form-preview`, which a host supporting MCP Apps (the
  Claude desktop chat) draws as a mock-up of the record form, check findings placed on their blocks. Read-only; the labels
  come from `layout_describe_table` through the host. The answers carry `designerLink`, the designer's absolute address.

## 1.26.0 (2026-10-26)

- `asOfTree`, `stateTimeline`, `delta`: a click on an entity, a state or a row of a report opens the record in EBX's own modal
  pop-up (a sub-session of the form, as the table views' preview button does) instead of a new browser tab; the form and its
  chosen date stay underneath. Planned next: an inline summary of a state under its line in the tree (fields per level) before
  opening the full record.

## 1.25.1 (2026-10-24)

- `asOfTree`: the count of a level names its entities ("3 sub-funds"), from the level's `name` text or the entity table's label,
  no longer the states table's label ("3 sub-fund states").

## 1.25.0 (2026-10-24)

- **Explorers: an "in force on" date** ([EXPLORER-FORMAT.md](docs/EXPLORER-FORMAT.md#in-force-on-date-1250)): `asOf`
  names the start and end date fields of a table of effective-dated states; the page gets a date input (today by default)
  and only the rows in force on that date count, facets and tiles included. The date is kept per user in the browser tab
  and shared with every explorer and with the *Hierarchy at a date* block of the forms. Set in the explorer settings of
  the designer.
- **Computed value `stateAt`**: a field of the state in force on the page's date among the states of a linked entity
  (the fund's name as it was on that date on a sub-fund's card, not its current name): one statement per page, cards only.
- The 1.24.0 blocks take the theme's main colour from `--ldf-main` (audit rule, lot C).

## 1.24.1 (2026-10-24, RC install: 1.24.0 ROSA blocks + robustness audit lots A-G)

- No change of its own: the build that brings together 1.24.0 and the robustness lots of 1.23.1 to 1.23.3.

## 1.24.0 (2026-10-24)

- **Three blocks for effective-dated models** ([LAYOUT-FORMAT.md](docs/LAYOUT-FORMAT.md#blocks-added-in-1240-effective-dated-models)),
  where an entity is a stable identity whose values live in successive states (effective date → end date) and the link
  to the parent is carried by the state: `asOfTree` (the hierarchy, e.g. fund → sub-funds → shares, as it stands at a
  date chosen on the form, with ◀ ▶ to the previous or next change and a count per level), `stateTimeline` (the states
  of one entity as bands on a time scale, open-ended bands faded, today's line, a marker on a date of the record, the
  current state highlighted when the form is a state) and `delta` (a before/after report kept as JSON in a text field,
  field by field, one section per row of an association). All three are in the designer's palette, category *Time* and
  *Quality*. Built for the ROSA fund repository (AMF), generic in the model they read.

## 1.23.3 (2026-10-24, robustness audit lots E, F and G)

**Geocoding (lot E)**

- **"Locate now" no longer holds a server thread** (audit L2-10): `/geocode` answers at once, 200 from the cache, otherwise 202
  `{"pending":true}` after queueing the address for ONE daemon thread that calls the service at its pace (one request a second);
  the page asks again every 2 s (60 times at most). The queue is bounded (500): beyond it the answer is 429 `LDG-429` with a
  message telling to retry in a few minutes. No wait inside the HTTP request any more. Not done: a per-user quota and a
  ticket tied to the addresses of the form (the ticket still lasts 4 hours); the bounded queue limits the damage.
- **The geocoding cache keeps no address text** (L2-11): key = SHA-256 of the normalised address, file lines are
  `hash, latitude, longitude, date`; the file is created readable by its owner only, entries older than 90 days
  (`-Debx.layout.geocoder.cacheDays=N`) are dropped when it is read and when it is compacted (every 5,000 additions), and a file
  of the old format (address in clear) is rewritten by hash at the first start.

**Operations, languages, tests (lot G)**

- **Italian in the explorer and the layouts home** (audit L2-21): `explorer.js` and `home.js` now carry French, English and Italian (any other language reads English, as before); the validation messages of a FormLayout row are written in the locales the instance declares as well as EN/FR/IT.
- **The runtime's own words are a bundle**: `RuntimeText` reads `RuntimeText.properties` (English), `RuntimeText_fr.properties`, `RuntimeText_it.properties` from the runtime jar instead of a table in the code; a language with no file, or a key missing from one, reads English. `RuntimeTextTest` keeps the three files on the same keys, checks every key the code asks for exists, and refuses a new visible literal (tag text, title/aria-label/placeholder/alt, accented literal) in the runtime sources. Not done: `title="map"` of the map's iframe is still in `LayoutPane`; an external folder where an administrator drops a fourth language is not offered.
- **`docs/OPERATIONS.md`** (L2-22): every `-D` property of the module, the administrators' explorer options (`explain`, `route=sql`, `parallel=0`, load test), the `kernel.log` lines and their levels, the codes (`LD-*`, `LDD-`, `LDX-`, `LDG-429`, MCP codes), the files written (geocache, `.bak`, lock), threads and caches, the jar upgrade. `INSTALL.md` points to it; `LAYOUT-API.md` is reduced to a pointer to `MCP.md`; `LAYOUT-FORMAT.md` documents `usedBy.max` and `workflowTasks.pub`.
- **`tools/test.sh` runs three more checks**: `RuntimeTextTest`, `GeoTicketTest` (forged, altered, expired and malformed tickets refused, a good one accepted) and `EscapingTest` (`esc` of the runtime blocks and of every page script, `Sql.lit`, `Sql.like`, `XPath.lit`, `Model.quote` on a hostile set). The four checks `tools/package.sh` runs at packaging are untouched.

**Designer, storage and activation (lot F)**

- **The designer checks a layout before saving it** (audit L2-13): the same size, nesting, shape, block id/type and reference checks as the MCP tool `layout_put`. A layout with no tabs, a block without `id`, an unknown field or table is refused with a coded message (`LD-INVALID`, `LD-INVALID-JSON`, `LD-TOO-LARGE`, `LD-TOO-DEEP`, `LD-EMPTY`) listing the first errors; nothing is written and the draft stays on the page. Warnings still save.
- **Activation no longer rewrites the XSD blindly** (L2-14): it runs under a lock (this module's monitor plus a lock file `<xsd>.layout-designer.lock` next to the XSD; a second administrator is told to retry, `LD-BUSY`), keeps a time-stamped `.bak-...-layout-designer` copy, checks that the new text is well-formed XML before replacing anything (otherwise the file is left as it was and `LD-MALFORMED` or `LD-XSD-INVALID` says so, the copy is kept) and writes a temporary file moved over the XSD in one step. The confirmation shows the absolute path of the file changed and warns when other folders of the server declare the same module. The lock file is not removed (zero bytes, harmless).
- **Layout lookup by key** (L2-15): `LayoutStore.findRow` asks for the row of a (model, table) with a predicate and keeps its key until the FormLayout table's modification date changes (a negative answer too), instead of scanning the table at each form display and each opening of an Actions menu ("Arrange my form" permission rule included).
- **Cache-busting follows the build** (L2-19): `?v=` on the designer, home, personalization and explorer pages is the version the build stamps (`ModuleVersion.version()`, from `VERSION` and git); a build with no stamp uses the server start time. `tools/package.sh` refuses a literal `ASSET_VERSION` in the source.
- **No exception text in the designer pages** (L2-12, designer, layouts home, personalization): users see "unexpected error (code LDD-...)" in EN, FR and IT; the exception and its stack are in `kernel.log` under the same code. `denied` now says what to do, with code `LD-DENIED`.

## 1.23.2 (2026-10-24, robustness audit lots C and D)

**Explorer (lot D)**

- **No more OR between a sub-query and another condition** (audit L2-09): a search over a `lookup` value, a flag or a tile that
  combines a count, a "same value" or suspects with another field is written as ONE `t."$pk" IN (SELECT ... UNION SELECT ...)`
  (each member one branch), never `(IN (SELECT ...)) OR LIKE ...`, which EBX evaluates record by record (17 minutes on one core,
  not cancellable). The LIKE keeps no CAST on a text field and gets ESCAPE only when the text holds `%`, `_` or `\`. The
  check refuses (error) an `any` that mixes a Match and Merge state with such a sub-query: write two flags. The search leaves
  such a metadataset source out when a lookup is searched too.
- **Favorites read by key** (L2-16): one predicate on user, explorer, dataspace and dataset instead of a sweep of the whole table,
  and a short cache retired by the table's modification date.
- **Explorer pool stopped at shutdown** (L2-18): `handleRepositoryShutdown` stops it; pages then run their statements one after
  the other. The administrator load test is off unless the server runs with `-Debx.layoutDesigner.loadTest=true`.
- **No exception text in the explorer** (L2-12, explorer and its designer): users see "unexpected error (code LDX-...)" in EN, FR
  and IT; the exception and its stack are in `kernel.log` under the same code.

**Form rendering (lot C)**

- **Association cards apply field permissions** (L2-03): a field the user may not read is no longer drawn in a `grid` block in `cards` style (same rule as every other block).
- **Associations are opened once per display** (L2-05): rows and counts are read with the user's own session on the request (EBX leaves out what they may not see, no permission per row), shared by every block and tab, and a count stops at 1000 (`-Debx.layoutDesigner.countCap`): tabs and panels show "1000+". A `lazy` grid counts with the same bound.
- **"Used by" is bounded** (L2-06): at most `max` referring rows are looked at (default 1000, per block, new key `max`), "1000+" beyond. The referring records are the ones EBX already returns for the relation: no extra XPath scan (a non-key predicate scans the table).
- **Hierarchy chart index** (L2-07): built by one thread at a time (others use the previous index or wait for the first), at most 500 000 links (`-Debx.layoutDesigner.orgChart.maxIndexLinks`, a partial index is announced by the chart's "large group" note and `kernel.log`), eight indexes at most; entities the user may not see no longer count towards the 2000-node cap.
- **Workflow conditions** (L2-08): the designer asks for the publication of a `running` condition (it is the only way to ask the engine about one publication); without it a publication that does not declare the variable is remembered for a minute and not asked again, and the first failing value shape ends the loop. The *Workflows in progress* block also reads `pub`.
- **One style sheet per form** (L2-17): style and scripts are written by the first tab only (about 40 KB less per extra tab).
- **Perspective colours** (L2-20): the form takes the theme's main and focus colours (`--ldf-main`, `--ldf-focus`), the ON EBX purple stays as fallback; donut and org chart use the main colour first. Not done: explorer.js and personalize.js (not in this lot).
- **Behaviour configuration tables** (L2-24): cached until the configuration or family table changes (and renewed every ten minutes), shared by all users.
- **No blank form on a rendering failure** (L2-25): an exception while the form is built writes `LDF-RENDER` in `kernel.log` and shows EBX's standard form; a tab that fails shows the "layout could not be read" text. A tab, row, block or field written as something other than an object is ignored.
- **For measuring** (audit section 5): with the kernel logger at DEBUG, `kernel.log` lists every block, tab and form that took more than 200 ms ("[layout-designer] block grid of tab 2 … 340 ms").

## 1.23.1 (2026-10-24)

- **Workflow buttons are re-tested when launched** (audit L2-01): the `LayoutAction` service now checks again `cond`, `wf` and
  `session` of the button, as the form did when it drew it, besides the right to see the block and to launch the publication.
  A button hidden on this record can no longer be launched by typing the service's address: message "no longer available for
  this record", nothing launched.
- **Session conditions documented as display only** (L2-02): they protect nothing; EBX's web component accepts a `trackingInfo`
  parameter (possible, not measured yet), so prefer a session attribute. Guide, format reference and the designer's hint say so.
- **No more 8-second wait after a launch** (L2-23): the first task is looked for once after about a second; if it is not there
  the user goes to the inbox with the message "Launched. The work will appear in your inbox."
- **No exception text shown** (L2-12, this service only): a failed launch says "could not be launched, ask an administrator to
  look at the server log"; the detail stays in `kernel.log`.
- **MCP endpoint bounded** (L2-04): the administrator test now comes before the body is read; the body is capped at 2 MB (413)
  and its nesting at 64 levels (400), also for a layout passed as a JSON string, a batch at 50 requests. Each refusal names its
  setting: `-Debx.layoutDesigner.mcp.maxBodyBytes`, `-Debx.layoutDesigner.mcp.maxDepth` (read at each call). `layout_put` and
  `explorer_put` refuse a document larger than the body cap, besides the existing checks.
- **MCP tools show no exception text** (L2-12, MCP part): a failing tool answers code `FAILED` and says to read `kernel.log`;
  a JSON parse error no longer echoes the parser's message.

## 1.23.0 (2026-10-20)

- **Conditions on the user's session** ([LAYOUT-FORMAT.md](docs/LAYOUT-FORMAT.md#conditions-on-the-users-session-since-1230)):
  a block, a field of a panel or a button of an Actions block may carry `"session"`, shown only when the EBX session of the
  user who opens the form meets it: its tracking information (`equals`, `contains`, `notEquals`, `notContains`, `filled`,
  `empty`) and/or one of its attributes (`Session.getAttribute`, same operators). Whatever set the value in that session
  counts: a workflow task, a trigger, a user service. Read when the form opens, compared ignoring case. In the designer:
  *Depending on the user's session* in the inspector, and a tag on the canvas; the layout check flags an unknown operator,
  a comparison without its text and an attribute condition without a name.

## 1.22.1 (2026-10-20)

- Release package: the worked explorer examples (`docs/examples/`) are shipped, as the README and the MCP guide link to
  them; the API page no longer refers to an internal note.

## 1.22.0 (2026-10-19)

- **Four new blocks over an association's rows** ([LAYOUT-FORMAT.md](docs/LAYOUT-FORMAT.md#blocks-added-in-1220)):
  `heatmap` (a matrix of two ordered fields, e.g. probability × impact of a project's risks), `gantt` (planned and actual
  bars with today's line, e.g. project phases), `compare` (planned against actual with the variance, overall and per
  value, e.g. budget lines) and `coverage` (how many rows have a field filled and which do not, e.g. local accounts mapped
  to the group chart). All four take `only` to keep some rows. They are in the designer's palette.
- **Hero gauge label**: `meterLabel` names what the gauge measures (progress, score…); it said "completeness" whatever
  the field.
- **Org chart wording**: a record with nothing below no longer reads "1 direct subsidiary"; the counts say "entities
  directly below", which fits any hierarchy (cost centres, organisation units), not only companies.

## 1.21.1 (2026-10-16)

- **Typing no longer queues searches.** EBX answers a page's requests one after the other, so a request replaced by a newer
  one could not be stopped on the server (measured: the newer one waited 5.6 s behind it). The page now sends nothing while
  a request runs and keeps only the latest one waiting.
- List view: a row's buttons stay on one line.
- **Match and Merge values read in the metadataset (editor's advice).** A count on the add-on's values alone ("Suspect",
  state, golden…) is made on the metadataset's `RecordMetadata` (`./hasSuspects = 'true'`: 5 ms against 2.0 s through the
  linked field on 576,692 records) when the user sees every record and every record has its metadata row; otherwise as
  before. Lists and combined counts JOIN the metadataset once (`LEFT JOIN mds."/root/RecordMetadata" rmd ON
  FK_AS_STRING(rmd.functionalId) = t."$pk"`: 1.3 s against 4.5 s) instead of a sub-query or the linked field, and the
  "suspects" computed value reads `hasSuspects` there instead of the `Suspected` table.

## 1.21.0 (2026-10-15)

- **MCP server** (`/ebx-layout-designer/rest/mcp`, administrators only): an agent reads a table as the designer does,
  starts, checks and saves record form layouts and explorers. Ten tools; a save is refused when the check finds an error
  or when the stored version moved. See [docs/MCP.md](docs/MCP.md).
- **Explorer check** (`ExplorerCheck`): every path, computed value, facet, flag, tile, sort, group, card part and action
  of an explorer, and whether EBX can evaluate each filter (the page drops what it cannot, with only a log line).

## 1.20.0 (2026-10-15)

- **Cards or list, the user's choice.** The configurator lists the layouts users may switch between (`card.layouts`); the
  page shows a switch and remembers the choice per user.
- **↑/↓ button** next to the sort: the user reverses the chosen sort.
- **Page header**: a create button opening EBX's creation form (only for users EBX lets create), and links to other tables
  of the dataset (filtered, with a service, in the current perspective) or to an address.
- **Linked records buttons** ("Contacts 2", "Subsidiaries 1"): EBX's own table view of the linked records, filtered on the
  card's record; hidden when there is none.
- **Dates relative to today** in conditions (`days`: "LEIs to renew within 90 days", "expired").
- **Groups**: records under a heading per value of a field (sector, country…), with the group's count. The heading of the records
  without a value can be renamed (`group.emptyLabel`).
- A condition on a Match and Merge linked field (state, golden, suspects…) now combines with a computed value: SQL reads
  it in the metadataset. Before, such a flag was dropped without a message.
- Fixes found reproducing the Companies explorer: a tree row that is its own parent (NACE sections) is a root (values were
  indented 12 levels); a header link with a blank `table` opens the explorer's own table (it was not drawn).

## 1.19.0 (2026-10-15)

- **Freshness chosen by the explorer's configurator.** *On demand* (the default): computed results are shared and kept
  until someone presses **Refresh** or until a maximum age (60 minutes by default); the page says when they were
  computed and warns when data changed since. *Automatic*: recomputed as soon as a table read changes, for small volumes
  or few users. Set in the designer, explorer settings (`freshness` in the explorer's JSON).
- **Two steps**: count, records and tiles come first, the facets right after in a second request.
- **Collapsed facets are not counted** until their box is opened.
- **A request replaced by a newer one stops** on the server (search-as-you-type no longer queues work nobody reads).

## 1.18.0 (2026-10-15)

- **Explorers ask EBX for every answer.** The explorer no longer reads the whole table into memory: each search,
  facet, tile, sort and page becomes an EBX XPath predicate run by a `Request` with the user's session (EBX's own
  index), or an EBX SQL statement when a computed value is involved (a count or a value read through a linked table,
  Match and Merge suspects through the add-on's metadataset). Nothing to build at the first opening, a change made in
  EBX shows in the next answer, and the explorer gets faster whenever EBX's engine does. Measured on 576,692 persons
  before the change: 15 s for the first opening, 2 to 15 s per action.
- Text search compares like EBX (case-insensitive substring; accents count). A `lookup` with `prefer` now takes the
  smallest value among the preferred linked records. A `provided` (Java) value shows on cards only.
- The statements of one page are sent to EBX at the same time (the explorer's own pool, the user's session shared as
  Insight's profiling does), and EBX's answers are kept a minute: shared between users who see every record (EBX checks
  it), per user otherwise, one computation when several users ask the same thing at once. 200 simultaneous users on
  576,692 records: median 2.5-4 s once answers are kept, against 47-519 s without.
- Text filters in SQL without `CAST`/`ESCAPE` so EBX's index serves them (editor's advice: 4 s to 0.1-0.4 s); suspects
  in SQL as one `IN` over a `UNION`. Under a filter, a long list of values says "more than N" instead of counting them.
- Rights checked with test accounts on a copy: records hidden by a record rule, hidden fields and a hidden linked table
  never show in counts, facets, searches or cards; users with restricted rights never share answers.
- Administrators: `explain=1` (each statement sent to EBX and its time), `route=sql`, `parallel=0`, and a load test.

## 1.17.1 (2026-10-14)

- **A layout that names something gone is reported.** A field removed from the model, a table or association that no
  longer exists, a workflow publication no longer published, a registered component no longer registered: the
  `FormLayout` record now carries a validation message for each (error for a field or a table, warning for the others;
  constraint `LayoutValidation` on `layout`, never blocks a save). The designer counts them in the top bar
  (*N reference(s) to check*), marks each block concerned with a red tag, shows a removed field of a panel as a red row
  with a remove button, and lists them all with *Go to block* and *Remove*.
- **At run time a gone reference stays silent**: a removed field is left out of the facts block (it used to show its
  raw path), and a service button whose service no longer exists for the record is not drawn (it used to answer
  "Access denied").
- The map's inspector note no longer mentions the old per-display lookup limit.

## 1.17.0 (2026-10-14)

- **Conditions tied to workflows**, on any block and on each button of an *Actions* block (inspector, *Depending on
  workflows*; layout key `"wf"`): show it only outside a workflow task or only inside one (`Session.isInWorkflowInteraction`),
  and hide it, or show it only, while a workflow instance is running for the record (one publication or any, through the
  variables that hold the record's XPath or key). Example: no "Change bank details" button inside a task, nor while a
  bank-details change is already running for the supplier. The running instances are looked up once per display; the
  *Workflows in progress* block now shares that lookup.

## 1.16.0 (2026-10-14)

- **Explorers: a search page over any table, drawn like a form.** A new service, *Design the explorer* (Actions menu of
  any table, administrators), draws a page with a search box, facets (values of a list field, flags raised by conditions,
  ranges of a number, a tree, the user's favorites), tiles that filter in one click, sorts, and one card per record. Every
  attribute of a card is chosen and set by clicking it: field or computed value, widget (text, pill or coloured dot,
  amount, gauge bar or ring, stars, relative date, flag of a country, link to the linked record…), colour per value, area
  of the card, roles; the card also takes an image from a DAMA media field, an edge coloured like its pill, three
  layouts, and buttons (open, favorite, workflow, service). Variants by role, every text in every locale. The explorer
  is stored in the new table `ExplorerLayout` (several per table possible) and shown by the service *Explorer*
  (`ebx-layout-designer@Explorer`, parameter `ldxExplorer`), for a perspective action; a saved change shows at the next
  opening, no restart. Format: [docs/EXPLORER-FORMAT.md](docs/EXPLORER-FORMAT.md).
- **Built for volume.** The page never receives the whole table nor a whole list of values: the server keeps one index
  per dataset and explorer (rebuilt when a table it reads changes), counts, filters, sorts and pages there, and sends one
  page of cards and, per facet, the most frequent values (10, then 10 more up to 50, then a search). Computed values (a
  count of linked rows, a value of a linked row, duplicates of a value, the Match and Merge suspects, a value given by a
  module through `ExplorerValues.register`) are read once per index build, one read per linked table.
- **EBX permissions in every case.** Each user gets their own view of the index: the records come from a request run
  with their session (record access rules, model permissions), the fields they may not read are tested per table and
  per record; a facet, a flag, a tile, a sort, a searched field or a card attribute reading a hidden field is removed for
  them, counts and tiles only cover what they see, and every AJAX call re-checks the record it names. A workflow button
  shows only to who may launch the publication.
- **Favorites** are kept in the new table `ExplorerFavorite`, per user, explorer and dataset.
- The module's data model gains two tables: **one server restart** is needed after the upgrade (as for any change of the
  module's model).

## 1.15.2 (2026-10-14)

- **An image gallery can show whole images.** The *image gallery* block cut every image to a square thumbnail, so a wide
  image (a product's tagline banner) lost both ends of its text. A new setting, *Framing* (`fit` in the layout), keeps
  the square thumbnails by default (`cover`) or shows each image whole, at its own proportions, up to 360 px high
  (`contain`).

## 1.15.1 (2026-10-13)

- **The designer warns when a map is computed from the address.** In the inspector of a *map* or *sites on a map* block
  whose latitude and longitude fields are empty and whose address fields are set, a notice says that each unknown address
  is looked up in a geocoding service (one request a second at most on the public service) and kept in a cache, and
  advises latitude and longitude fields for many records. The same text is in the user guides and the format reference.
  A table of more than 1,000 rows placed by its address gets one WARN line in `kernel.log` per server start.
- **"Locate now" on a map waiting for positions.** A *map* or *sites on a map* block whose addresses are not known yet
  shows a *Locate now* button: the browser asks the module (`/ebx-layout-designer/geocode`, with a ticket signed by the
  form, valid four hours, since that address does not see the EBX session) for each address in turn, at the service's
  pace, and places it on the map without reloading the form; an address not found falls back on its town. Positions
  found go to the same cache as the background lookups.

## 1.15.0 (2026-10-13)

- **A form never waits for the geocoding service any more.** The *map* and *sites on a map* blocks placed an address
  without coordinates by asking Nominatim while the form was being drawn: about 0.8 s per address plus the 1.1 s the
  public service asks between two calls, up to six addresses per form, and again after every restart (memory cache).
  Measured on EBX 6.2.3 on the supplier form: 0.7 to 6.2 s for a record opened for the first time, 0.5 to 0.8 s once its
  addresses were known. Now an address not known yet is looked up by one background thread and shows "position being
  looked up"; it is placed at the next opening. Positions found are kept in a file (`<ebx.home>/layout-designer-geocache.tsv`,
  property `ebx.layout.geocoder.cache`, `off` to disable) and read back after a restart.

## 1.14.0 (2026-10-13)

- **An "Actions" block** (palette, category Actions): buttons that launch a workflow on the record, or open a service on
  it. A workflow button shows only to who may launch the publication; it asks for confirmation in EBX's pop-up, launches
  the workflow as the user with the record's XPath in the chosen variable, and opens the first task, inside the
  perspective. EBX 6.3's own workflow launchers do not show in the record's Actions menu; this block puts the launch on
  the form. Each button can take a colour and an XPath condition, and *one request at a time per record*: while a
  request launched for the record is still running, the button opens its task (shown, not taken) instead of launching another.
- **A panel's colour can follow the record** (inspector of a panel, *Colour by a condition*; layout key `toneIf`: an
  XPath predicate, the colour when true and the colour otherwise). One panel can turn red on an anomaly and green
  otherwise.
- **A native grid outside the first tab now loads when its tab is opened by default**, as `"lazy": true` did since
  1.11.0; a grid can still be built with the form by unticking the option (`"lazy": false`). On EBX 6.2 each native grid
  costs about a second of server rendering. Grids shown as cards are not affected.

## 1.13.0 (2026-10-13)

- **A panel can take a colour** (inspector of a panel, *Panel colour*; layout key `"tone"`: `ok`, `warn`, `bad`, `info`,
  `accent`, `muted`, the tones of the badges). The panel gets the tone's background, a border of its colour (thicker on
  the left) and a title in that colour, so that a panel shown only when something is wrong (a `cond`) stands out from
  the others. *None* keeps the white panel.
- **The *changes* block no longer lists a date and time that only differs by milliseconds**: shown to the second, both
  sides read the same (a value written with milliseconds, then saved again through a form, which drops them). A
  difference of a second or more is still a change, as in EBX's own comparison.

## 1.12.0 (2026-10-13)

- **Del removes the selected block or field** in the designer, like the inspector's *Remove* button and the bin: select it
  and press Del (Backspace on a Mac keyboard). *Undo* puts it back. The key does nothing while typing in a field of the
  inspector, in Preview mode, or when a tab or a row is selected.

## 1.11.1 (2026-10-13)

- **The creation form of a loaded-on-opening grid is no longer cut.** In 1.11.0 the frame measured the position of what
  it showed; a record form fills its frame (its buttons are anchored to the bottom), so the frame kept the height of
  the grid (about 200 px) and the form opened by the "+" was cut. The frame now grows by what a scrolling area inside it
  hides, keeps its height while a form is shown, and goes back to the grid's height when the form is closed.

## 1.11.0 (2026-10-13)

- **A grid can be loaded when its tab is opened** (inspector of a grid block, *Load when its tab is opened*, layout key
  `"lazy": true`; optional `"height"`, in pixels, before the frame fits its content). The form no longer builds that
  native grid when it opens: the block shows a placeholder, and the first time its tab becomes visible a frame loads a
  page that draws this grid alone (module service `LayoutGrid`, never in a menu), with EBX's own toolbar, "+", filter
  and paging; the "+" creates a row already linked to the record. The block's and the tab's counters follow a row added or
  removed in the frame (Data Services `…/<association>:count`, with the user's permissions), and the frame fits the height
  of what it shows (a short grid, an open creation form). Measured on EBX 6.2.3 on
  a supplier form with five grids: about 0.75 s of server rendering instead of about 5 s (each native grid costs about
  1 s on 6.2.3, much less on 6.3). A grid the user may not see (role, EBX permission) is not drawn in the frame either.

## 1.10.2 (2026-10-12)

- **A bin to remove a block or a field** (contributed by Christian Mayor, PR #1): drop a block or a field on the bin at
  the bottom right of the designer to remove it from the form; a whole block can now be dragged by its body, not only by
  its tag. Undo puts it back. The bin is hidden in Preview mode.

## 1.10.1 (2026-10-12)

- **EBX permissions are applied to every block, not only to the fields.** Fields have always gone through EBX's own
  widgets, which apply the user's permissions; the blocks around them read values through the Java API, which does
  not, so a header, key figures, a display widget, the history, the changes, values by source, a validation message, the
  rows of an association or a linked record could show a value the user may not read. Every read now asks EBX
  (`SessionPermissions.getNodeAccessPermission` / `getAdaptationAccessPermission`): a field the user may not read is left
  out of a panel and of every list, a record they may not see is left out of rows, counts, the hierarchy and "Used by",
  and a block whose fields are all out of reach is not drawn at all; *Arrange my form* does not even name such a block.
  Checked on EBX 6.2.3 with a buyer account and dataset permissions hiding a group, one field of a panel and one field of
  another tab. The designer itself is for administrators only.

## 1.10.0 (2026-10-12)

- **Who sees what, by role.** By default nothing changes: everyone sees the whole form. A tab, block or field can now be
  made *visible for* some roles only, or *read only for* some roles (inspector › *Who sees it*, roles picked from EBX's
  directory or typed). A user who may not see a part does not get it at all: a hidden tab takes its blocks, a panel whose
  fields are all hidden disappears. Administrators see everything unless the layout says otherwise. This arranges the
  form only: EBX permissions still protect the data.
- **Variants of a form.** A layout can hold whole forms for some roles (*Form › + New variant*, or *Profiles and
  variants › Duplicate as a variant*), in priority order: each user gets the first variant they have a role for, else
  the standard form. *Profiles and variants* lists them, shows who gets which form and a *Who sees what* table.
- **Preview as** a role or a user of the directory: in Design mode what they do not see is struck through, in Preview
  mode it disappears and the canvas switches to the variant they would get.
- **Arrange my form.** When the layout allows it, every user can arrange the form for themself: order of the tabs, the tab
  opened first, blocks hidden, folded or pinned to a first tab *My essentials*. A button on the form and an entry of the
  record's Actions menu open it in EBX's pop-up. The arrangement is laid over the layout, never a copy, so a later change
  in the designer still reaches everyone; *Always shown* keeps a tab or block out of reach; nothing is hidden while a
  record is created. Stored in a new table `UserFormPreference` of the module's dataset (the module's model changes: one
  restart after installing).
- Tabs now keep a stable `id` in the layout. Format additions: `roles`, `roRoles`, `locked`, `personal`, `adminSeesAll`,
  `variants` (docs/LAYOUT-FORMAT.md, *Who sees what*).
- An empty *My essentials* tab says why: every pinned block has a display condition the record does not meet.
- `build.sh` compiles the module against the runtime it has just built, ahead of any older runtime jar in the server's
  lib. `tools/test.sh` runs the checks that need no server.

## 1.9.0 (2026-10-12)

- **New block "Value over time"** (palette › Time): the successive values of one or more fields, read in the table's
  history, one tab per field. A number gets a curve (steps, or a line), its current value and the change since the first
  value; the list of changes gives the date, old → new value and the author ("automatic process" for EBX's system user).
  A value that never changed says so instead of drawing a flat line. Period: the whole history, 12 months or 90 days.
- **New block "Sites on a map"** (palette › Location): the rows of an association (establishments, addresses) on an
  OpenStreetMap map, with their list beside it; a click on a site centres the map on it, zoom and drag work. A row is
  placed by its latitude and longitude, else by its address (the EBX server asks Nominatim, at most six new addresses per
  display, answers kept in memory); an unknown street is placed on its town and flagged. A yes/no field highlights the
  head office, another hides inactive sites.
- **New block "Values by source"** (palette › Quality): on a golden record of the Match and Merge add-on, the value each
  merged source gave, field by field, the value kept highlighted; by default only the fields where sources differ, the key
  left out. A merged record links to its golden. Public API of the add-on, called so that a form works without it.
- **New block "Workflows in progress"** (palette › Actions): the workflows whose variables point to the record (its XPath
  or key), on every publication and its earlier versions: label, start, current tasks with who holds them and since when.
  The user's own task opens in EBX's pop-up inbox; someone else's opens in the monitoring view.
- **New block "Translations"** (palette › Quality): for each language, the share translated and the missing fields, from
  a translation table (a row per language, expected languages reported at 0 % when absent) or from fields of the record.
- **New block "Inheritance"** (palette › Quality): in a child dataset, the fields changed here with the parent's value,
  and those that follow the parent; "created here" when the parent does not have the record.
- **Activation, clearer**: the state of the form is now a banner under the designer's header, always visible, with the
  next action as its main button: not used yet (Activate), activated and waiting for EBX's restart, DMA model, or active.
  "See the steps" opens a step-by-step guide. For a DMA model it gives the class to copy, where to declare it in the
  model's component library (Component name, Configuration › Rule), where to choose it for the table (Form layout ›
  Component), then Publish; for a module model, the line written into the XSD and why a restart is needed; when the
  designer cannot write the file, the line to paste by hand.
- **The designer knows when the form is really used**: the form reports each table it is drawn for, so the banner turns
  green ("last drawn on … on …") for a DMA model too, where nothing else tells it (in memory, since the server started).
- The history reading is shared by the "Change history" block and the new one.

## 1.8.1 (2026-10-12)

- **Hierarchy chart**: up to six subsidiaries sit centred under the record, each hung on the line like an org chart (they
  were packed to the left); cards no longer overlap their neighbour.

## 1.8.0 (2026-10-12)

- **New block "Hierarchy chart"** (palette › Relations): the group the record belongs to, through a foreign key to the
  parent in the same table. Three layouts, switchable on the form: an **org chart** centred on the record (its ancestors
  above with the number of other entities beside them, the record with its counts, its direct subsidiaries as cards, one
  more level on demand), a **tree** of the whole group, opened down to the record, with a search, and **rings** (one per
  level, slices sized by the entities below, the record's path solid). In automatic mode the org chart becomes the tree
  beyond 24 direct subsidiaries. A line under the name and a colour by value (tones, or automatic colours) are chosen in
  the designer. A click opens the entity's record in EBX's pop-up over the current one, so closing it comes back to the
  record and the tab (or, as chosen, instead of the current record, or in a new tab). The table is read once into a key → parent index kept until the table
  changes, then only the group's records (up to 2,000) are read.

## 1.7.0 (2026-10-11)

- **Map from an address**: the map block takes address fields in order (number, street, postcode, city, country…). When
  latitude or longitude is empty, the EBX server asks OpenStreetMap's search (Nominatim) for the position of the address
  and the map says so under it. Answers are kept in memory, calls spaced by more than a second as the public service asks;
  `-Debx.layout.geocoder.url=<url>` points to another Nominatim, `=off` never sends an address out.
- **Change history**: a change of many fields at once (an enrichment, an import) shows "N fields", the detail one click
  away. *Sum up beyond N changed fields* (`summarizeAbove`, 6) sets the threshold, 0 never sums up.

## 1.6.2 (2026-10-11)

- **Contacts block**: wider cards (320 px minimum, `cardWidth` to change it) so that a name and an e-mail address stay on
  one line, and no more breaking in the middle of a word. `max` sets how many contacts are shown (60 by default, as
  before); when there are more, the block now says so instead of cutting the list silently.

## 1.6.1 (2026-10-09)

- **Change history**: a creation now shows who and when only, not every value of the new record (they are the form
  itself). *List the values at creation* (`creationDetails`) brings the old display back.

## 1.6.0 (2026-10-09)

- **New block "Changes in this request"** (palette › Quality): in a child dataspace (a workflow's work dataspace), what
  the record changes compared with the reference, field by field (old value struck through → new value), or "created in
  this request". Compares with the reference as it was when the request was created (default) or as it is now. Public
  API `DifferenceHelper.compareOccurrences`.
- **New block "Used by"** (palette › Relations): the records of other tables (and other datasets) that point to this
  one, counted per table and field, with a few examples. Public API `SchemaNode.getRelationships(dataset, session)`,
  filtered by the user's permissions.

## 1.5.0 (2026-10-09)

- **New block "Record information"** (palette › Time): who created the record and when, who changed it last and when.
  Each of the four items can be hidden, the block shows as a grid or on one line, with "3 days ago" if wanted. The values
  are those EBX keeps for every record (`Adaptation.getCreator`, `getTimeOfCreation`, `getLastUser`,
  `getTimeOfLastModification`), user names through the directory: nothing to set up in the model. The designer's preview
  shows the real values of the record chosen.

## 1.4.4 (2026-10-09)

Fixes from the "layout module tests" report.

- **Back to the table**: a *Close* button in the designer's bar (it asks first when changes are not saved).
- **Activation**: the module's folder is also found when Tomcat declares it in `conf/server.xml` (a Tomcat run by
  Eclipse), with a relative `docBase`, or under `wtpwebapps`. A table missing from the XSD now says so.
- **Display widgets** (highlighted figure, badge…) make the field read only on an existing record even when *Read only*
  was not ticked: a layout saved before 1.4.0, or a key field, used to show the plain input.
- **Labels above the field** (and labels of display rows) use the same size and colour as EBX's own form labels.

## 1.4.3 (2026-10-09)

- **Activation**: the table is found in the model's XSD by its real nesting. A table whose name is also the name of a
  group in an earlier table (`/root/Contact` next to a "Contact" group of `/root/Companies`) used to be reported as not
  found, so the form could not be declared.
- The activation screen no longer says "DMA model" for a module model whose file or table it cannot reach: it says the
  declaration must be made by hand, and why.

## 1.4.2 (2026-10-08)

- **Fields driven by a configuration table, families in a tree**: `attributesBy.familyParent` names the field holding a
  family's parent (in `familyTable`, or by default in the table the driving foreign key points to). A sub-family then also
  gets the attributes of its ancestors; when two levels list the same attribute, the closest one decides whether it is
  mandatory. A loop in the parents stops the walk.
- Release checks now test the freshly built runtime jar first (the server's installed copy used to come first on the
  class path).

## 1.4.1 (2026-10-08)

- **Formatted address**: a `number` field goes on the first street line, before or after the street after the country
  (`numberPlace` to force it): "201 Sussex Street", "Hauptstraße 5".
- **Links and actions**: an address made of one field only (`${./Contact/website}`) is used as it is (it used to be
  URL-encoded and the button hidden); `https://` is added when missing.

## 1.4.0 (2026-10-08)

- **Component library**: the palette's blocks are grouped by category and searchable (name, description, keywords);
  the widget list of a field offers only what suits its type, in *Input* and *Display* groups. Every setting of the new
  components is made in the inspector, nothing is fixed in the code.
- **16 new blocks**: timeline, change history, completeness, lifecycle steps, checklist, breakdown chart (bars or
  ring), association totals, matrix, linked record, contacts, image gallery, documents, links and actions, validity
  period, formatted address, hierarchy.
- **20 display widgets**: pill coloured by value, amount, value with unit, percentage bar, stars, relative date,
  duration, country, phone, e-mail, web address, colour, EAN / GTIN barcode, copyable identifier, tags, masked value,
  link to the record, thumbnail, GPS coordinates, yes / no icon.
- **Behaviours**, live while the user types: show a block or a field only for some values of another field, a mandatory
  asterisk that follows a value, read only by value or role, and the fields of a panel driven by a configuration table
  (family → attributes). Documented EBX APIs only (`ebx_form_getValue`, `setActionOnAfterValueChanged`).
- **Collapsible sections**: a row can be drawn as a section with a title, open or collapsed.
- Formats in `docs/LAYOUT-FORMAT.md`. Layouts of 1.3.0 are read unchanged.

## 1.3.0 (2026-10-08)

- **Opening hours** block (`"type": "hours"`): open now in the record's time zone, the current week one card per day
  (today highlighted, a bar for the open hours), the next exceptional days. Nothing fixed: the associations and their
  fields, the codes of the day field (or their recognition: 1-7, English, French or Italian names), the optional
  exceptions, how many are listed, the time-zone field and the titles are chosen in the inspector. Times as text
  `HH:mm` or time values; several slots per day; slots ending after midnight. Format in `docs/LAYOUT-FORMAT.md`.

## 1.2.0 (2026-10-07)

- **State of the form in the top bar**, next to Save, as the model declares it: *Declared in the model*, *The table uses
  another form*, *Not used by the table*; for a DMA model, *DMA model: check the declaration* (no public API tells it).
- **Activation** button: opens the activation in the inspector (declare the designed form, put the previous one back, the
  DMA instructions), highlighted when the form is not used.
- **</> Code** button: the saved JSON and the frozen Java class in a dialog. The block at the bottom of the page is gone.
- Public EBX Java API only: every class the module uses is in the published Javadoc of EBX or of the Match and Merge
  add-on.

## 1.1.1 (2026-10-07)

- Designer on a screen under 1280 px wide: the inspector (the settings of the selected tab, row, block or field) opens
  as a drawer on the right while something is selected, with a Close button (and Escape). It used to drop below the
  form, out of sight, and the palette slid over it.

## 1.1.0 (2026-10-07)

- **About** page (service `ebx-layout-designer@About`, on the `FormLayout` table, for every user): version, commit, build
  date, the running ON EBX, a line to copy into a support request, and links to the documentation.
- The documentation is served by the module (`/ebx-layout-designer/www/help/`): user guide in English and French,
  release notes, and for administrators the installation guide and the layout format. Generated from the Markdown by
  `tools/build_help.py`, so it is always the one of the installed version.
- *Help* link in the designer's top bar, *Documentation* and *About* links on the *Record form layouts* page.
- The build stamps `version.properties` (version from the git tag, commit, build time).

## 1.0.0 (2026-10-07)

First release.

- **Designer** (service *Design the record form*, on every table, administrators only): tabs, one- or two-column rows,
  blocks placed by drag and drop, a record of the table as live preview, an inspector, one input per instance locale
  for every text, a *Translations* view listing what is missing.
- **Runtime** (`ConfigurableForm`, a `UIForm` in the server's `lib`): reads the table's active layout at each display,
  so a saved layout shows at the next opening of a record, with no restart. Without a layout the standard EBX form shows.
- **Blocks**: panel of native field rows, identity band, key figures, validation report, association (native EBX grid or
  cards), map (OpenStreetMap), group structure, Match and Merge state, help text, registered Java component. Any block
  can be conditioned by an XPath predicate on the record and hidden on creation.
- **Validation report block**: the record's own EBX validation report, by severity, each message located
  (tab › panel › field) with a *Go to field* link that opens the right tab. A message written in one locale only falls
  back on the instance's default locale.
- **Record form layouts** page: saved layouts, and the choice of a dataspace, dataset and table with EBX's own selectors
  to start a new one.
- **Declaration in the model**: for a model packaged in a module, the designer writes the `recordForm` line in the XSD
  (with a backup) and can put the previous form back. For a DMA model, the class is declared in the model screen (or
  with the `data-model` MCP operation `set_form_layout`) and the model republished.
- Public EBX Java API only; no MCP server needed; Java 11 bytecode.

Seen running on EBX 6.3.0-rc2 (FastTrack): the module model Companies (73 fields, 11 associations) and a DMA model of
B2C customers (3 tables, three validation rules of each severity).

Known limitations: see `docs/USER-GUIDE.md`, section *Limitations*.
