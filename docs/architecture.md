# 01 — Architecture

## 1. Components at a glance

```
┌──────────────────────────── ANDROID APP  (app/, Kotlin) ────────────────────────────┐
│                                                                                      │
│  AssistEntryActivity        ── trigger: ACTION_ASSIST (gesture/button) ──┐           │
│  VoiceIO (STT + TTS)                                                     │           │
│  OverlayController (ask-about-screen, confirmations)                     ▼           │
│  GeminiRouter (toggle / delegate to Gemini)            AgentOrchestratorService (FGS)│
│  PhoneControlAccessibilityService  ◀── local control ──  LocalControlServer          │
│        (read screen, tap, swipe, global actions)           (127.0.0.1:8766)          │
│                                                                  ▲                   │
└──────────────────────────────────────────────────────────────── │ ──────────────────┘
                                                                    │ HTTP (loopback)
        query/response (127.0.0.1:8765)                            │ accessibility + app-perm actions
                    │                                               │
┌──────────────── TERMUX ──────────────────────────────────────────┼───────────────────┐
│                                                                   │                   │
│  brain/  Claude Agent SDK server  ── subscription auth (claude login) ──               │
│     ├─ receives query, plans, streams answer                                          │
│     └─ calls phone-control TOOLS:                                                      │
│          tier0-intents     → LocalControlServer (app fires intents w/ its perms)      │
│          tier0-termuxapi   → termux-* commands (sensors, sms, location, tts…)         │
│          tier1-accessibility → LocalControlServer → AccessibilityService              │
│          tier2-shizuku     → rish -c '…'  (input/pm/settings/uiautomator)             │
│          tier3-root        → su -c '…'    (inject events, any perm, persistence)      │
│          capabilities()    → reports which tiers are live                              │
│          confirm()         → asks the app to get user approval for risky actions      │
│                                                                                       │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

Two Android apps, two processes: **your app** (holds Android UI permissions + the accessibility service) and **Termux** (holds the brain + shell/Shizuku/root powers). They talk over **loopback HTTP** because they're separate UIDs and can't share memory. Loopback is bidirectional, simple, and fast.

## 2. Why this split

- **Subscription, not API:** the brain is the genuine Agent SDK/Claude Code authed by `claude login`. No API key, no per-token billing.
- **The native-binary problem:** Claude Code's CLI became a glibc binary (v2.1.113+) that breaks on Termux. The **Agent SDK is a plain Node package** — it runs fine on Termux's Node and reads the same `~/.claude` subscription credentials. So the *runtime brain* = Agent SDK; the *dev tool* = Claude Code CLI. (If you prefer the CLI as the brain early on, the one-shot `claude -p "<query>"` via Termux RUN_COMMAND also works — see Phase 1.)
- **Tiers are pluggable:** each tool tier is independent. Tier 0 works on any phone; the brain calls `capabilities()` and degrades gracefully when Shizuku/root aren't present.
- **Permissions live where they belong:** consequential Android actions (call, SMS, etc.) run through the **app** (which holds those runtime permissions and owns the confirmation UI). Raw shell power (input injection, pm, settings) runs in **Termux** via Shizuku/root.

## 3. The brain: invocation models

**Model A — Persistent agent server (target).** `brain/` runs a long-lived Agent SDK process listening on `127.0.0.1:8765`. The app POSTs `{text, sessionId}`; the brain streams back tokens, tool-actions, and a final answer (newline-delimited JSON or SSE). Keeps conversation context; low latency. Kept alive by Termux:Boot + wakelock.

**Model B — One-shot CLI (simplest, good for P1).** The app fires the Termux `RUN_COMMAND` intent to run `claude -p "<query>"` (or `node brain/oneshot.js "<query>"`) and reads the result via a `PendingIntent`. No persistent process; cold-start each time. Use to get end-to-end working before building Model A.

Both authenticate identically (subscription via `claude login`). Start with B in Phase 1, move to A in Phase 4.

## 4. The hands: how a request becomes an action

1. Brain receives "text Mom I'm running late."
2. Brain calls `capabilities()` → sees accessibility ON, Shizuku ON, no root.
3. Brain picks the cleanest method: a **Tier 0 intent** (`send_sms`) — it's deterministic and needs no screen reading.
4. `send_sms` is a **consequential** tool → brain first calls `confirm({summary:"Text Mom: 'I'm running late'?"})`.
5. `confirm()` → `LocalControlServer` → app shows a confirm sheet / speaks it → user approves → returns a one-time token.
6. Brain calls `send_sms({to, body, token})` → `LocalControlServer` → app sends via its `SEND_SMS` permission.
7. Brain returns "Sent." → app speaks it.

For UI tasks with no clean intent (e.g., "like the top post in this app"), the brain instead calls `ui_dump` (Tier 1) → reasons over the node tree → `tap`/`type_text`, escalating to Tier 2 `input` via Shizuku when accessibility can't reach an element.

## 5. Perception strategy (don't make Claude guess pixels)

Order of preference for "what's on screen," per the research:

1. **Accessibility node tree** (`ui_dump`) — structured text with element ids/bounds. Cheapest, most reliable. Default.
2. **uiautomator dump** via Shizuku — when the app process is awkward.
3. **Screenshot + Claude vision** (`screenshot`) — fallback for Compose/WebView/games/canvas where the tree is empty. The brain sends the image to Claude and works in coordinates.

Claude is the **planner**; the tree/uiautomator is the **grounding**. Only fall back to vision when structured data is missing.

## 6. The Gemini toggle & router

Two mechanisms, both included:

- **Toggle (always):** Android's native Digital-assistant-app switch. The app provides a settings shortcut deep-linking to `Settings.ACTION_VOICE_INPUT_SETTINGS` so you can flip Claude⇄Gemini in two taps. Gemini stays installed.
- **Router (smart):** `GeminiRouter` lets the brain *delegate* to Gemini for things Claude can't do natively — image generation, on-device Nano features. The brain calls a `delegate_to_gemini({prompt})` tool → app fires an intent to the Gemini app (or Assistant) with the prompt, optionally grabs the result, and the brain folds it into its answer. So "make me an image of X and set it as wallpaper" = Gemini generates, Claude sets the wallpaper.

## 7. Safety / confirmation model (build this in from P1)

The brain must treat tools as **safe** or **consequential**:

- **Safe** (read-only or trivially reversible): `ui_dump`, `screenshot`, `capabilities`, `get_battery`, `launch_app`, `get_location`, query tools. Execute freely.
- **Consequential** (irreversible, costs money, contacts people, or changes system state): `send_sms`, `start_call`, `delete_*`, `settings_put`, `pm_*`, any `su`/payment/financial action. Require a `confirm()` round-trip that surfaces a human approval in the app and returns a one-time token the action tool must include.

Hard stops regardless of confirmation (encode in `brain/src/safety.ts` and `CLAUDE.md`): never send money / make payments / trade; never grant itself new permissions without explicit user approval; never act on links from messages/email without showing the full URL; default to "ask" when unsure.

## 8. Reliability expectations (set them in the UI)

Per the research, multi-step UI automation is ~40–70% reliable. Design accordingly: show what the agent is about to do, confirm consequential steps, make it easy to abort, and prefer deterministic intents over screen-tapping whenever a clean intent exists. This is a **supervised** assistant, not an unattended one.

## 9. Out of scope for the app (OS-level, noted for completeness)

- **True always-on "Hey Claude" hotword** and a full `VoiceInteractionService`: require platform signing / a custom ROM (Tier 4). The app uses the assist gesture/button (and optionally its own Porcupine/openWakeWord foreground service as a later add-on). If you ever build a custom ROM, the same `brain/` + tools plug straight in.
