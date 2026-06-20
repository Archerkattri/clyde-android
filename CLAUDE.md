# CLAUDE.md — Clyde (Claude-powered Android assistant)

> Repo memory. Claude Code auto-loads this. Keep it updated as the build progresses.

## What this project is
An Android app that replaces Gemini as the system assistant, driven by **Claude running on the user's Pro/Max subscription** (NOT the API). The app is a thin shell (trigger + voice + device control); the brain is the **Claude Agent SDK running in Termux**, authed by `claude login`. They talk over loopback HTTP.

## The non-negotiable rule: subscription, never API
- The brain authenticates ONLY via `claude login` (subscription).
- **Never** read, set, or require `ANTHROPIC_API_KEY`. At brain startup, assert it is unset and refuse to run if present (fail loud).
- Do not build an in-app Anthropic API client. Device intelligence comes from the Agent SDK process, not a homemade client.

## Where this repo is built vs where it runs
- **Built/edited on:** a Windows 11 PC (this machine). Node 20 + git present; no Android SDK/JDK installed here yet.
- **`brain/` runs in:** Termux on the Android phone (Node). It is `tsc`-buildable on the PC for type-checking.
- **`app/` runs on:** the Android phone. Build the APK with Android Studio or on-device Gradle (see `scripts/`). The PC can edit but not compile it until the Android SDK is installed.

## Architecture (see docs/architecture.md)
- `app/` — Kotlin Android shell, **Jetpack Compose + Material 3**. Key components: `AssistEntryActivity` (ACTION_ASSIST trigger), `AgentOrchestratorService` (foreground session owner, hosts `LocalControlServer`), `PhoneControlAccessibilityService` (Tier-1 hands), `VoiceIO`, `OverlayController`/`ConfirmSheet`, `GeminiRouter`, `CapabilityProbe`, `SettingsActivity` (setup wizard + control center).
- `brain/` — TypeScript, runs in Termux. `server.ts` (loopback agent server), `agent.ts` (Agent SDK loop), `tools/` (phone-control tools by tier), `safety.ts`, `capabilities.ts`, `appClient.ts`.

## Integration contract (fixed — don't change without updating both sides)
- Brain server: `127.0.0.1:8765`. App control server: `127.0.0.1:8766`.
- Shared secret header `X-Clyde-Key` (in `brain/.env` and app prefs) on every loopback call.
- App→brain: `POST /query {text,sessionId}` → streamed NDJSON (`status` / `action` / `need_confirm` / `final`).
- Brain→app: endpoints in docs/component-specs.md `LocalControlServer` (`/caps`, `/a11y/*`, `/intent/*`, `/speak`, `/confirm`, `/gemini/delegate`, `/overlay/status`).

## The four control tiers (progressive; brain picks via capabilities())
- T0 intents + Termux:API (no perms) · T1 accessibility (no root) · T2 Shizuku/`rish` (ADB, no root) · T3 root/`su`.
- Tool catalog with JSON schemas: docs/mcp-tool-catalog.md. Only register tools whose tier is live.

## Safety model (implement from P2)
- Tools are `safe` or `consequential`. Consequential = irreversible / contacts people / spends money / changes system state.
- Every consequential tool requires a one-time `token` from `confirm()` (surfaced to the user by the app). Never fabricate tokens.
- Hard stops: no payments/trades/money movement; never grant the app new permissions without explicit user request; always show full URLs before opening links from messages/email; when unsure, ask.

## Design system — "Warm Paper, Live Blue" (see design/clyde-mockups.html)
Built with the **frontend-design** skill. Distinctive, authentic-Claude, anti-AI-slop.
- **Two-color discipline:** Clyde **Blizzard Blue #56C1DE** is identity + LIVE only ("blue is a
  verb": mic, active ask-bar, live rung, focus ring, the one primary CTA, mascot live skin, FAB).
  Claude **Terracotta #D97757** is the "powered by Claude" signature and fills nothing (mark,
  2px answer border, keyword/link, powered-by). Anthropic green **#788C5D** = "verified/done" only.
- **Canvas:** warm paper `#FAF9F5`, ink `#141413`, muted `#73706A`, line `#E8E6DC`. Flat, hairlines, no gradients.
- **Mascot:** **Clawd** (Claude Code's 8-bit pixel crab) — blue when live, orange (warn) on consequence;
  perches ON the box edge / FAB, never circle-bound. App GIFs in `app/src/main/assets/clawd/`
  (clawd-on-desk, AGPL — personal-build only; redraw `design/assets/clawd/gen-sprite.mjs` to ship).
- **Type:** display = Bricolage Grotesque · body = Hanken Grotesk · mono = IBM Plex Mono ·
  **answer voice = Source Serif 4** (the strongest "Claude is talking" cue). Never Inter/Roboto/system.
- **Voice/copy:** active voice, sentence case, end-user terms; no status-pill slop.
- Compose mapping: `app/src/main/java/dev/kris/clyde/ui/` (`ClydeColor`, `ClydeTheme`, `ClydeTypography`)
  + `overlay/ClawdView` (mascot), `overlay/OverlayController` (summon/confirm glass).

## Conventions
- Android: Kotlin, **min SDK 31 / target 35**, single `app` module, Jetpack Compose. Coroutines for IO. No secrets in code.
- Brain: TypeScript strict, small pure tool handlers, all device effects behind `appClient` / `rish` / `su` wrappers.
- Prefer the lowest-friction correct tool: clean intent > accessibility > input-injection > root.

## Build / run
- Brain dev: `cd brain && npm run dev` (tsx) → `127.0.0.1:8765`. Prod: `npm run build && node dist/server.js` via Termux:Boot.
- Brain typecheck on PC: `cd brain && npm install && npm run typecheck`.
- Build APK: `scripts/build-apk.sh` (gradle assembleDebug). Install: `scripts/install-apk.sh`.
- Verify auth: `claude /status` must say subscription.

## Current status (code-complete + compiling; runtime bring-up on device pending)
- [x] Scaffold + design system ("Warm Paper, Live Blue", real Clawd, 12-panel board)
- [x] P0 brain + hello (tsc clean, /healthz, subscription-gated)
- [x] P1 trigger + voice (AssistEntryActivity, AgentOrchestratorService FGS, VoiceIO)
- [x] P2 Tier 0 + safety/confirm (DeviceIntents, brain safety tokens, overlay ConfirmSheet)
- [x] P3 Tier 1 accessibility (PhoneControlAccessibilityService + a11y endpoints/tools)
- [x] P4 Model A streaming (BrainClient.query NDJSON) + Tier 2 (brain rish tools; Shizuku detect)
- [x] P5 router + summon overlay + Clawd; Tier 3 brain tools (su)
- App builds: `gradle :app:assembleDebug` → app-debug.apk. Login → verify → setup → home → assist.
- TODO on device: run the brain in Termux (`claude login`), grant overlay/accessibility, Shizuku
  re-arm; multi-step UI automation is ~40-70% (supervised). Status-chip/minimized-FAB is folded
  into the summon overlay for now.

## Out of scope (don't attempt in the app)
- `BIND_VOICE_INTERACTION` / true always-on hotword / `CAPTURE_AUDIO_HOTWORD` — need platform signing / custom ROM. Use the assist gesture; leave seams only.
