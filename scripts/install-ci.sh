#!/usr/bin/env bash
# Installs the release APK that CI built for a branch (default: the current one) on the device
# attached over adb — no local build. Usage: scripts/install-ci.sh [branch|run-id]
set -euo pipefail

cd "$(dirname "$0")/.."
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
target="${1:-$(git branch --show-current)}"

if [[ "$target" =~ ^[0-9]+$ ]]; then
  run="$target"
else
  run="$(gh run list --workflow ci.yml --branch "$target" --status success --limit 1 --json databaseId --jq '.[0].databaseId')"
  [[ -n "$run" ]] || { echo "No successful CI run for branch '$target'." >&2; exit 1; }
fi

dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT
echo "Run $run: downloading APKs…"
gh run download "$run" --name apks --dir "$dir"
apk="$(find "$dir" -path '*release*' -name '*.apk' | head -1)"
[[ -n "$apk" ]] || { echo "No release APK in run $run." >&2; exit 1; }

echo "Installing $(basename "$apk")…"
"$ADB" install -r "$apk"
