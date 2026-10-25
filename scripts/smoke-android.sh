#!/usr/bin/env bash
set -euo pipefail
adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable
adb shell svc data disable
adb install -r build/baseline.apk
adb install -r build/meiCraft-tests.apk
adb shell am instrument -w -e phase seed de.meiapps.meicraft.tests/de.meiapps.meicraft.SmokeInstrumentation > build/android-update-seed.log
grep -q "PASS: " build/android-update-seed.log
adb install -r build/meiCraft-0.1.0.apk
adb shell am instrument -w -e phase verify de.meiapps.meicraft.tests/de.meiapps.meicraft.SmokeInstrumentation > build/android-smoke.log
python3 - <<'PY'
from pathlib import Path
import re,base64
s=Path('build/android-smoke.log').read_text()
if 'PASS: ' not in s or 'INSTRUMENTATION_CODE: -1' not in s or 'INSTRUMENTATION_RESULT: error=' in s:
 print(s[:12000]);raise SystemExit('Android smoke test failed')
match=re.search(r'INSTRUMENTATION_RESULT: screenshot=([A-Za-z0-9+/=]+)',s)
if match:Path('build/smoke.png').write_bytes(base64.b64decode(match.group(1)))
print(re.search(r'INSTRUMENTATION_RESULT: summary=(.*)',s).group(1))
PY
