# Clyde

**Claude, with hands.** An Android assistant that replaces Gemini, driven by Claude on your
Pro/Max **subscription** (no API billing), with supervised device control across four
progressive privilege tiers.

The app is a thin shell (trigger + voice + device control). The brain is the real
**Claude Agent SDK** running in Termux, authed by `claude login`. They talk over loopback HTTP.

```
You ──assist gesture──▶ App shell ──127.0.0.1:8765──▶ Claude (Agent SDK, your subscription)
                           ▲                                   │
                           │                            calls phone-control tools
                           └────────── speak / act ◀──────────┘
```

## The four tiers
- **T0** — Android intents + Termux:API (alarms, calls, SMS, nav, sensors). No special perms.
- **T1** — Accessibility (read screen, tap/type/swipe, screenshots). No root.
- **T2** — Shizuku / `rish` (ADB-level: input, pm, settings, uiautomator). No root.
- **T3** — Root / `su` (inject events, any permission, persistence).

The brain calls `capabilities()` and picks the lowest-friction correct tool per action.
Consequential actions (texts, calls, system changes) require an in-app confirmation that
returns a one-time token.

## Layout
- `app/` — Kotlin + Jetpack Compose Android shell.
- `brain/` — TypeScript Claude Agent SDK server (runs in Termux).
- `design/` — design system + mockups (`clyde-mockups.html`).
- `docs/` — architecture, tool catalog, permissions, build phases.
- `termux/`, `scripts/` — setup + build helpers.

## Status
Scaffold + design system done. Building in phases P0→P5 (see `docs/build-phases.md`).

## The one rule
Never set `ANTHROPIC_API_KEY`. The brain runs on your subscription via `claude login`.
Setting an API key silently switches to pay-per-token billing — the brain refuses to start if it's present.
