# 06 — Termux setup (the brain, on your subscription)

Goal: Claude running in Termux as the brain, authed with your **Pro/Max subscription** (no API key), reachable by the app on `127.0.0.1:8765`, and kept alive.

## 0. Install the apps (F-Droid / GitHub — NOT Play Store)

- **Termux**, **Termux:API**, **Termux:Boot** (and optionally Termux:Widget) — all from F-Droid so they share a signature.
- **Shizuku** (Play or GitHub) — for Tier 2.
- Your built **Clyde APK** (later, from `scripts/build-apk.sh`).

## 1. Base packages

```bash
pkg update && pkg upgrade -y
pkg install nodejs-lts git termux-api openssh -y
termux-setup-storage
# allow the app to call Termux (Model B):
mkdir -p ~/.termux && echo "allow-external-apps=true" >> ~/.termux/termux.properties
```

## 2. Get Claude + log in with your subscription

Two roles:
- **Dev tool (build the app):** Claude Code CLI.
- **Runtime brain:** Claude Agent SDK (Node package — no native-binary problem).

```bash
# Claude Code CLI (dev tool). Native-binary issue on Termux → use ONE of:
#  (a) pin the last JS version:
npm install -g @anthropic-ai/claude-code@2.1.112
#  (b) OR run latest inside Ubuntu:  pkg install proot-distro && proot-distro install ubuntu
#      then install Claude Code inside the Ubuntu login shell.

# LOG IN WITH SUBSCRIPTION (this is the whole point):
claude            # first run → choose "Claude Pro/Max subscription" → OAuth login
claude /status    # verify it says subscription, NOT API
```

**CRITICAL — keep it on the subscription:**
```bash
# Make sure NO api key is set anywhere, or you'll be billed per-token:
unset ANTHROPIC_API_KEY
grep -rIl ANTHROPIC_API_KEY ~/.bashrc ~/.profile ~/.zshrc 2>/dev/null   # should be empty
```
The Agent SDK reuses the same `~/.claude` credentials from `claude login`, so the brain is subscription-authed too. If logging in via a phone browser is awkward, log in on desktop and copy `~/.claude` over (ssh/Tailscale).

## 3. The brain project

```bash
cd ~/clyde/brain
npm init -y
npm install @anthropic-ai/claude-agent-sdk @modelcontextprotocol/sdk
# server.ts listens on 127.0.0.1:8765; it must NOT set ANTHROPIC_API_KEY.
# Generate the shared loopback secret once and put it in brain/.env AND the app:
node -e "console.log('CLYDE_KEY='+require('crypto').randomBytes(16).toString('hex'))" > .env
npx tsc && node dist/server.js     # or ts-node src/server.ts in dev
curl -s -H "X-Clyde-Key: $(cut -d= -f2 .env)" 127.0.0.1:8765/healthz
```

> The Agent SDK can use your Claude plan (Anthropic: "Use the Claude Agent SDK with your Claude plan"). If you ever prefer the CLI as the brain (Model B), wire `oneshot.ts` to call `claude -p` instead — same subscription auth.

## 4. Shizuku → rish (Tier 2, no root, no PC)

```bash
# Enable Developer Options ▸ Wireless debugging, start Shizuku via wireless debugging (on-device).
# Copy rish + rish_shizuku.dex out of the Shizuku app into Termux (see Shizuku docs / app "Use in terminal apps"):
#   files land in ~/  →  test:
./rish -c 'id'        # expect uid=2000(shell)  → Tier 2 is live
./rish -c 'input tap 500 1000'
```
Shizuku must be re-armed after each reboot (quick wireless-debugging tap; can be automated). Tier 2 tools shell out via `rish -c '…'`.

## 5. Keep the brain alive (essential)

```bash
# Wakelock + disable phantom-process killer (needs Tier 2/Shizuku or root):
termux-wake-lock
./rish -c 'settings put global settings_enable_monitor_phantom_procs false'
./rish -c 'device_config put activity_manager max_phantom_processes 2147483647'
# Disable battery optimization for Termux + Clyde in Android settings.
```

`termux/boot/start-brain.sh` (runs at boot via Termux:Boot):
```bash
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
cd ~/clyde/brain && node dist/server.js >> ~/clyde/brain.log 2>&1 &
```
Put it in `~/.termux/boot/` and make it executable. Run the brain under `tmux` during development so it survives the terminal closing.

## 6. Root (Tier 3, optional)

If the device is rooted (Magisk/KernelSU), the brain's `su -c '…'` path lights up Tier 3 automatically (via `capabilities()`), and you can make the brain a permanent service and inject into secure screens. Accept the Play Integrity / banking tradeoffs (see report §8). Don't root a device you need for tap-to-pay.

## 7. Smoke test the whole loop

```bash
# brain up on 8765, app installed with accessibility ON:
curl -s -H "X-Clyde-Key: $KEY" -d '{"text":"what is my battery level","sessionId":"t1"}' 127.0.0.1:8765/query
# → streamed NDJSON ending in {"type":"final","text":"Your battery is 74% and charging."}
```
