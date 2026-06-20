#!/usr/bin/env bash
# Install the debug APK via adb (or Shizuku's `rish pm install` on-device).
set -e
cd "$(dirname "$0")/.."
APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "Build first: scripts/build-apk.sh"; exit 1; }
if command -v adb >/dev/null 2>&1; then
  adb install -r "$APK"
else
  echo "adb not found. On-device: ./rish -c \"pm install -r $PWD/$APK\""
fi
