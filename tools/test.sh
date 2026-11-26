#!/usr/bin/env bash
# Runs the checks that need no server (role resolution, personal arrangement, explorer queries and permissions), against
# dist/ebx-layout-designer-runtime.jar and the module's classes.
# Usage: EBX_LIB_DIR=<server>/lib tools/test.sh   (build.sh first)
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
lib="${EBX_LIB_DIR:?set EBX_LIB_DIR to the lib folder of the EBX server project}"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
cp="$root/dist/ebx-layout-designer-runtime.jar:$lib/*"
javac -nowarn -cp "$cp" -d "$work" "$root/runtime/test/AudienceTest.java" "$root/runtime/test/SameSecondTest.java" "$root/runtime/test/GeocoderTest.java"
java -cp "$work:$cp" AudienceTest
java -cp "$work:$cp" com.on.ebx.layout.runtime.SameSecondTest
java -cp "$work:$cp" com.on.ebx.layout.runtime.GeocoderTest
# explorers (1.16.0): against the module's classes
mcp="$root/ebx-layout-designer/target/classes:$cp"
javac -nowarn -cp "$mcp" -d "$work" "$root/ebx-layout-designer/src/test/java/com/on/ebx/layout/explorer/ExplorerTest.java"
java -cp "$work:$mcp" com.on.ebx.layout.explorer.ExplorerTest
