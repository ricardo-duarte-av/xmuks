#!/usr/bin/env bash
# The GitHub Release's notes for tag $1: the release commit's own summary ("Since X.Y.Z: …"), then
# every change since the previous v* tag, one line each. Release commits and housekeeping (formatting,
# lint fixes, re-recorded screenshots) are left out. Paragraphs are unwrapped: GitHub shows every
# newline in a release's notes as a line break.
set -euo pipefail
tag="$1"
previous=$(git describe --tags --abbrev=0 --match 'v*' "$tag^" 2>/dev/null || true)
range=${previous:+$previous..}$tag

git log -1 --format=%b "$tag" | sed '/^Co-Authored-By:/d' |
  awk 'NF { line = line ? line " " $0 : $0; next } line { print line; print ""; line = "" } END { if (line) print line }' |
  sed -e :a -e '/^\n*$/{$d;N;ba' -e '}'
echo
echo "## Changes"
echo
git log --no-merges --reverse --format='- %s' "$range" | grep -viE '^- Release |spotless|detekt|ktlint|golden' || true
