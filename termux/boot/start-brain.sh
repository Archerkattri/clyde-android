#!/data/data/com.termux/files/usr/bin/sh
# Termux:Boot hook — keep awake + launch the brain on boot.
# Install: cp this to ~/.termux/boot/ and `chmod +x`.
termux-wake-lock

# Subscription only — never bill per token.
unset ANTHROPIC_API_KEY

# Best-effort: keep Android from killing the brain (needs Shizuku rish or root).
if [ -x "$HOME/rish" ]; then
  "$HOME/rish" -c 'settings put global settings_enable_monitor_phantom_procs false' 2>/dev/null
  "$HOME/rish" -c 'device_config put activity_manager max_phantom_processes 2147483647' 2>/dev/null
fi

cd "$HOME/clyde/brain" || exit 1
npm run start >> "$HOME/clyde/brain.log" 2>&1 &
