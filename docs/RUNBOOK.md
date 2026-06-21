# Clyde — runbook

How to build, run, and recover Clyde. (Living doc; grows with the app.)

## Build the app (APK)
- On a PC with Android SDK, or on-device:
  ```
  scripts/build-apk.sh          # → app/build/outputs/apk/debug/app-debug.apk
  scripts/install-apk.sh        # adb install -r  (or rish pm install on-device)
  ```
- This dev machine uses a portable toolchain at `C:\Users\krish\clyde-toolchain`
  (JDK 17 + Android SDK 35 + Gradle 8.10.2). `local.properties` points at it.
  Build directly: `JAVA_HOME=…/jdk …/gradle/bin/gradle :app:assembleDebug`.

## Run the brain (in Termux, on your subscription)
1. `termux/setup.sh` once (installs node, claude-code, allow-external-apps).
2. `claude` → sign in with Pro/Max subscription → `claude /status` says subscription.
3. `cd ~/clyde/brain && npm install && cp .env.example .env`
   - Set `CLYDE_KEY` to EXACTLY match the app's key.
   - Never set `ANTHROPIC_API_KEY` (the brain refuses to start if present).
4. `npm run start` → brain on `127.0.0.1:8765`. Verify: `curl 127.0.0.1:8765/healthz`.
5. Boot autostart: copy `termux/boot/start-brain.sh` into `~/.termux/boot/` (chmod +x).

## First run (app)
1. Open Clyde → **Login**: "Open Termux & start sign-in" runs `claude login`.
2. **Verify** polls the brain: brain reachable · subscription · no API key → Continue.
3. Setup (next screens): auto-detects root/custom-ROM, else Basic / Full-control.

## Tier 2 (Shizuku) — see termux/rish-setup.md
Re-arm after reboot (wireless-debugging tap). Brain degrades to Tier 0/1 if absent.

## Recover
- Assistant unresponsive → check brain: `curl 127.0.0.1:8765/healthz`; restart `npm run start`.
- Kill switch: `curl -X POST -H "X-Clyde-Key: <key>" 127.0.0.1:8765/kill` (invalidates confirm tokens).
- Brain won't start, says API key set → `unset ANTHROPIC_API_KEY` and relaunch.

## Status
See CLAUDE.md "Current status" checkboxes.
