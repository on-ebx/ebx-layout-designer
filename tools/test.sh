#!/usr/bin/env bash
# Runs the checks that need no server (role resolution, personal arrangement), against dist/ebx-layout-designer-runtime.jar.
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
