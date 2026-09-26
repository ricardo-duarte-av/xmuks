#!/usr/bin/env bash
# Fails if R8 renamed any member of a class whose native code binds to Java names by string.
# Usage: scripts/check-jni-mapping.sh app/build/outputs/mapping/release/mapping.txt
set -euo pipefail
mapping="$1"
jni_prefixes=("com.github.luben.zstd.")
bad=$(awk -v prefixes="${jni_prefixes[*]}" '
  BEGIN { n = split(prefixes, p, " ") }
  /^[^ ]/ { in_jni = 0; for (i = 1; i <= n; i++) if (index($1, p[i]) == 1) in_jni = 1; cls = $1; next }
  in_jni && /^    [^0-9]/ {
    # "    type name -> newName"  (fields; methods carry "(" and line ranges)
    split($0, parts, " -> ")
    k = split(parts[1], lhs, " "); name = lhs[k]; sub(/\(.*/, "", name)
    if (name != parts[2] && name != "<init>" && name != "<clinit>") print cls "." name " -> " parts[2]
  }' "$mapping")
if [ -n "$bad" ]; then
  echo "R8 renamed JNI-bound members (add keep rules):"; echo "$bad"; exit 1
fi
echo "JNI-bound classes kept intact."
