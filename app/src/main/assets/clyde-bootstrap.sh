#!/data/data/com.termux/files/usr/bin/bash
# Clyde brain bootstrap. Fetched from the Clyde app over loopback and run ONCE inside Termux.
# The Clyde app shows the exact command, e.g.:
#   curl -fsS -H "X-Clyde-Key: KEY" http://127.0.0.1:8766/bootstrap.sh | bash -s -- KEY
set -e
KEY="${1:-}"
APP="http://127.0.0.1:8766"
CLYDE_DIR="$HOME/clyde"

echo "== Clyde brain bootstrap =="
if [ -z "$KEY" ]; then
  echo "!! missing CLYDE_KEY argument — copy the exact command from the Clyde app."
  exit 1
fi

echo "-> installing packages (nodejs-lts, git, termux-api, curl)…"
pkg update -y >/dev/null 2>&1 || true
pkg install -y nodejs-lts git termux-api curl >/dev/null 2>&1

# Allow the Clyde app to drive Termux afterwards (RUN_COMMAND requires this).
mkdir -p "$HOME/.termux"
if ! grep -q "allow-external-apps=true" "$HOME/.termux/termux.properties" 2>/dev/null; then
  echo "allow-external-apps=true" >> "$HOME/.termux/termux.properties"
fi

echo "-> fetching the brain from the Clyde app…"
mkdir -p "$CLYDE_DIR"
curl -fsS -H "X-Clyde-Key: $KEY" "$APP/brain.tgz" -o "$CLYDE_DIR/brain.tgz"
tar -xzf "$CLYDE_DIR/brain.tgz" -C "$CLYDE_DIR"
rm -f "$CLYDE_DIR/brain.tgz"

echo "-> installing brain dependencies…"
cd "$CLYDE_DIR/brain"
npm install --no-audit --no-fund

# Write the shared loopback secret so the app and brain authenticate to each other.
[ -f .env ] || touch .env
if grep -q '^CLYDE_KEY=' .env; then
  sed -i "s/^CLYDE_KEY=.*/CLYDE_KEY=$KEY/" .env
else
  echo "CLYDE_KEY=$KEY" >> .env
fi

# The subscription brain. The native CLI has no Termux build, so pin the last JS release.
echo "-> installing the Claude CLI…"
npm install -g @anthropic-ai/claude-code@2.1.112 >/dev/null 2>&1 || \
  echo "(claude CLI install failed — see termux/rish-setup.md for the proot fallback)"

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

cat <<'NEXT'

✓ Brain installed. Two one-time steps remain:
  1) claude          # choose "Claude Pro/Max subscription", finish login in the browser
  2) npm run start   # starts the brain on 127.0.0.1:8765, then return to the Clyde app
The Clyde app turns the brain status green as soon as it's reachable.
NEXT
