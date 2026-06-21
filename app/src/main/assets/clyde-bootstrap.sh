#!/data/data/com.termux/files/usr/bin/bash
# Clyde brain bootstrap. Fetched from the Clyde app over loopback and run ONCE inside Termux.
# The Clyde app shows the exact command, e.g.:
#   curl -fsS -H "X-Clyde-Key: KEY" http://127.0.0.1:8766/bootstrap.sh | bash -s -- KEY
set -e
KEY="${1:-}"
APP="http://127.0.0.1:8766"
CLYDE_DIR="$HOME/clyde"
LOG="$CLYDE_DIR/bootstrap.log"
mkdir -p "$CLYDE_DIR"
: > "$LOG"

# Fail loud: on any error, name the step and show the tail of the log so the user isn't left guessing
# (and the Clyde app's "waiting for the brain…" isn't mistaken for a silent install failure).
step="startup"
fail() {
  echo
  echo "✗ Clyde bootstrap failed during: $step"
  echo "  last lines of the log:"
  tail -n 12 "$LOG" 2>/dev/null | sed 's/^/  | /'
  echo "  Fix the issue above and paste the command again. Full log:  cat ~/clyde/bootstrap.log"
  exit 1
}
trap fail ERR

echo "== Clyde brain bootstrap =="
if [ -z "$KEY" ]; then
  echo "!! missing CLYDE_KEY argument — copy the exact command from the Clyde app."
  exit 1
fi

step="installing packages"
echo "-> installing packages (nodejs-lts, git, termux-api, curl)…"
pkg update -y >>"$LOG" 2>&1 || true
pkg install -y nodejs-lts git termux-api curl >>"$LOG" 2>&1

# Allow the Clyde app to drive Termux afterwards (RUN_COMMAND requires this).
mkdir -p "$HOME/.termux"
if ! grep -q "allow-external-apps=true" "$HOME/.termux/termux.properties" 2>/dev/null; then
  echo "allow-external-apps=true" >> "$HOME/.termux/termux.properties"
fi

step="fetching the brain"
echo "-> fetching the brain from the Clyde app…"
curl -fsS -H "X-Clyde-Key: $KEY" "$APP/brain.tgz" -o "$CLYDE_DIR/brain.tgz" 2>>"$LOG"
tar -xzf "$CLYDE_DIR/brain.tgz" -C "$CLYDE_DIR" 2>>"$LOG"
rm -f "$CLYDE_DIR/brain.tgz"

step="installing brain dependencies"
echo "-> installing brain dependencies…"
cd "$CLYDE_DIR/brain"
npm install --no-audit --no-fund >>"$LOG" 2>&1

# Write the shared loopback secret so the app and brain authenticate to each other.
[ -f .env ] || touch .env
if grep -q '^CLYDE_KEY=' .env; then
  sed -i "s/^CLYDE_KEY=.*/CLYDE_KEY=$KEY/" .env
else
  echo "CLYDE_KEY=$KEY" >> .env
fi

# The subscription brain. The native CLI has no Termux build, so pin the last JS release.
step="installing the Claude CLI"
echo "-> installing the Claude CLI…"
if ! npm install -g @anthropic-ai/claude-code@2.1.112 >>"$LOG" 2>&1; then
  echo "(claude CLI install failed — proot fallback in termux/rish-setup.md; details:  cat ~/clyde/bootstrap.log)"
fi

# Auto-start the brain on boot (Termux:Boot).
mkdir -p "$HOME/.termux/boot"
cat > "$HOME/.termux/boot/start-brain.sh" <<'BOOT'
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
unset ANTHROPIC_API_KEY
cd "$HOME/clyde/brain" || exit 1
npm run start >> "$HOME/clyde/brain.log" 2>&1 &
BOOT
chmod +x "$HOME/.termux/boot/start-brain.sh"

trap - ERR
cat <<'NEXT'

✓ Brain installed. Two one-time steps remain:
  1) claude          # choose "Claude Pro/Max subscription", finish login in the browser
  2) npm run start   # starts the brain on 127.0.0.1:8765, then return to the Clyde app
The Clyde app turns the brain status green as soon as it's reachable.
NEXT
