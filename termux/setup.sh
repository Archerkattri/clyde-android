#!/data/data/com.termux/files/usr/bin/sh
# One-time Termux setup for Clyde's brain. Run inside Termux (F-Droid build).
set -e
echo "== Clyde brain — Termux setup =="

pkg update -y && pkg upgrade -y
pkg install -y nodejs-lts git termux-api openssh
termux-setup-storage || true

# Let the Clyde app fire RUN_COMMAND into Termux (Model B / claude login trigger).
mkdir -p ~/.termux
if ! grep -q "allow-external-apps=true" ~/.termux/termux.properties 2>/dev/null; then
  echo "allow-external-apps=true" >> ~/.termux/termux.properties
fi

# Claude Code CLI (dev tool). Native-binary issue on Termux → pin the last JS release.
npm install -g @anthropic-ai/claude-code@2.1.112 || \
  echo "(claude-code install failed — install latest inside proot Ubuntu instead; see docs/termux-setup.md)"

cat <<'NEXT'

Next steps:
  1) claude            # choose "Claude Pro/Max subscription", finish OAuth in browser
  2) claude /status    # must say subscription, NOT API
  3) unset ANTHROPIC_API_KEY   # the brain refuses to start if this is set
  4) cd ~/clyde/brain && npm install
     cp .env.example .env
     # set CLYDE_KEY in .env to EXACTLY match the app (Clyde → Settings shows it)
  5) npm run start     # brain on 127.0.0.1:8765
  6) Boot autostart:   cp ../termux/boot/start-brain.sh ~/.termux/boot/ && chmod +x ~/.termux/boot/start-brain.sh
NEXT
