#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-../android-sdk}"
SDK="$(cd "$SDK" && pwd)"
BT="$SDK/build-tools/36.0.0"
JAVA_BIN="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}/bin"
mkdir -p build/test-apk/classes build/test-apk/dex build/test-assets
bash scripts/qr-dependency.sh
QR_JAR=build/dependencies/zxing-core-3.5.3.jar
APP_VERSION="$(sed -n 's/^versionName=//p' version.properties)"
cp "build/meiCraft-$APP_VERSION.apk" build/test-assets/current.apk
"$BT/aapt2" link -I "$SDK/platforms/android-37.0/android.jar" --manifest tests/AndroidManifest.xml -A build/test-assets -o build/test-apk/resources.apk
"$JAVA_BIN/java" -m jdk.compiler/com.sun.tools.javac.Main -Xlint:deprecation -Werror -encoding UTF-8 -source 17 -target 17 -classpath "$SDK/platforms/android-37.0/android.jar:build/classes:$QR_JAR" -d build/test-apk/classes tests/SmokeInstrumentation.java
python3 - <<'PY'
import zipfile,pathlib
with zipfile.ZipFile('build/test-apk/classes.jar','w') as z:
 for p in pathlib.Path('build/test-apk/classes').rglob('*.class'):z.write(p,p.relative_to('build/test-apk/classes'))
PY
"$BT/d8" --lib "$SDK/platforms/android-37.0/android.jar" --classpath build/classes.jar --classpath "$QR_JAR" --min-api 26 --output build/test-apk/dex build/test-apk/classes.jar
cp build/test-apk/resources.apk build/test-apk/unsigned.apk
python3 - <<'PY'
import zipfile,pathlib
with zipfile.ZipFile('build/test-apk/unsigned.apk','a') as z:
 for p in pathlib.Path('build/test-apk/dex').glob('*.dex'):z.write(p,p.name)
PY
"$BT/zipalign" -f -p 4 build/test-apk/unsigned.apk build/test-apk/aligned.apk
"$BT/apksigner" sign --ks .signing/keystore.p12 --ks-pass file:.signing/password --out build/meiCraft-tests.apk build/test-apk/aligned.apk

