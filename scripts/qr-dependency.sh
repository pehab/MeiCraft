#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
QR_JAR=build/dependencies/zxing-core-3.5.3.jar
QR_SHA=8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82
mkdir -p build/dependencies
if ! printf '%s  %s\n' "$QR_SHA" "$QR_JAR" | sha256sum --check --status 2>/dev/null; then
 curl --fail --location --retry 2 --connect-timeout 20 --max-time 120 \
  https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar -o "$QR_JAR.download"
 printf '%s  %s\n' "$QR_SHA" "$QR_JAR.download" | sha256sum --check --status
 mv "$QR_JAR.download" "$QR_JAR"
fi
