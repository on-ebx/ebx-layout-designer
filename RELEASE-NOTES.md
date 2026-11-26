# Layout Designer for ON EBX: release notes

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
