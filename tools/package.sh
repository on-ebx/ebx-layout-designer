#!/usr/bin/env bash
# Builds a release of the Layout Designer into dist/ (git-ignored, nothing built is committed):
#   ebx-layout-designer-<version>.zip          what is shared: the war, the runtime jar, Jackson for a server without it,
#                                              the documentation, the release notes and SHA256SUMS of all of it
#   ebx-layout-designer-<version>.zip.sha256
# Usage: EBX_LIB_DIR=<server>/lib tools/package.sh
# ALLOW_DIRTY=1 packages a working tree with uncommitted changes (the version then ends in -dirty).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
lib="${EBX_LIB_DIR:?set EBX_LIB_DIR to the lib folder of the EBX server project}"
command -v zip >/dev/null 2>&1 || { echo "package.sh needs the zip command." >&2; exit 2; }

version="$(tr -d '[:space:]' < "$root/VERSION")"
if [[ -n "$(git -C "$root" status --porcelain --untracked-files=no)" ]]; then
	[[ "${ALLOW_DIRTY:-0}" == "1" ]] || { echo "Uncommitted changes: commit them, or set ALLOW_DIRTY=1." >&2; exit 1; }
	version="$version-dirty"
fi
commit="$(git -C "$root" rev-parse HEAD)"
name="ebx-layout-designer-$version"

java_home="${JAVA_HOME:-}"
if [[ -z "$java_home" && -x /usr/libexec/java_home ]]; then java_home="$(/usr/libexec/java_home 2>/dev/null || true)"; fi
jar_bin="${java_home:+$java_home/bin/}jar"
sha256() { if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$@"; else sha256sum "$@"; fi; }

work="$(mktemp -d "${TMPDIR:-/tmp}/layout-package.XXXXXX")"
trap 'rm -rf "$work"' EXIT

printf '== help pages\n'
python3 "$root/tools/build_help.py" --check

printf '== compile\n'
EBX_LIB_DIR="$lib" bash "$root/build.sh"

manifest="$work/MANIFEST.MF"
{
	printf 'Manifest-Version: 1.0\n'
	printf 'Implementation-Title: ebx-layout-designer\n'
	printf 'Implementation-Version: %s\n' "$version"
	printf 'Implementation-Build: %s\n' "$commit"
} > "$manifest"

stage="$work/$name"
mkdir -p "$stage/server-lib/if-missing" "$stage/docs"

printf '== checks\n'
"${java_home:+$java_home/bin/}javac" --release 11 -cp "$root/dist/ebx-layout-designer-runtime.jar:$lib/*" -d "$work/checks" "$root/runtime/test/com/on/ebx/layout/runtime/OpeningHoursCheck.java" "$root/runtime/test/com/on/ebx/layout/runtime/BarcodeCheck.java" "$root/runtime/test/com/on/ebx/layout/runtime/FamilyCheck.java"
"${java_home:+$java_home/bin/}java" -cp "$root/dist/ebx-layout-designer-runtime.jar:$lib/*:$work/checks" com.on.ebx.layout.runtime.OpeningHoursCheck
"${java_home:+$java_home/bin/}java" -cp "$root/dist/ebx-layout-designer-runtime.jar:$lib/*:$work/checks" com.on.ebx.layout.runtime.BarcodeCheck
"${java_home:+$java_home/bin/}java" -cp "$root/dist/ebx-layout-designer-runtime.jar:$lib/*:$work/checks" com.on.ebx.layout.runtime.FamilyCheck

printf '== runtime jar\n'
mkdir -p "$work/runtime"
(cd "$work/runtime" && "$jar_bin" xf "$root/dist/ebx-layout-designer-runtime.jar")
rm -rf "$work/runtime/META-INF"
"$jar_bin" cfm "$stage/server-lib/ebx-layout-designer-runtime.jar" "$manifest" -C "$work/runtime" .

printf '== war\n'
war="$work/war"
mkdir -p "$war/WEB-INF/classes"
cp -R "$root/ebx-layout-designer/src/main/webapp/." "$war/"
cp -R "$root/ebx-layout-designer/target/classes/." "$war/WEB-INF/classes/"
find "$war" -name '.DS_Store' -type f -delete
"$jar_bin" cfm "$stage/ebx-layout-designer.war" "$manifest" -C "$war" .

printf '== Jackson (only for a server whose lib has none)\n'
for j in annotations core databind; do
	src="$lib/jackson-$j.jar"
	[[ -f "$src" ]] || { echo "No $src: cannot ship Jackson." >&2; exit 1; }
	v="$(unzip -p "$src" META-INF/MANIFEST.MF | tr -d '\r' | sed -n 's/^Bundle-Version: //p')"
	cp "$src" "$stage/server-lib/if-missing/jackson-$j-$v.jar"
done

printf '== documentation\n'
cp "$root/README.md" "$root/RELEASE-NOTES.md" "$root/LICENSE" "$stage/"
cp "$root/docs/"*.md "$stage/docs/"
cp "$root/docs/tomcat-context.xml" "$stage/docs/"

(cd "$stage" && find . -type f ! -name SHA256SUMS | sort | sed 's|^\./||' | while read -r f; do sha256 "$f"; done > SHA256SUMS)

mkdir -p "$root/dist"
rm -f "$root/dist/$name.zip" "$root/dist/$name.zip.sha256"
(cd "$work" && zip -qr "$root/dist/$name.zip" "$name")
(cd "$root/dist" && sha256 "$name.zip" > "$name.zip.sha256")
printf 'release: %s\n' "$root/dist/$name.zip"
unzip -l "$root/dist/$name.zip"
