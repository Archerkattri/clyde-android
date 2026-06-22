#!/usr/bin/env bash
# AutoBot-Android — autonomously test Clyde on a connected phone with `claude` + mobile-mcp.
# Run from any shell with adb + claude + node 22+ on PATH (git-bash, WSL, macOS, Linux).
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── preflight ─────────────────────────────────────────────────────────────
command -v adb    >/dev/null || { echo "✗ adb not found — install Android platform-tools and add to PATH."; exit 1; }
command -v claude >/dev/null || { echo "✗ claude CLI not found — install it and run 'claude' once to sign in."; exit 1; }
command -v npx    >/dev/null || { echo "✗ npx/node not found — install Node 18+ (mobile-mcp needs it)."; exit 1; }

DEVICES="$(adb devices | awk 'NR>1 && $2=="device"{print $1}')"
if [ -z "$DEVICES" ]; then
  echo "✗ No authorized device. Plug in your phone, enable USB debugging, and accept the on-phone prompt."
  echo "  adb devices:"; adb devices
  exit 1
fi
echo "✓ device(s): $DEVICES"
adb shell pm list packages 2>/dev/null | grep -q "dev.kris.clyde" \
  && echo "✓ Clyde (dev.kris.clyde) is installed" \
  || echo "⚠ dev.kris.clyde NOT found on the device — install the Clyde APK first, or AutoBot will report it."

# Only the mobile-mcp tools are allowed — AutoBot drives the phone, nothing else. (Server-level
# "mcp__mobile" also works on recent CLIs; the explicit list is the safe default.)
ALLOW="mcp__mobile__mobile_list_available_devices,mcp__mobile__mobile_use_device,mcp__mobile__mobile_get_screen_size,mcp__mobile__mobile_launch_app,mcp__mobile__mobile_terminate_app,mcp__mobile__mobile_list_apps,mcp__mobile__mobile_list_elements_on_screen,mcp__mobile__mobile_click_on_screen_at_coordinates,mcp__mobile__mobile_double_tap_on_screen,mcp__mobile__mobile_long_press_on_screen_at_coordinates,mcp__mobile__mobile_swipe_on_screen,mcp__mobile__mobile_type_keys,mcp__mobile__mobile_press_button,mcp__mobile__mobile_take_screenshot,mcp__mobile__mobile_get_orientation,mcp__mobile__mobile_set_orientation"

TS="$(date +%Y%m%d-%H%M%S)"
REPORT="$DIR/report-$TS.md"
echo "▶ AutoBot is driving the phone — watch the screen. Final report → $REPORT"
echo

claude -p "$(cat "$DIR/test-plan.md")" \
  --mcp-config "$DIR/.mcp.json" \
  --allowedTools "$ALLOW" \
  --permission-mode default \
  --output-format text \
  | tee "$REPORT"

echo
echo "✓ Done. Report saved: $REPORT"
