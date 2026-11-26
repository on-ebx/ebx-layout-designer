# ON EBX Layout Designer — MCP server

Two things can be designed for any table of any dataset, as JSON documents stored by the Layout Designer:

- the **record form layout** (`ebx-form-layout/1`): tabs, rows, panels of fields, and some forty display blocks. One per
  model and table. Full format: `layout_help(topic: "forms")`.
- **explorers** (`ebx-layout-designer/explorer`): faceted search pages over a table (search, facets, tiles, sorts, groups,
  cards, actions). Several per table, each with an id. Full format: `layout_help(topic: "explorers")`.

Everything here runs as you, and only for administrators (like the designer pages).

## Workflow

1. `layout_describe_table(dataspace, dataset, table)`: the fields (paths, types, enumeration values, foreign key targets),
   groups, associations and their fields, the instance locales, the published workflows and the registered components.
   Use these paths; never guess one. Elements flagged `technical` (Match and Merge's `Association_Reverse`,
   `Linked_Field_*`) are not for display.
2. Start from what exists: `layout_get` (form) or `explorer_list` + `explorer_get` (explorers); for a new form,
   `layout_starter`.
3. Edit the JSON. Every text is an object with one entry per locale of the instance (`{"en-US": "…", "fr-FR": "…"}`).
4. `layout_validate` / `explorer_validate` until `valid` is true. Errors block a save; read the warnings too.
5. `layout_put` / `explorer_put` with `expected_version` = the version you read (0 for a new form), so that a change saved
   meanwhile in the designer is not overwritten. `dry_run: true` checks without writing.
6. The answer gives the address that opens the result (`url`, `designerUrl`). A saved layout or explorer shows at the next
   opening: no restart, no compilation.

## Record form: one more step

A form layout is used only once the model declares the runtime class `com.on.ebx.layout.runtime.ConfigurableForm` on the
table. `activation` in the answers says whether it does and how to do it:
- DMA model: `data-model` MCP server, `execute_operation(set_form_layout, {element_path, class_name})`, then
  `publish_data_model`. No restart.
- module model: the designer's *Activation* button writes the XSD; EBX reads it at its next start (one restart, once).

## What the checks catch that the pages do not say

- A path the table does not have: the page silently skips it.
- An explorer filter EBX cannot evaluate in one query — a sub-field of a media field (`/Logo/attachment`: test `/Logo`),
  a `provided` value (computed in Java). The explorer page drops such a flag, facet or tile with only a line in
  `kernel.log`; `explorer_validate` reports it as an error.
- `hideWhenNone` on a `linked` action defaults to true: write `"hideWhenNone": false` to keep a button showing 0.

## Conventions

- Paths are relative to the record with a leading slash: `/Address/City`; computed values are `derived:<name>` or
  `{"derived": "<name>"}`.
- Dataspace keys are given without the `B` prefix of Data Services.
