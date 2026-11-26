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
| Palette (left) | the table's **fields** by group (search by label or path; a placed field is marked), its **associations**, the **blocks**, the **registered components** |
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

Text, single-line text, text area, number, date, check box, Yes / No (radio), drop-down list, list without "Create"
(foreign key), radio buttons. Read-only display forms: highlighted figure, pill, link, code (fixed font). EBX's own
widget is used when nothing is chosen.

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

Two settings on every block: **Show only if (XPath)**, a predicate on the record (`./Lifecycle/status = 'ACTIVE'`),
and **Hide when creating a record**.

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

## 9. Limitations (1.0.0)

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
- Seen running on EBX 6.3.0-rc2 only, with the locales en-US, fr-FR and it-IT.
