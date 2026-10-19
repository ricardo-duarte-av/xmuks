#!/usr/bin/env bash
# CI's formatting (ktlint, as spotless runs it) and detekt checks, without Gradle: run before every
# push (the Gradle gate, scripts/gate.sh, is for release tags; it's too heavy for this box's RAM).
# Exits non-zero on any finding, so `scripts/check.sh && git push` can't push a red tree.
# --fix lets ktlint rewrite what it can first.
set -euo pipefail
cd "$(dirname "$0")/.."

tools="${XDG_CACHE_HOME:-$HOME/.cache}/xmuks-tools"
ktlint_version=$(sed -n 's/^ktlint = "\(.*\)"/\1/p' gradle/libs.versions.toml)
detekt_version=$(sed -n 's/^detekt = "\(.*\)"/\1/p' gradle/libs.versions.toml)
rules_version=$(sed -n 's/^composeRules = "\(.*\)"/\1/p' gradle/libs.versions.toml)
ktlint="$tools/ktlint-$ktlint_version"
detekt="$tools/detekt-cli-$detekt_version-all.jar"
rules="$tools/compose-rules-detekt-$rules_version.jar"
rules_common="$tools/compose-rules-common-detekt-$rules_version.jar"

fetch() {
  [ -s "$2" ] && return
  mkdir -p "$tools"
  curl -sSfL "$1" -o "$2.part" && mv "$2.part" "$2"
}
fetch "https://github.com/pinterest/ktlint/releases/download/$ktlint_version/ktlint" "$ktlint"
chmod +x "$ktlint"
fetch "https://repo1.maven.org/maven2/dev/detekt/detekt-cli/$detekt_version/detekt-cli-$detekt_version-all.jar" "$detekt"
fetch "https://repo1.maven.org/maven2/io/nlopez/compose/rules/detekt/$rules_version/detekt-$rules_version.jar" "$rules"
fetch "https://repo1.maven.org/maven2/io/nlopez/compose/rules/common-detekt/$rules_version/common-detekt-$rules_version.jar" \
  "$rules_common"

fix=()
[ "${1:-}" = "--fix" ] && fix=(-F)
status=0

# Sources and build scripts as spotless's `kotlin` and `kotlinGradle` targets.
"$ktlint" "${fix[@]}" --relative '**/src/**/*.kt' '**/*.gradle.kts' '!**/src/test/**' '!**/build/**' || status=1
# Tests as spotless's `kotlinTest`: the same rules with line length off (inline JSON fixtures).
# ktlint takes that only from an .editorconfig, so they're checked as a copy under one that says so
# (and, with --fix, copied back).
copy=$(mktemp -d)
trap 'rm -rf "$copy"' EXIT
sed 's/^max_line_length = .*/max_line_length = off/' .editorconfig > "$copy/.editorconfig"
mapfile -t tests < <(git ls-files '*/src/test/*.kt')
cp --parents "${tests[@]}" "$copy"
(cd "$copy" && "$ktlint" "${fix[@]}" --relative '**/src/test/**/*.kt') || status=1
if [ ${#fix[@]} -gt 0 ]; then
  for f in "${tests[@]}"; do cmp -s "$copy/$f" "$f" || cp "$copy/$f" "$f"; done
fi

# detekt as the root project configures it: every module's main and test sources, not build-logic.
inputs=()
while read -r dir; do inputs+=(--input "$dir"); done < <(
  find . -path ./build-logic -prune -o -path '*/build' -prune -o -type d \
    \( -path '*/src/*/kotlin' -o -path '*/src/*/java' \) -print
)
java -jar "$detekt" --build-upon-default-config --config config/detekt/detekt.yml --plugins "$rules:$rules_common" \
  --parallel "${inputs[@]}" || status=1

exit $status
