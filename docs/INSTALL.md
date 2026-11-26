# Installing the Layout Designer

## What the release holds

| File | Goes to | Role |
|---|---|---|
| `ebx-layout-designer.war` | `<tomcat>/webapps` | the module: dataset `LayoutDesigner`, the designer and the *Record form layouts* page |
| `server-lib/ebx-layout-designer-runtime.jar` | the folder that holds `ebx.jar` / `ebx-lib.jar` (Tomcat's `common.loader`) | `ConfigurableForm`, the record form every data model can declare |
| `server-lib/if-missing/jackson-*.jar` | same folder, **only if it has no Jackson** | JSON library used by both pieces |
| `docs/` | — | this guide, the user guide, the layout format, a Tomcat context sample |

Keep the file names: the war's name is the module's context path (`/ebx-layout-designer`, the `publicPath` of its
`module.xml`), and keeping the runtime jar's name lets an upgrade simply replace it.

## Requirements

- An EBX whose web applications use the Jakarta servlet API (Tomcat 10 or later). Seen running on **EBX 6.3.0-rc2**;
  the code calls the public EBX Java API only, nothing of 6.3 in particular.
- Java 11 or later.
- `jackson-annotations`, `jackson-core` and `jackson-databind` 2.x on the server classpath. A FastTrack server has them in
  its `lib`; a plain EBX distribution does not (`ebx.jar` embeds a relocated copy the module cannot use): copy the three
  jars of `server-lib/if-missing/` (2.14.2, Apache License 2.0). Never put a second version next to an existing one.
- An administrator account: the services are for administrators only.

## Steps

1. Stop the server.
2. Copy `server-lib/ebx-layout-designer-runtime.jar` next to the EBX jar of the server (`<tomcat>/lib` on a standard
   install, `<server project>/lib` on a FastTrack workspace).
3. Check for Jackson in that folder (`ls <lib> | grep jackson-databind`). If nothing answers, copy the three jars of
   `server-lib/if-missing/` there.
4. Copy `ebx-layout-designer.war` into `<tomcat>/webapps`. To run the module from a source folder instead, see
   `tomcat-context.xml`.
5. Start the server. `kernel.log` then shows, at the first start only:
   ```
   [layout-designer] dataspace LayoutDesigner created
   [layout-designer] dataset LayoutDesigner created
   ```
   followed by one `layout … loaded` line per seed layout whose model exists on this instance (the seed is the
   FastTrack Companies form; it is skipped elsewhere).

Integrity: `SHA256SUMS` lists every file of the release (`shasum -a 256 -c SHA256SUMS`).

## Opening the tools

- **Designer of one table**: open the table, **Actions › Design the record form**. As a web component:
  `/ebx/?branch=<dataspace>&instance=<dataset>&xpath=/root/<Table>&service=ebx-layout-designer@LayoutDesigner`.
- **Record form layouts** (the list of layouts and the start of a new one): dataspace and dataset `LayoutDesigner`,
  table *Form layouts*, **Actions › Record form layouts**. In a perspective, add an action with service
  `ebx-layout-designer@LayoutsHome` and the parameters `branch = LayoutDesigner`, `instance = LayoutDesigner`,
  `xpath = /root/FormLayout` (it is what the *Configuration* section of the MultiDomain perspective holds).

## Making a table use its designed form

A layout is used only once the table declares the runtime class as its record form:

```xml
<osd:table>
    ...
    <recordForm osd:class="com.on.ebx.layout.runtime.ConfigurableForm"/>
</osd:table>
```

- **Model packaged in a module** (an XSD file): the designer's *Activation* panel writes this line itself,
  keeps a copy of the XSD next to it (`.bak-<time>-layout-designer`) and remembers the class it replaced, to put it back
  with *Deactivate*. EBX reads the XSD at start: **restart once**. The module's folder must be reachable from the
  server (a Tomcat context in `conf/Catalina/localhost` or an exploded war in `webapps`).
- **DMA model**: declare the class as the table's form in the model screen, or with the `data-model` MCP operation
  `set_form_layout`, then republish. No restart.

From then on, every save in the designer shows at the next opening of a record. A table that declares the class but has
no active layout shows the standard EBX form.

## Upgrade

Stop the server, replace the war and the runtime jar, start. Layouts are data (`LayoutDesigner/FormLayout`) and are kept.
If the exploded folder `webapps/ebx-layout-designer/` stays, delete it so that Tomcat unpacks the new war.

## Uninstall

1. For each table that declares the form: *Deactivate* in the designer (module model) or remove the declaration (DMA
   model), so the table goes back to its previous form.
2. Stop the server, remove the war, its exploded folder and the runtime jar (and the Jackson jars if you added them).
3. The dataspace `LayoutDesigner` stays; close it if the layouts are no longer wanted.

A table that still declares `ConfigurableForm` once the runtime jar is gone will not compile: do step 1 first.
