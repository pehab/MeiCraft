#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
QR_JAR=build/dependencies/zxing-core-3.5.4.jar
QR_SHA=71de5d89341b5fcf5dd89da7f44e84d825d0e084cdf3ec77c9abe26b0f0ceb13
mkdir -p build/dependencies
if ! printf '%s  %s\n' "$QR_SHA" "$QR_JAR" | sha256sum --check --status 2>/dev/null; then
 curl --fail --location --retry 2 --connect-timeout 20 --max-time 120 \
  https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.4/core-3.5.4.jar -o "$QR_JAR.download"
 printf '%s  %s\n' "$QR_SHA" "$QR_JAR.download" | sha256sum --check --status
 mv "$QR_JAR.download" "$QR_JAR"
fi
