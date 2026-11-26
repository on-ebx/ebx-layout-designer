#!/usr/bin/env bash
# Builds the Layout Designer with the jars of an EBX server project's lib folder only (no Maven):
#   dist/ebx-layout-designer-runtime.jar   the form runtime, for <server>/lib (shared by every module)
#   ebx-layout-designer/target/classes     the designer module's classes and resources
# Usage: EBX_LIB_DIR=<server>/lib ./build.sh
set -euo pipefail

root="$(cd "$(dirname "$0")" && pwd)"
lib="${EBX_LIB_DIR:?set EBX_LIB_DIR to the lib folder of the EBX server project}"
release="${JAVA_RELEASE:-11}"
[[ -f "$lib/ebx-lib.jar" ]] || { printf 'No ebx-lib.jar under %s\n' "$lib" >&2; exit 2; }

java_home="${JAVA_HOME:-}"
if [[ -z "$java_home" && -x /usr/libexec/java_home ]]; then java_home="$(/usr/libexec/java_home 2>/dev/null || true)"; fi
bin="${java_home:+$java_home/bin/}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# runtime
find "$root/runtime/src" -name '*.java' > "$work/runtime.txt"
"${bin}javac" --release "$release" -encoding UTF-8 -nowarn -Xlint:none -cp "$lib/*" -d "$work/runtime" @"$work/runtime.txt"
mkdir -p "$root/dist"
"${bin}jar" --create --file "$root/dist/ebx-layout-designer-runtime.jar" -C "$work/runtime" .

# module (compiled against the runtime just built)
out="$root/ebx-layout-designer/target/classes"
rm -rf "$out"; mkdir -p "$out"
find "$root/ebx-layout-designer/src/main/java" -name '*.java' > "$work/module.txt"
"${bin}javac" --release "$release" -encoding UTF-8 -nowarn -Xlint:none -cp "$lib/*:$work/runtime" -d "$out" @"$work/module.txt"
cp -R "$root/ebx-layout-designer/src/main/resources/." "$out/"
# version stamp read by ModuleVersion (the About page): git describe from a tag, else VERSION; commit; build time (UTC)
version="$(git -C "$root" describe --tags --always --match 'v[0-9]*' 2>/dev/null || true)"
case "$version" in v[0-9]*) version="${version#v}" ;; *) version="$(tr -d '[:space:]' < "$root/VERSION")${version:+-dev+$version}" ;; esac
mkdir -p "$out/com/on/ebx/layout/designer"
printf 'version=%s\ncommit=%s\nbuiltAt=%s\n' "$version" "$(git -C "$root" rev-parse --short HEAD 2>/dev/null || echo unknown)" \
	"$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$out/com/on/ebx/layout/designer/version.properties"

printf 'version: %s\nruntime: %s\nmodule:  %s classes -> %s\n' "$version" "$root/dist/ebx-layout-designer-runtime.jar" "$(find "$out" -name '*.class' | wc -l | tr -d ' ')" "$out"
