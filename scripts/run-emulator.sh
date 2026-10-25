#!/usr/bin/env bash
set -euo pipefail
SDK="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$SDK/platform-tools:$PATH"
API="${TEST_API:-30}"
AVD_MANAGER=$(find "$SDK/cmdline-tools" -name avdmanager -type f | sort -V | tail -n 1)
printf 'no\n' | "$AVD_MANAGER" create avd --name meicraft-ci --package "system-images;android-$API;google_apis;x86_64" --device pixel_2 --force
adb start-server
"$SDK/emulator/emulator" -avd meicraft-ci -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader -memory 2048 -cores 2 -camera-back none -camera-front none > build/emulator.log 2>&1 &
EMU_PID=$!
trap 'kill "$EMU_PID" 2>/dev/null || true' EXIT
for n in $(seq 1 120); do
 if [ "$(timeout 10 adb -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then
  adb shell input keyevent 82
  bash scripts/smoke-android.sh
  exit 0
 fi
 sleep 2
done
cat build/emulator.log
exit 1
