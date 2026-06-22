# AutoBot-Android — auto-test Clyde on a real phone

An AutoBot-style tester for Clyde: the **`claude` CLI drives your phone** through
[`mobile-mcp`](https://github.com/mobile-next/mobile-mcp) (taps, types, screenshots over ADB),
runs the flows in [`test-plan.md`](test-plan.md), critiques each screen, and writes a
`report-<timestamp>.md`. No manual tapping — you watch it work.

It drives a live device over ADB. There are **two modes**:

| Mode | Script | Tests | Needs |
|---|---|---|---|
| **Full E2E** | `run.sh` | UI **+ brain** (sign-in, queries, device control) | a **real arm64 phone** (or an arm64 emulator) |
| **UI-only** | `run-emulator-ui.sh` | **UI rendering only** (layout, fonts, Clawd, the ask-bar) | a fast **x86_64 emulator** |

Why the split: Clyde's brain is a standalone **arm64 `node` binary**. An x86_64 emulator runs Clyde's
*UI* fine via ARM translation, but Android's arm64-translation runner **cannot exec the brain** — so
login/queries/device-control can't be tested on an x86_64 emulator. Use the UI-only mode for fast
visual regression there, and the full E2E mode on a real phone (or a slow arm64 emulator) for the brain.

## Prerequisites
1. **Node 18+** (mobile-mcp requires it): `node -v`.
2. **Android platform-tools** (`adb`) on your PATH. From the Android SDK, or `brew install android-platform-tools`.
3. **`claude` CLI**, signed in: run `claude` once and complete login (your Pro/Max subscription).
4. **Clyde installed on the phone** (v0.1.26+), and **already signed in** inside Clyde (see caveat below).
5. **Phone in developer mode** with **USB debugging ON**, plugged in via USB. Then:
   ```
   adb devices        # must list your phone as "device" (accept the on-phone RSA prompt)
   ```

## Run
```bash
bash qa/autobot/run.sh
```
(Windows: use **git-bash** or **WSL** — both can reach `adb`. The script is plain bash.)

It prints a live note, drives the phone (watch the screen), and on finish saves + prints
`qa/autobot/report-<timestamp>.md`.

## What it tests (see test-plan.md)
- **F1** App launches & every screen renders cleanly (UI critique).
- **F2** Summon the popup and **type** a question → gets an answer (v0.1.25 typing).
- **F3** **Mic button re-listens** instead of closing; speech stops (v0.1.25).
- **F4** **"open YouTube Music and play Let Down by Radiohead"** → actually drives the app (v0.1.26).
- **F5** A built-in action (set a timer).

Each flow gets a verdict (✅ PASS / ⚠️ PARTIAL / ❌ FAIL / ⛔ BLOCKED) with quoted on-screen text, plus a
**Bugs/friction** list and **Environment notes**.

## Caveats (read these)
- **Sign-in can't be automated.** Clyde's login is a browser OAuth (approve in Chrome). Sign in **by hand
  once** before running; AutoBot will mark the login-dependent flows BLOCKED if it isn't signed in.
- **The power-button assist gesture can't be triggered** by ADB cleanly, so AutoBot summons the popup via
  the in-app **"Ask Clyde"** button instead.
- **Plumbing is validated (on an emulator).** The mobile-mcp tool names, the `--allowedTools` list, and the
  `claude -p` driving loop are confirmed working — the UI-only emulator mode ran end-to-end and critiqued
  Clyde's screens. The physical-phone `run.sh` uses the same plumbing; only phone-specific behavior is
  still unverified from the build machine. If anything differs on your mobile-mcp version the script
  prints the error — tell me and I'll adjust (or try `--permission-mode bypassPermissions` for a fully
  autonomous run).
- It only allows the **mobile-mcp** tools — it can tap/type/screenshot your phone, nothing on your computer.

## Tuning
- Edit [`test-plan.md`](test-plan.md) to add/inspect flows — it's just instructions to the agent.
- Re-run anytime; each run is timestamped. Diff reports across builds to catch regressions.
