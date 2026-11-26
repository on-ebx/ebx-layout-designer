# Layout Designer for ON EBX: release notes

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
