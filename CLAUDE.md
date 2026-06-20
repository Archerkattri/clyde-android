# CLAUDE.md — Clyde (Claude-powered Android assistant)

> Repo memory. Claude Code auto-loads this. Keep it updated as the build progresses.

## What this project is
An Android app that replaces Gemini as the system assistant, driven by **Claude running on the user's Pro/Max subscription** (NOT the API). The app is a thin shell (trigger + voice + device control); the brain is the **Claude Agent SDK running in Termux**, authed by `claude login`. They talk over loopback HTTP.

## The non-negotiable rule: subscription, never API
- The brain authenticates ONLY via `claude login` (subscription).
- **Never** read, set, or require `ANTHROPIC_API_KEY`. At brain startup, assert it is unset and refuse to run if present (fail loud).
- Do not build an in-app Anthropic API client. Device intelligence comes from the Agent SDK process, not a homemade client.

## Where this repo is built vs where it runs
- **Built/edited on:** a Windows 11 PC (this machine). Toolchain now installed under `C:\Users\krish\clyde-toolchain` (JDK, Android SDK 36, Gradle), plus Android Studio (build with its JBR 21) and **WSL2 Ubuntu-24.04 + Docker** (for the embedded-runtime bootstrap build). Node 20 + git present. The app DOES compile here (`gradlew :app:assembleDebug` on JBR 21).
- **`brain/` runs in:** Termux on the Android phone (Node). It is `tsc`-buildable on the PC for type-checking.
- **`app/` runs on:** the Android phone. Build the APK with Android Studio or on-device Gradle (see `scripts/`). The PC can edit but not compile it until the Android SDK is installed.

## Packaging: embedded runtime (one sideloaded APK, no F-Droid) — IN PROGRESS
Goal: ship Clyde as ONE sideloaded APK with the brain running in-process — no separate Termux app, no
F-Droid, no manual paste. NOT Google-Play-distributable (W^X exec + bundled GPL binaries) → sideload only.
- A **bespoke** Termux-style bootstrap is built FOR OUR PREFIX (`dev.kris.clyde`) via `bootstrap/build.sh`
  (forked termux-packages in WSL2/Docker; multi-hour Node-from-source compile). Minimal: Node +
  `termux-exec` + ca-certificates, with the esbuild-bundled brain + the JS `@anthropic-ai/claude-code`
  CLI baked in. Output `app/src/main/assets/bootstrap-aarch64.zip` (gitignored; ~110–125 MB; arm64 only).
- App side is **clean-room** (no Termux GPLv3 code → Clyde stays license-flexible; see `NOTICE.md`):
  `runtime/EmbeddedRuntime.kt` extracts the bootstrap on first run; `runtime/BrainRunner.kt` runs `node`
  under `AgentOrchestratorService` with `LD_PRELOAD=libtermux-exec.so` (the W^X workaround). Both GUARDED
  on `EmbeddedRuntime.isBundled()` so builds without the asset keep the external-Termux path working.
- Brain: `CLAUDE_CLI_PATH` points the SDK at the bundled JS CLI. The native CLI is glibc-only — pin the
  JS release (claude-code 2.1.112). `CLAUDE_CODE_OAUTH_TOKEN` is the SUBSCRIPTION headless token (allowed).
- WSL build env: build dir `/opt/clyde-work` chowned to uid **1001** (the container's `builder`);
  `CLYDE_BUILD_WORK=/opt/clyde-work bash bootstrap/build.sh`.
- TODO: finish the bootstrap build; in-app `claude login`/`setup-token` flow (headless); embedded-mode
  setup screen; on-device W^X + end-to-end validation (needs an arm64 phone). The "Termux companion"
  (curl wizard / `termux/setup.sh`) is the FALLBACK for non-embedded builds.

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
- Android: Kotlin, **min SDK 31 / target 36 / compile 36**, single `app` module, Jetpack Compose. Coroutines for IO. No secrets in code.
- Toolchain (June 2026, "everything latest"): **AGP 9.2.0, Gradle 9.6.0, Kotlin + Compose-compiler 2.4.0, JDK 21** (build with Android Studio's JBR 21). AGP-9 compat: `android.newDsl=false` + `android.builtInKotlin=false` keep the standalone Kotlin plugin (removed in AGP 10 → migrate to built-in Kotlin then). compileSdk stays **36** (stable Android 16); API 37 is preview, so the Compose-1.11/lifecycle-2.11 (37-only) releases are intentionally skipped — using Compose BOM 2026.03.00, lifecycle 2.10.0, activity 1.13.0, core-ktx 1.17.0, coroutines 1.11.0, **Coil 3.5.0** (coil3 coordinates). Brain: TypeScript 6.0.3, zod 4.4.3, tsx 4.22.4, @types/node 20.x (Termux Node 20), claude-agent-sdk 0.3.183.
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
