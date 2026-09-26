#!/usr/bin/env bash
# The same checks CI runs. Exits non-zero on any failure, so `scripts/gate.sh && git commit …`
# can't commit a red tree.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest :core:protocol:test verifyRoborazziDebug \
  :app:assembleRelease -PwarningsAsErrors=true --console=plain "$@"
scripts/check-jni-mapping.sh app/build/outputs/mapping/release/mapping.txt
