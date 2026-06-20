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

## Design system — "Console" (see design/clyde-mockups.html)
Built with the **frontend-design** skill. Distinctive instrument-panel identity; not a templated default.
- **Signature element:** the **Tier Ladder** — a 4-rung gauge (T0→T3), heat-coded safe→danger, that recurs across onboarding, dashboard, and the confirm/overlay surfaces. It encodes the progressive-tier architecture.
- **Palette (dark phosphor console):** `ink #080D0C`, `ink-2 #0C1413`, `panel #0F1A18`, `line #1E322E`, `text #EAF4EF`, `muted #7E9A93`, `signal #56E6C0` (Clyde's voice/active glow).
- **Tier ramp:** `t0 #3FD08A` (emerald, safe) · `t1 #45B6F0` (sky, accessibility) · `t2 #F4B23E` (amber, ADB) · `t3 #FB5E6D` (rose, root/danger).
- **Type:** display = Bricolage Grotesque · body = Hanken Grotesk · mono/data = IBM Plex Mono. (Never Inter/Roboto/system/Space Grotesk.)
- **Voice/copy:** active voice, sentence case, end-user terms; a button says exactly what it does and keeps that name through the flow. Errors explain what happened + how to fix.
- Compose mapping lives in `app/src/main/java/dev/kris/clyde/ui/` (`ClydeColors`, `ClydeTheme`, `ClydeType`, `TierLadder`).

## Conventions
- Android: Kotlin, **min SDK 31 / target 35**, single `app` module, Jetpack Compose. Coroutines for IO. No secrets in code.
- Brain: TypeScript strict, small pure tool handlers, all device effects behind `appClient` / `rish` / `su` wrappers.
- Prefer the lowest-friction correct tool: clean intent > accessibility > input-injection > root.

## Build / run
- Brain dev: `cd brain && npm run dev` (tsx) → `127.0.0.1:8765`. Prod: `npm run build && node dist/server.js` via Termux:Boot.
- Brain typecheck on PC: `cd brain && npm install && npm run typecheck`.
- Build APK: `scripts/build-apk.sh` (gradle assembleDebug). Install: `scripts/install-apk.sh`.
- Verify auth: `claude /status` must say subscription.

## Current status
- [x] Scaffold + design system ("Console" identity, mockups)
- [ ] P0 brain + hello   - [ ] P1 trigger+voice   - [ ] P2 Tier0   - [ ] P3 Tier1   - [ ] P4 Model A + Tier2   - [ ] P5 router/overlay/root
(Update these as you go.)

## Out of scope (don't attempt in the app)
- `BIND_VOICE_INTERACTION` / true always-on hotword / `CAPTURE_AUDIO_HOTWORD` — need platform signing / custom ROM. Use the assist gesture; leave seams only.
