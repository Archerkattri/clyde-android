# Claude Android Assistant — Build Handoff Packet

**Goal:** build an Android app that replaces Gemini as your phone assistant, driven by **Claude on your subscription** (no API billing), with full device control across 4 privilege tiers, and a one-tap toggle back to Gemini.

This packet is everything Claude Code needs to build it. Read this page, then work top-to-bottom.

---

## The one idea that makes this free on your subscription

You are **not** building a Claude client. A client needs an API key = pay-per-token.

Instead, the app is a **thin shell** (trigger + voice + device-control), and the **brain is the real Claude Agent SDK / Claude Code running in Termux, logged in with your Pro/Max subscription**. The shell hands a request to that brain over a localhost socket; the brain reasons and calls **phone-control tools** to act; the answer comes back and is spoken.

```
You ──trigger──▶ App shell ──localhost:8765──▶ Claude (Agent SDK in Termux, your subscription)
                    ▲                                    │
                    │                              calls phone-control tools
                    └──────── speak / act ◀──────────────┘
```

**Hard rule:** never set `ANTHROPIC_API_KEY` anywhere in the runtime. That env var silently overrides subscription auth and bills you. Auth only via `claude login` (subscription). See `06-termux-setup.md`.

**Honest caveat:** powering an always-on custom app from a personal subscription lives in a gray zone of Anthropic's usage terms. Driving the official Claude Code / Agent SDK with your login is the defensible interpretation; don't extract OAuth tokens for a homemade client. Keep usage personal and reasonable; this could change with policy updates.

---

## What you're building (3 layers)

| Layer | What | Where it lives |
|---|---|---|
| **Trigger** | Assist gesture/button → voice in, TTS out, Gemini toggle | `app/` (Kotlin) |
| **Brain** | Claude Agent SDK as a localhost agent server, subscription auth | `brain/` (TypeScript, runs in Termux) |
| **Hands** | Phone-control tools across 4 tiers (intents → accessibility → Shizuku → root) | `brain/src/tools/` + `app/` accessibility service |

The four device-control tiers are **progressive** — the app detects what's available (accessibility on? Shizuku? root?) and the brain picks the best method per action:

- **Tier 0** — Android intents + Termux:API (alarms, calls, texts, nav, launch apps, sensors). No special perms.
- **Tier 1** — Accessibility service (read screen, tap/type/swipe, screenshots). No root.
- **Tier 2** — Shizuku / `rish` (ADB-level: `input`, `pm`, `settings`, `uiautomator`). No root.
- **Tier 3** — Root / `su` (inject into secure screens, any permission, persistent service).

(Tier 4 — custom ROM for a true always-on hotword — is out of scope for the app; it's an OS build, noted in `01-architecture.md`.)

---

## How to use this packet

1. **`01-architecture.md`** — the full design, data flows, and the safety/confirmation model. Read first.
2. **`02-repo-structure.md`** — the exact folder layout to create.
3. **`03-component-specs.md`** — class-by-class spec of the app shell and the brain.
4. **`04-mcp-tool-catalog.md`** — every phone-control tool, with JSON schemas, grouped by tier. This is the "hands."
5. **`05-permissions-and-manifest.md`** — the `AndroidManifest.xml` skeleton + permission table + gotchas.
6. **`06-termux-setup.md`** — get Claude Code + the brain running in Termux on your subscription.
7. **`07-build-phases.md`** — build in 6 testable phases (P0→P5). Don't build it all at once.
8. **`08-claude-code-kickoff.md`** — the actual prompts to paste into Claude Code, per phase.
9. **`CLAUDE.md`** — drop this at the repo root; Claude Code auto-loads it as project memory.

---

## Quickstart (the 10-minute version)

1. On the phone: install **Termux + Termux:API + Termux:Boot** (F-Droid, not Play Store). Install **Shizuku**. See `06-termux-setup.md`.
2. In Termux: install Node, `claude login` with your subscription, scaffold the repo with `CLAUDE.md`.
3. Open Claude Code in Termux at the repo root, paste **Phase 0 prompt** from `08-claude-code-kickoff.md`.
4. Build phases in order, testing each on the device. Set the app as **Settings ▸ Apps ▸ Default apps ▸ Digital assistant app** when Phase 1 lands.

---

## Defaults you can change

- App name: **Clyde** · package: `dev.kris.clyde` (rename throughout).
- Brain port: `127.0.0.1:8765`. App control port: `127.0.0.1:8766`.
- Brain runtime: **Claude Agent SDK (TypeScript)** — chosen because it dodges the Claude Code native-binary issue on Termux and is built for embedding. Claude Code CLI (one-shot `claude -p`) is the simpler fallback for early phases.
- Stack: Kotlin + Gradle (app), Node/TypeScript (brain).
