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
| `blocks` | array of columns, each an array of blocks | `[[…left…], […right…]]` |

## Block: common keys

| Key | Meaning |
|---|---|
| `type` | `panel`, `hero`, `facts`, `validation`, `grid`, `map`, `tree`, `duplicates`, `note`, `custom` |
| `id` | unique in the layout |
| `title`, `note` | texts |
| `cond` | XPath predicate on the record; the block shows only when it is true (always shown on creation) |
| `hideCreate` | `true`: not shown when a record is created |

## Block types

**`panel`**: `fields` (array of fields), `ro` (whole panel read only). A field:

| Key | Meaning |
|---|---|
| `p` | path |
| `label` | text replacing the model's label on this form |
| `w` | widget: `line`, `area`, `check`, `yesno`, `select`, `nocreate`, `radio`; read-only display forms `fig`, `badge`, `link`, `mono` |
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

**`duplicates`**: no other key.

**`note`**: `text`.

**`custom`**: `cls` (the name a `LayoutComponent` was registered under); any other key is passed to the component.

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
