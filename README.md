# Clyde

**Claude, with hands.** An Android assistant that replaces Gemini, driven by Claude on your
Pro/Max **subscription** (no API billing), with supervised device control across four
progressive privilege tiers.

The app is a thin shell (trigger + voice + device control). The brain is the real
**Claude Agent SDK**, authed by `claude login`. They talk over loopback HTTP.

```
You ──assist gesture──▶ App shell ──127.0.0.1:8765──▶ Claude (Agent SDK, your subscription)
                           ▲                                   │
                           │                            calls phone-control tools
                           └────────── speak / act ◀──────────┘
```

## Install (test build)

Grab the signed APK from [**Releases**](../../releases) and sideload it on an **arm64** Android
phone (Android 12+). Two flavours of the brain runtime:

- **Embedded build** *(the release APK)* — the brain runs **in-process**; no Termux, no F-Droid.
  First launch extracts a purpose-built Node runtime, then you do `claude login` once (subscription).
- **Companion build** — a slimmer APK that talks to the brain running in [Termux](termux/) (the
  fallback path). See `termux/setup.sh`.

Then grant the overlay + accessibility permissions the setup wizard asks for, and trigger it with
your phone's assist gesture.

> **The one rule:** never set `ANTHROPIC_API_KEY`. The brain runs on your subscription via
> `claude login`; an API key silently switches to pay-per-token billing, so the brain **refuses to
> start** if one is present.

## The four tiers
- **T0** — Android intents + Termux:API (alarms, calls, SMS, nav, sensors). No special perms.
- **T1** — Accessibility (read screen, tap/type/swipe, screenshots). No root.
- **T2** — Shizuku / `rish` (ADB-level: input, pm, settings, uiautomator). No root.
- **T3** — Root / `su` (inject events, any permission, persistence).

The brain calls `capabilities()` and picks the lowest-friction correct tool per action.
Consequential actions (texts, calls, system changes) require an in-app confirmation that returns a
one-time, args-bound token.

## Mascot
**Clawd** — our own license-clean 8-bit pixel crab, **drawn natively in Compose** (no image/GIF
files; grid source in `design/assets/clawd/gen-sprite.mjs`). It animates per state and ships in every
build with no third-party art bundled.

## Layout
- `app/` — Kotlin + Jetpack Compose Android shell (+ the embedded-runtime extractor/runner).
- `brain/` — TypeScript Claude Agent SDK server.
- `bootstrap/` — builds the embedded Node runtime asset (see `bootstrap/README.md`).
- `design/` — design tokens + our own Clawd sprite source.
- `docs/` — architecture, tool catalog, permissions, build phases.
- `termux/`, `scripts/` — companion setup + build helpers.

## Build from source
- App: `gradlew :app:assembleRelease` (JDK 21). Signing is read from `app/keystore.properties`
  (gitignored — supply your own keystore).
- Brain: `cd brain && npm install && npm run typecheck && npm test`.
- Embedded runtime asset: `bash bootstrap/build.sh` on Linux/Docker (multi-hour; arm64 only).

## Security
The loopback secret is AES-256/GCM-encrypted under an Android-Keystore (TEE) key. Consequential
tools require a single-use confirmation token bound to the exact action + arguments. See
`docs/` and `NOTICE.md` for third-party binary licenses.
