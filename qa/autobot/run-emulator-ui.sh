#!/usr/bin/env bash
# AutoBot-Android (emulator, UI-only) — fast visual/layout regression of Clyde's screens on an
# x86_64 Android emulator, driven by `claude` + mobile-mcp.
#
# WHY a separate mode: Clyde's brain is a standalone arm64 `node` binary. An x86_64 emulator runs
# Clyde's *UI* via ARM translation, but Android's arm64-translation runner CANNOT exec the brain — so
# login/queries/device-control can't be tested here. This mode tests only what RENDERS (see
# test-plan-ui.md). Full end-to-end (brain) needs a real phone (run.sh) or an arm64 emulator.
#
# Two things this does that the phone runner doesn't:
#   1. installs the APK with `--abi arm64-v8a` so the emulator picks Clyde's arm64 libs as primary
#      (an x86_64 emulator otherwise picks x86_64 → empty nativeLibraryDir → app can't find its libs);
#   2. uses the UI-only test plan and best-effort summons the overlay so the ask-bar can be critiqued.
#
# Usage:
#   bash qa/autobot/run-emulator-ui.sh [AVD_NAME] [APK_PATH]
# Both args optional: AVD defaults to the first `emulator -list-avds`; APK auto-detected
# (release preferred, then debug). Requires Node 18+ (mobile-mcp), adb, claude on PATH.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"

# ── locate the SDK tools (PATH first, then the toolchain this repo is built with) ────────────
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/clyde-toolchain/android-sdk}}"
ADB="$(command -v adb || true)";          [ -z "$ADB" ] && ADB="$SDK/platform-tools/adb"
EMU="$(command -v emulator || true)";     [ -z "$EMU" ] && EMU="$SDK/emulator/emulator"
[ -x "$ADB" ] || { echo "✗ adb not found (PATH or $SDK/platform-tools). Install Android platform-tools."; exit 1; }
command -v claude >/dev/null || { echo "✗ claude CLI not found — install it and run 'claude' once to sign in."; exit 1; }
command -v npx    >/dev/null || { echo "✗ npx/node not found — install Node 18+ (mobile-mcp needs it)."; exit 1; }

# ── pick / boot an emulator ──────────────────────────────────────────────────────────────────
RUNNING="$("$ADB" devices | awk 'NR>1 && $1 ~ /^emulator-/ && $2=="device"{print $1; exit}')"
if [ -n "$RUNNING" ]; then
  echo "✓ using already-running emulator: $RUNNING"
else
  [ -x "$EMU" ] || { echo "✗ no running emulator and emulator binary not found ($SDK/emulator). Boot an AVD first."; exit 1; }
  AVD="${1:-$("$EMU" -list-avds | head -1)}"
  [ -n "$AVD" ] || { echo "✗ no AVD found. Create one in Android Studio (an x86_64 image with ARM translation, e.g. API 34/35 google_apis_playstore)."; exit 1; }
  echo "▶ booting emulator AVD '$AVD' (headless)…"
  "$EMU" -avd "$AVD" -no-window -no-audio -no-snapshot-load -no-boot-anim >/dev/null 2>&1 &
  "$ADB" wait-for-device
  echo -n "  waiting for boot"
  for _ in $(seq 1 90); do
    [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    echo -n "."; sleep 2
  done
  echo
  RUNNING="$("$ADB" devices | awk 'NR>1 && $1 ~ /^emulator-/ && $2=="device"{print $1; exit}')"
  [ -n "$RUNNING" ] || { echo "✗ emulator did not come up."; exit 1; }
  echo "✓ emulator up: $RUNNING"
fi
ADBT=("$ADB" -s "$RUNNING")

# ── find the APK ───────────────────────────────────────────────────────────────────────────
APK="${2:-}"
if [ -z "$APK" ]; then
  for c in "$ROOT/app/build/outputs/apk/release/app-release.apk" \
           "$ROOT/app/build/outputs/apk/debug/app-debug.apk"; do
    [ -f "$c" ] && { APK="$c"; break; }
  done
fi
[ -n "$APK" ] && [ -f "$APK" ] || { echo "✗ no APK found. Build one ('./gradlew :app:assembleDebug') or pass a path as arg 2."; exit 1; }
echo "✓ APK: $APK"

# ── install forcing the arm64 ABI (the whole point of this mode) ─────────────────────────────
echo "▶ installing with --abi arm64-v8a (so the emulator uses Clyde's arm64 libs)…"
"${ADBT[@]}" install -r --abi arm64-v8a "$APK"
PKG="$("${ADBT[@]}" shell pm list packages 2>/dev/null | tr -d '\r' | sed 's/^package://' | grep -E '^dev\.kris\.clyde(\.debug)?$' | head -1)"
[ -n "$PKG" ] || { echo "✗ Clyde not present after install."; exit 1; }
echo "✓ installed package: $PKG"

# ── best-effort: grant overlay perm and summon the ask-bar so UI-3 can critique it ───────────
# (SYSTEM_ALERT_WINDOW is an appop; granting it lets the overlay show. The assist activity may
#  redirect to sign-in if not logged in — that's fine, the plan handles a missing overlay.)
"${ADBT[@]}" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || true
"${ADBT[@]}" shell am start -n "$PKG/dev.kris.clyde.assist.AssistEntryActivity" >/dev/null 2>&1 || true
sleep 2

# ── run the UI-only plan ─────────────────────────────────────────────────────────────────────
ALLOW="mcp__mobile__mobile_list_available_devices,mcp__mobile__mobile_use_device,mcp__mobile__mobile_get_screen_size,mcp__mobile__mobile_launch_app,mcp__mobile__mobile_terminate_app,mcp__mobile__mobile_list_apps,mcp__mobile__mobile_list_elements_on_screen,mcp__mobile__mobile_click_on_screen_at_coordinates,mcp__mobile__mobile_double_tap_on_screen,mcp__mobile__mobile_long_press_on_screen_at_coordinates,mcp__mobile__mobile_swipe_on_screen,mcp__mobile__mobile_type_keys,mcp__mobile__mobile_press_button,mcp__mobile__mobile_take_screenshot,mcp__mobile__mobile_get_orientation,mcp__mobile__mobile_set_orientation"

TS="$(date +%Y%m%d-%H%M%S)"
REPORT="$DIR/report-emulator-ui-$TS.md"
PLAN="$(cat "$DIR/test-plan-ui.md")

---
The installed Clyde package on this emulator is: $PKG (use it for mobile_launch_app)."
echo "▶ AutoBot is critiquing Clyde's UI on $RUNNING — final report → $REPORT"
echo

claude -p "$PLAN" \
  --mcp-config "$DIR/.mcp.json" \
  --allowedTools "$ALLOW" \
  --permission-mode default \
  --output-format text \
  | tee "$REPORT"

echo
echo "✓ Done. Report saved: $REPORT"
