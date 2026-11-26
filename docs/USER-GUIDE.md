# Layout Designer: user guide

The Layout Designer lays out the record form of an EBX table by drag and drop, with no code and no compilation. It is
for administrators. Business users only see the result: the record form of the table.

## 1. Start a layout

**From the list of layouts**: the *Record form layouts* page (dataset `LayoutDesigner`, table *Form layouts*,
**Actions › Record form layouts**, or its entry in a perspective) lists the saved layouts: table, model, version,
state, last change. *Open the designer* opens one on a dataset of its model.

To start a new one, choose the **dataspace** and the **dataset** with EBX's own selectors (tree and search), then the
**table** (the list follows the chosen dataset), then *Continue*.

**From the table itself**: open the table, **Actions › Design the record form**.

The first time, the designer proposes a starting layout built from the model: a *General* tab with one panel per
group of the model (two columns), the loose fields in a first panel, and one tab per association. It is a draft until
you save it.

## 2. The screen

| Area | What it holds |
|---|---|
| Top bar | *Preview with* (the record shown in the preview), *Language* (the locale of the preview and of the texts you type), *Translations*, *Design* / *Preview* mode, *Width* (test a narrow screen), *Help*, the **state of the form** as the model declares it (*Declared in the model*, *The table uses another form*, *Not used by the table*; *DMA model: check the declaration*), *Activation*, *</> Code*, *Undo*, *Save* |
| Palette (left) | the table's **fields** by group (search by label or path; a placed field is marked), its **associations**, the **blocks** by category (search on name, description and keywords), the **registered components** |
| Form (centre) | the record form as it will look, with the chosen record's real values |
| Inspector (right) | the settings of what you clicked: tab, row, block or field, or the activation of the form. Under 1280 px wide it opens as a drawer on the right while something is selected |

## 3. Building the form

- **Tabs**: *+ Tab*, drag a tab to move it. The inspector sets its label (one per locale) and a counter: the sum of
  the rows of the ticked associations, shown in the tab title ("Orders (5)").
- **Rows**: *+ Row with 1 column* or *2 columns*, with proportions 1:1, 2:1 or 1:2. Under 900 px a two-column row
  stacks, left column first.
- **Fields**: drag a field into a panel. Dropped on an empty column, it creates a panel around itself. Per field: a
  label for this form (empty = the model's label, translated by EBX), a widget, read only, label above on the full
  width. A mandatory field left off the form is flagged: creating a record without it would fail.
- **Associations**: drag one to place its grid.
- **Blocks**: drag from the palette; drop between two rows to open a new row. A block dropped on a tab goes to its end.
- Every change says what it did ("panel X was created around Y") and *Undo* takes it back.

### Widgets of a field

The list offers only the widgets that suit the field's type, in two groups.

- **Input**: text, single-line text, text area, number, date, check box, Yes / No (radio), drop-down list, list without
  "Create" (foreign key), radio buttons. EBX's own widget is used when nothing is chosen.
- **Display (read only)**: highlighted figure, pill, link, code, and since 1.4.0 pill coloured by value, amount with
  currency, value with unit, percentage bar, star rating, relative date ("in 12 days"), duration, country (name and
  flag), phone and e-mail (with Copy), web address, colour, EAN / GTIN barcode (bars and check digit), copyable
  identifier, tags, masked value (some roles may show it all), link to the record of a foreign key, thumbnail, GPS
  coordinates, yes / no as an icon. Their settings (currency, unit, colours per value…) appear under the list. Choosing
  one ticks *Read only*. Since 1.4.4 a display widget always makes the field read only on an existing record (a layout
  saved earlier without the box ticked shows it too); while a record is created the field keeps EBX's input.

### Behaviours of a field

Under *Behaviours* in the field's inspector:

- **Show only if (live)**: the field appears or hides while the user types in another field, without saving
  (`Family is one of Wipers`, `Opt-in is filled`…); its value can be kept or cleared when it hides;
- **Mandatory if (live asterisk)**: the asterisk follows a value;
- **Read only if**: for some values of a field, and/or for some roles, except for others (computed when the form opens).

A behaviour changes what is shown, not what EBX validates: to block saving, add a rule to the model.

## 4. Blocks

| Block | Settings |
|---|---|
| **Panel** | title, note under the title, fields, whole panel read only |
| **Identity band** | title field, subtitle fields, chips, gauge (a 0–100 field), status pill. Shows "New record" on creation |
| **Key figures** | a row of short values, plus the row count of an association |
| **Validation report** | see section 5 |
| **Association** | *Native grid* (EBX's own grid: toolbar, filter, paging, creation) or *Cards* (chosen fields on each card, the full grid under *Manage the rows*); title (default: the association's label), add button label |
| **Map** | a latitude and a longitude field; OpenStreetMap in a fixed-height box, *Large map* option |
| **Opening hours** | open now (in the record's time zone), the current week one card per day, the next exceptional days. Choose the association of the regular hours and its day, opening and closing fields (and an optional "closed" field), optionally the codes of the day field, the association of the exceptional hours and its fields, how many exceptions to list, and the record's time-zone field. Details in the layout format reference |
| **Group structure** | the parent foreign key and the association of children: parent, the record, its children |
| **Duplicates** | the Match and Merge state of the record (golden, merged, group). Without the add-on, the block says so |
| **Help text** | a paragraph for the user |
| **Registered component** | a Java block written by a developer (section 8) |

Since 1.4.0:

| Block | What it shows |
|---|---|
| **Timeline** | the rows of an association on a timeline, most recent first, coloured by a field |
| **Change history** | who changed what and when, for the chosen fields (history must be activated on the table) |
| **Completeness** | the share of filled fields (each with a weight), the missing ones with *Go to field* |
| **Lifecycle (steps)** | the steps of a status field, the current one highlighted, exit steps in red |
| **Checklist** | points ticked by a condition: a field filled, a value, or an XPath predicate |
| **Breakdown (chart)** | an association's rows grouped by a field, counted, summed or averaged, as bars or a ring |
| **Association totals** | sum, average, minimum, maximum or count over an association's rows, as a number, an amount or a percentage |
| **Matrix** | an association's rows crossed on two fields (availability by country and channel) |
| **Linked record** | chosen fields of the record a foreign key points to, with a link to open it |
| **Contacts** | the people of an association as cards, e-mail and phone with Copy, the main contact first |
| **Image gallery** | images of media or URL fields, of the record or of an association |
| **Documents** | files of an association: name, type, date (red once an expiry date is past), link |
| **Links and actions** | buttons to an address built from the record's values (`https://shop.example.com/p/${./Sku}`) |
| **Validity period** | upcoming, in force, ending soon or expired, with the days left |
| **Changes in this request** (1.6.0) | in a workflow's work dataspace, what the record changes compared with the reference, field by field (old → new) |
| **Used by** (1.6.0) | the records of other tables that point to this one, counted per table, with a few examples |
| **Record information** (1.5.0) | who created the record and when, who changed it last and when, as a grid or on one line; nothing to set up |
| **Formatted address** | the address fields laid out after the country, with a map link |
| **Hierarchy** | the breadcrumb of a parent key over several levels, and the children |

Settings on every block: **Show only if (live)**, a condition on another field evaluated while the user types (on a
panel, the hidden fields' values can be cleared); **Show only if (XPath, when opened)**, a predicate on the record
(`./Lifecycle/status = 'ACTIVE'`); **Hide when creating a record**.

A **panel** can also take its fields from a **configuration table** of the dataset: *Fields driven by a configuration
table* names the table, the field of the record that drives (a family), and the table's columns for the family, the
attribute (path or field name) and, optionally, *mandatory*. A field the table lists for a family shows only for that
family, live; a field it never lists always shows.

A **row** can be a **collapsible section** (row inspector): a title, open or collapsed when the form opens.

## 5. The validation report block

It shows the record's own validation report, as EBX computes it from the model's constraints and rules: nothing to
configure on the data side.

- choose the severities shown (errors, warnings, information), the grouping (by severity, by tab, none) and how many
  messages show before "more";
- each message says **where** the field is on this form (*Overview › Loyalty › Points*) and, with *Go to field*,
  opens the right tab and highlights it;
- **action buttons**: a button shows only when a message is about the chosen field, group or association, and opens
  the place where it is fixed (for example *Add an address* on a missing e-mail-invoicing address);
- *Hide the block when the report is empty*.

Messages come in the user's language. A message written in one locale only (a DMA validation rule typed in English)
shows in the instance's default locale, then in English.

## 6. Languages

Every text you type (tab labels, titles, notes, button labels, help texts, field labels) has one value per locale of
the instance. Choose the locale in *Language* and type; a dotted underline marks a text with no translation in the
language shown. *Translations* lists every text of the form with what is missing per locale, and *Only texts to
translate* narrows it. A text missing in a locale shows in the default locale. Model labels and validation messages
are EBX's own and are already translated.

The designer's own screens are in French or English, after the user's locale.

## 7. Save, declare, use

- **Save** writes the layout (JSON) as a new version in `LayoutDesigner/FormLayout`. It shows at the next opening of a
  record, **no restart**, as soon as the table declares the designed form.
- **Activation** (*Activation* in the top bar; the state next to it says whether the table uses the form):
  - model packaged in a module: *Declare the designed form on this table* writes the `recordForm` line in the XSD,
    with a backup; **restart the server once**. *Put the previous form back* undoes it (restart again);
  - DMA model: declare `com.on.ebx.layout.runtime.ConfigurableForm` as the table's form in the model screen (or with
    the `data-model` MCP operation `set_form_layout`) and republish; no restart.
- **</> Code**: the saved JSON, and the same layout as a frozen `UIForm` class for a project that does not want the
  designer at run time (optional).

## 8. For developers: registered components

A module can add its own block. Implement `com.on.ebx.layout.runtime.LayoutComponent` (`getLabel(Locale)`,
`write(UIFormPaneWriter, UIFormContext, JsonNode block, Locale)`) and register it once at start:

```java
LayoutComponents.register("acme.creditScore", new CreditScoreComponent());
```

It then appears in the palette under *Registered components*; the runtime opens a panel with the block's title and
note, and the component writes inside it. The module needs the runtime jar on its compile classpath only (it is on the
server's at run time).

## 9. Limitations

- **DMA models**: the designer cannot write the declaration itself (declare the class in the model screen or with
  `set_form_layout`), nor read it: the state in the top bar then says *DMA model: check the declaration*. The state of a
  module model comes from its XSD file; a declaration applies after the next restart.
- **Go to field** needs a message attached to a field. A rule declared on the table (a constraint across several
  fields) reports on the record as a whole: the message shows, without a location or a link. An action button tied to
  a field therefore never shows for such a rule.
- A **field-level rule** is not evaluated by EBX on an empty field: a rule "e-mail or mobile" belongs on the table.
- The **map** is OpenStreetMap only (an iframe): it needs the user's browser to reach openstreetmap.org.
- A **condition** that cannot be evaluated (a wrong XPath) shows the block and logs a warning in `kernel.log`.
- One layout per table and model: every dataset of the model shares it.
- **Behaviours** run in the browser and change only what is shown: EBX's validation is unchanged.
- The **change history** block needs history activated on the table; *Linked record*, *Hierarchy* and *Link to the
  record* open the target in a new tab.
- Previews of the blocks that read an association's rows are indicative in the designer; the form shows the real values.
- Seen running on EBX 6.3.0-rc2 only, with the locales en-US, fr-FR and it-IT.
