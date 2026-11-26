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

## Tab

| Key | Type | Meaning |
|---|---|---|
| `label` | text | tab title |
| `count` | array of paths | associations whose rows are summed into the title ("Orders (5)") |
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
| `type` | `panel`, `hero`, `facts`, `validation`, `grid`, `map`, `tree`, `hours`, `duplicates`, `note`, `custom`; since 1.4.0 `timeline`, `history`, `completeness`, `stepper`, `checklist`, `chart`, `aggregates`, `pivot`, `linked`, `contacts`, `gallery`, `documents`, `links`, `validity`, `address`, `hierarchy` |
| `id` | unique in the layout |
| `title`, `note` | texts |
| `cond` | XPath predicate on the record; the block shows only when it is true (always shown on creation) |
| `hideCreate` | `true`: not shown when a record is created |
| `showIf` | a live condition (see *Behaviours*): the block shows or hides while the user types, without saving |
| `onHide` | on a panel with `showIf`: `keep` (default) or `clear` the values of its fields when it hides |

## Block types

**`panel`**: `fields` (array of fields), `ro` (whole panel read only). A field:

| Key | Meaning |
|---|---|
| `p` | path |
| `label` | text replacing the model's label on this form |
| `w` | widget: `line`, `area`, `check`, `yesno`, `select`, `nocreate`, `radio`; read-only display forms `fig`, `badge`, `link`, `mono`, and the display widgets of 1.4.0 (below) |
| `showIf`, `mandatoryIf`, `readOnlyIf`, `onHide` | behaviours (below) |
| `ro` | read only (a computed field always is) |
| `wide` | label above, full width |

**`hero`**: `title` (a path here, not a text), `sub` (paths), `chips` (paths), `meter` (path of a 0–100 value),
`status` (path).

**`facts`**: `items` (paths), `count` (path of an association whose rows are counted).

**`validation`**: `sev` (`{"error": true, "warning": true, "info": true}`), `group` (`severity`, `tab`, `none`), `max`
(messages before "more"), `links` (Go to field), `hideEmpty`, `actions` (array of `{"on": <path>, "btn": <text>,
"go": <path>}`: a button labelled `btn` shown when a message is about `on`, opening `go`), `covers` (paths of fields
this block stands for).

**`grid`**: `a` (association path), `style` (`native` or `cards`), `cardFields` (paths shown on each card), `addBtn`
(text).

**`map`**: `lat`, `lng` (paths), `tall` (larger map).

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
| `history` | `fields` (paths followed; empty: all), `max` (8), `author` (true). Reads `AdaptationTable.getHistory()`: history must be activated on the table, else the block says so |
| `completeness` | `fields: [{p, weight}]` (empty: every field placed on the form), `threshold` (80, %), `listMissing` (true, with "Go to field") |
| `stepper` | `field` (status), `steps` (codes in order; empty: the enumeration's values), `exits` (codes shown in red outside the steps), `labels: {code: text}` (optional) |
| `checklist` | `items: [{label, when, go}]`; `when` is `{"filled": path}`, a condition `{"field", "in"…}` or `{"xpath": "./Price > 0"}`; `go` (optional) the field opened by "Go to field" |
| `chart` | `a`, `by` (field of the rows), `measure: {fn: count / sum / avg, field}`, `shape` (`bars` or `donut`), `top` (6; the rest grouped as "Other") |
| `aggregates` | `a`, `items: [{label, fn: sum / avg / min / max / count, field, format: number / money / percent, currency, decimals}]` |
| `pivot` | `a`, `rows`, `cols` (fields of the rows), `value: {field}` (empty: a tick when the pair exists) |
| `linked` | `fk` (foreign key of the record), `fields` (paths of the target table), `open` (true: link to the record, `getURLForSelection`) |
| `contacts` | `a`, `name` (default: row label), `role`, `email`, `phone`, `main` (boolean: shown first, highlighted) |
| `gallery` | `a` + `media` + `caption`, and/or `fields` (media or URL fields of the record), `perRow` (4) |
| `documents` | `a`, `file` (media or URL field), `name`, `docType`, `date`, `expiry` (true: a past date shows red) |
| `links` | `items: [{label, url, sameTab}]`; `${./path}` in `url` inserts the record's value, URL-encoded; a button whose value is empty is hidden; a `url` that is only `${./path}` (a website field) is used as it is, `https://` added when missing; only `http(s):`, `mailto:`, `tel:` and `/` addresses |
| `validity` | `from`, `to` (date fields), `warnDays` (30): upcoming, in force, ending soon, expired |
| `address` | `lines` (street paths), `number` (street number, put on the first line; `numberPlace`: `auto` = after the street for DE, AT, CH, NL, BE, IT, ES and the other countries that write it so, before elsewhere; `numberBefore`; `numberAfter`), `zip`, `city`, `region`, `country`, `format` (`auto` by country, `zipFirst`, `cityFirst`, `zipAfter`), `mapLink` (true) |
| `hierarchy` | `parent` (foreign key), `up` (the parent's own parent key when the target is another table; same path by default on a self-referencing table), `label`, `depth` (6), `children` (association) |

A media field is read through the Digital Asset Manager's public API (`MediaType.getAttachment`,
`AssetManager.getDigitalAsset`, `DigitalAsset.getURL`), called by reflection so the form works without the add-on; a text
field holding an `http(s)` or `/` address works as well.

Tones (`color.map`, `colors`): `ok` (green), `warn` (orange), `bad` (red), `accent` (purple), `info` (blue), `muted` (grey).

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
