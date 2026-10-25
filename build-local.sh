#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-../android-sdk}"
SDK="$(cd "$SDK" && pwd)"
BT="$SDK/build-tools/36.0.0"
JAVA_BIN="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}/bin"
rm -rf build/classes build/dex
mkdir -p build/classes build/dex .signing
APP_VERSION="$(sed -n 's/^versionName=//p' version.properties)"
"$BT/aapt2" compile --dir app/src/main/res -o build/resources.zip
python3 - <<'PYMANIFEST'
from pathlib import Path
import os
version=dict(line.split('=',1) for line in Path('version.properties').read_text().splitlines() if '=' in line)
code=int(os.environ.get('MEICRAFT_VERSION_CODE',version['versionCode']))
assert 0<=code<2147483647
s=Path('app/src/main/AndroidManifest.xml').read_text().replace('<manifest ', '<manifest package="de.meiapps.meicraft" android:versionCode="'+str(code)+'" android:versionName="'+version['versionName']+'" ',1)
s=s.replace('<uses-feature ', '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="37"/><uses-feature ',1)
Path('build/AndroidManifest.xml').write_text(s)
PYMANIFEST
"$BT/aapt2" link -I "$SDK/platforms/android-37.0/android.jar" --manifest build/AndroidManifest.xml --java build/generated -A app/src/main/assets -o build/resources.apk build/resources.zip
"$JAVA_BIN/java" -m jdk.compiler/com.sun.tools.javac.Main -Xlint:deprecation -Werror -encoding UTF-8 -source 17 -target 17 -classpath "$SDK/platforms/android-37.0/android.jar" -d build/classes $(find app/src/main/java build/generated -name '*.java')
python3 - <<'PY'
import zipfile,pathlib
with zipfile.ZipFile('build/classes.jar','w') as z:
 for p in pathlib.Path('build/classes').rglob('*.class'): z.write(p,p.relative_to('build/classes'))
PY
"$BT/d8" --lib "$SDK/platforms/android-37.0/android.jar" --min-api 26 --output build/dex build/classes.jar
cp build/resources.apk build/unsigned.apk
python3 - <<'PY'
import zipfile,pathlib
with zipfile.ZipFile('build/unsigned.apk','a') as z:
 for p in pathlib.Path('build/dex').glob('*.dex'):z.write(p,p.name)
PY
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [ ! -f .signing/keystore.p12 ]; then
 python3 -c 'import secrets,pathlib;pathlib.Path(".signing/password").write_text(secrets.token_hex(24))'
 "$JAVA_BIN/keytool" -genkeypair -keystore .signing/keystore.p12 -storetype PKCS12 -storepass:file .signing/password -alias meicraft -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=meiCraft, OU=meiApps, O=Private Development, C=DE"
fi
"$BT/apksigner" sign --ks .signing/keystore.p12 --ks-pass file:.signing/password --out "build/meiCraft-$APP_VERSION.apk" build/aligned.apk
"$BT/apksigner" verify --verbose "build/meiCraft-$APP_VERSION.apk"
