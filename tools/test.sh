#!/usr/bin/env bash
# Runs the checks that need no server (role resolution, personal arrangement, explorer queries and permissions), against
# dist/ebx-layout-designer-runtime.jar and the module's classes.
# Usage: EBX_LIB_DIR=<server>/lib tools/test.sh   (build.sh first)
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
lib="${EBX_LIB_DIR:?set EBX_LIB_DIR to the lib folder of the EBX server project}"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
cp="$root/dist/ebx-layout-designer-runtime.jar:$lib/*"
javac -nowarn -cp "$cp" -d "$work" "$root/runtime/test/AudienceTest.java" "$root/runtime/test/SameSecondTest.java" "$root/runtime/test/GeocoderTest.java" "$root/runtime/test/RenderCostTest.java" "$root/runtime/test/LayoutStoreCacheTest.java" "$root/runtime/test/RuntimeTextTest.java" "$root/runtime/test/GeoTicketTest.java"
java -cp "$work:$cp" AudienceTest
java -cp "$work:$cp" com.on.ebx.layout.runtime.SameSecondTest
java -cp "$work:$cp" com.on.ebx.layout.runtime.GeocoderTest
# what keeps a form display cheap (1.23.2)
java -cp "$work:$cp" com.on.ebx.layout.runtime.RenderCostTest
# the layout row is searched once per modification of the table (1.23.3)
java -cp "$work:$cp" com.on.ebx.layout.runtime.LayoutStoreCacheTest
# the words the runtime writes come from the RuntimeText bundle, EN/FR/IT, no new literal (1.23.3)
java -Dlayout.root="$root" -cp "$work:$cp" com.on.ebx.layout.runtime.RuntimeTextTest
# the geocoding ticket: forged, altered and expired refused, a good one accepted (1.23.3)
java -cp "$work:$cp" com.on.ebx.layout.runtime.GeoTicketTest
# explorers (1.16.0): against the module's classes
mcp="$root/ebx-layout-designer/target/classes:$cp"
javac -nowarn -cp "$mcp" -d "$work" "$root/ebx-layout-designer/src/test/java/com/on/ebx/layout/explorer/ExplorerTest.java"
java -cp "$work:$mcp" com.on.ebx.layout.explorer.ExplorerTest
# MCP endpoint (1.21.0): transport and tool catalogue
javac -nowarn -cp "$mcp" -d "$work" "$root/ebx-layout-designer/src/test/java/com/on/ebx/layout/designer/LayoutMcpTest.java"
java -cp "$work:$mcp" com.on.ebx.layout.designer.LayoutMcpTest
# button conditions checked again at launch (1.23.1)
javac -nowarn -cp "$mcp" -d "$work" "$root/ebx-layout-designer/src/test/java/com/on/ebx/layout/designer/ActionConditionsTest.java"
java -cp "$work:$mcp" com.on.ebx.layout.designer.ActionConditionsTest
# designer: layout check before saving, activation lock and well-formedness, cache-busting version, coded failures (1.23.3)
javac -nowarn -cp "$mcp" -d "$work" "$root/ebx-layout-designer/src/test/java/com/on/ebx/layout/designer/DesignerStoreTest.java"
java -Dlayout.root="$root" -cp "$work:$mcp" com.on.ebx.layout.designer.DesignerStoreTest
# what keeps a hostile value from becoming markup, script or a query (1.23.3)
javac -nowarn -cp "$mcp" -d "$work" "$root/ebx-layout-designer/src/test/java/com/on/ebx/layout/explorer/EscapingTest.java"
java -Dlayout.root="$root" -cp "$work:$mcp" com.on.ebx.layout.explorer.EscapingTest
