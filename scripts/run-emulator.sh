#!/usr/bin/env bash
set -euo pipefail
SDK="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$SDK/platform-tools:$PATH"
API="${TEST_API:-30}"
export ANDROID_USER_HOME="$PWD/build/android-user"
export ANDROID_AVD_HOME="$PWD/build/avd"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
AVD_MANAGER=$(find "$SDK/cmdline-tools" -name avdmanager -type f | sort -V | tail -n 1)
printf 'no\n' | "$AVD_MANAGER" create avd --name meicraft-ci --package "system-images;android-$API;google_apis;x86_64" --device pixel_2 --path "$ANDROID_AVD_HOME/meicraft-ci.avd" --force
printf 'avd.ini.encoding=UTF-8\npath=%s\n' "$ANDROID_AVD_HOME/meicraft-ci.avd" > "$ANDROID_AVD_HOME/meicraft-ci.ini"
python3 - <<'PYAVD'
import os,pathlib
p=pathlib.Path(os.environ['ANDROID_AVD_HOME'])/'meicraft-ci.avd/config.ini'
s=p.read_text();replacements={'hw.lcd.width':'540','hw.lcd.height':'960','hw.lcd.density':'240'}
lines=[]
for line in s.splitlines():
 key=line.split('=',1)[0].strip()
 if key in replacements:line=key+'='+replacements.pop(key)
 lines.append(line)
lines.extend(k+'='+v for k,v in replacements.items());p.write_text('\n'.join(lines)+'\n')
PYAVD
adb start-server
"$SDK/emulator/emulator" -avd meicraft-ci -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader -memory 2048 -cores 2 -camera-back none -camera-front none > build/emulator.log 2>&1 &
EMU_PID=$!
trap 'adb logcat -d -s AndroidRuntime:E > build/android-crash.log 2>/dev/null || true; kill "$EMU_PID" 2>/dev/null || true' EXIT
for n in $(seq 1 120); do
 if ! kill -0 "$EMU_PID" 2>/dev/null; then cat build/emulator.log; exit 1; fi
 if [ "$(timeout 10 adb -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then
  adb shell input keyevent 82
  bash scripts/smoke-android.sh
  exit 0
 fi
 sleep 2
done
cat build/emulator.log
exit 1
