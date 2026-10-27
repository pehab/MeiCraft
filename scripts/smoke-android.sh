#!/usr/bin/env bash
set -euo pipefail
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
# The fresh emulator has no user to acknowledge Android's first fullscreen tutorial.
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable
adb shell svc data disable
adb install -r build/baseline.apk
adb install -r build/meiCraft-tests.apk
adb shell am instrument -w -e phase seed de.meiapps.meicraft.tests/de.meiapps.meicraft.SmokeInstrumentation > build/android-update-seed.log
grep -q "PASS: " build/android-update-seed.log
APP_VERSION="$(sed -n 's/^versionName=//p' version.properties)"
adb install -r "build/meiCraft-$APP_VERSION.apk"
adb shell pm grant de.meiapps.meicraft android.permission.CAMERA
adb shell am force-stop de.meiapps.meicraft
adb shell am instrument -w -e phase verify de.meiapps.meicraft.tests/de.meiapps.meicraft.SmokeInstrumentation > build/android-smoke.log
python3 - <<'PY'
from pathlib import Path
import re,base64
s=Path('build/android-smoke.log').read_text()
for label,file in [('screenshot','smoke.png'),('landscapeScreenshot','landscape.png'),('failureScreenshot','failure.png'),('nightScreenshot','night.png'),('homeScreenshot','home.png'),('forgeHeatScreenshot','forge-heat.png'),('forgeReadyScreenshot','forge-ready.png'),('qrScreenshot','qr.png'),('qrReceiverScreenshot','qr-receiver.png'),('qrReceiverReverseScreenshot','qr-receiver-reverse.png')]:
 chunks=re.findall(r'INSTRUMENTATION_STATUS: '+label+r'Chunk=([A-Za-z0-9+/=]+)',s)
 if chunks:Path('build/'+file).write_bytes(base64.b64decode(''.join(chunks)))
if 'PASS: ' not in s or 'INSTRUMENTATION_CODE: -1' not in s or 'INSTRUMENTATION_RESULT: error=' in s:
 print('\n'.join(line for line in s.splitlines() if 'Chunk=' not in line)[:12000]);raise SystemExit('Android smoke test failed')
print(re.search(r'INSTRUMENTATION_RESULT: summary=(.*)',s).group(1))
PY

adb shell am force-stop de.meiapps.meicraft
adb shell am instrument -w -e phase worlds-reopen de.meiapps.meicraft.tests/de.meiapps.meicraft.SmokeInstrumentation > build/android-worlds-reopen.log
python3 - <<'PYREOPEN'
from pathlib import Path
import re,base64
s=Path('build/android-worlds-reopen.log').read_text()
chunks=re.findall(r'INSTRUMENTATION_STATUS: worldsScreenshotChunk=([A-Za-z0-9+/=]+)',s)
if chunks:Path('build/worlds.png').write_bytes(base64.b64decode(''.join(chunks)))
if 'PASS: ' not in s or 'INSTRUMENTATION_CODE: -1' not in s or 'INSTRUMENTATION_RESULT: error=' in s:
 print('\n'.join(line for line in s.splitlines() if 'Chunk=' not in line)[:12000]);raise SystemExit('World restart test failed')
print(re.search(r'INSTRUMENTATION_RESULT: summary=(.*)',s).group(1))
PYREOPEN


for phase in empty-seed empty-reopen; do
 adb shell am force-stop de.meiapps.meicraft
 adb shell am instrument -w -e phase "$phase" de.meiapps.meicraft.tests/de.meiapps.meicraft.SmokeInstrumentation > "build/android-$phase.log"
 python3 - "$phase" <<'PYEMPTY'
from pathlib import Path
import sys,re
s=Path('build/android-'+sys.argv[1]+'.log').read_text()
if 'PASS: ' not in s or 'INSTRUMENTATION_CODE: -1' not in s or 'INSTRUMENTATION_RESULT: error=' in s:
 print(s[:12000]);raise SystemExit('Empty world library test failed')
print(re.search(r'INSTRUMENTATION_RESULT: summary=(.*)',s).group(1))
PYEMPTY
done
