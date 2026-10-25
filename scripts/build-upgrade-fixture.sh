#!/usr/bin/env bash
set -euo pipefail
SDK="${ANDROID_SDK_ROOT:-../android-sdk}"
# Exercise the real 0.2.0 binary with this build's test signing identity.
# Production updates keep the original project key; CI never publishes its temporary key.
"$SDK/build-tools/36.0.0/apksigner" sign --ks .signing/keystore.p12 --ks-pass file:.signing/password --out build/baseline.apk dist/meiCraft-0.2.0.apk
