#!/usr/bin/env bash
# Build the Clyde debug APK. Works on a PC with the Android SDK, or on-device.
set -e
cd "$(dirname "$0")/.."
if [ -x ./gradlew ]; then GRADLE=./gradlew; else GRADLE="${GRADLE:-gradle}"; fi
"$GRADLE" :app:assembleDebug "$@"
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
