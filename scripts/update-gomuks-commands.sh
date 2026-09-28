#!/usr/bin/env bash
# Regenerates core/data/src/main/resources/gomuks-commands.json — gomuks' built-in command specs
# (MSC4391 format) — from a gomuks checkout, the way gomuks web builds its stdcommands.json.
# Usage: scripts/update-gomuks-commands.sh [path/to/gomuks]   (default ~/gomuks)
set -euo pipefail
cd "$(dirname "$0")/.."
GOMUKS="${1:-$HOME/gomuks}"
out="core/data/src/main/resources/gomuks-commands.json"
# gomuks may need a newer Go than the system's: let Go fetch the toolchain it asks for.
(cd "$GOMUKS" && GOTOOLCHAIN=auto go run ./pkg/hicli/cmdspec/print -) | python3 -c 'import json,sys; json.dump(json.load(sys.stdin), sys.stdout, indent=1, ensure_ascii=False)' > "$out"
echo "Wrote $out ($(git -C "$GOMUKS" rev-parse --short HEAD))"
