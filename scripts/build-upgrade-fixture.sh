#!/usr/bin/env bash
set -euo pipefail
cp build/meiCraft-0.1.0.apk build/current-release.apk
MEICRAFT_VERSION_CODE=0 bash build-local.sh
mv build/meiCraft-0.1.0.apk build/baseline.apk
mv build/current-release.apk build/meiCraft-0.1.0.apk
