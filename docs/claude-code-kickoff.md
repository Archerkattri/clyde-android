# 08 — Claude Code kickoff prompts

Paste these into **Claude Code**, run at the repo root, one phase at a time. Each assumes `CLAUDE.md` + `docs/` are present. Review diffs and test on the device before moving on.

## Workflow tips
- Run Claude Code in Termux (`cd ~/clyde && claude`) or on a PC with Android Studio — your choice; both build the same repo.
- Work **one phase per session**; let it write, then you build/test, then continue.
- Keep `CLAUDE.md`'s status checkboxes updated (tell Claude to tick them).
- Use `/status` to confirm you're on the subscription before any brain work.
- After each phase: `git add -A && git commit` (ask Claude to commit with a clear message).

---

## Setup prompt (run once)
```
Read CLAUDE.md and everything in docs/. Summarize the architecture, the integration
contract (ports 8765/8766, X-Clyde-Key, the NDJSON query protocol), the 4 tiers, and the
"subscription not API" rule back to me in 10 bullet points so I can confirm you've got it.
Then scaffold the repo exactly per docs (the app/ and brain/ trees, build.gradle.kts,
package.json, tsconfig, .gitignore, empty stub files with TODOs). Don't implement logic yet.
```

## P0 — brain + hello
```
Implement Phase P0 from docs/build-phases.md.
In brain/: set up the Claude Agent SDK agent loop (agent.ts) and an HTTP server (server.ts)
on 127.0.0.1:8765 with the X-Clyde-Key header and a /healthz route. Implement ONE tool,
get_battery, via `termux-battery-status`. The agent must authenticate via the existing
`claude login` subscription credentials and MUST assert ANTHROPIC_API_KEY is unset at startup
(refuse to run, with a clear message, if it's set). Add npm scripts dev/build/start.
Give me the exact commands to run and a curl test that asks "what's my battery?" and shows
the streamed NDJSON ending in a {"type":"final"} line.
```

## P1 — trigger + voice + one-shot brain
```
Implement Phase P1.
In app/: AssistEntryActivity (ACTION_ASSIST + VOICE_COMMAND, translucent, noHistory),
AgentOrchestratorService (foreground service, types microphone|specialUse), VoiceIO
(SpeechRecognizer STT + TextToSpeech). Wire Model B: TermuxRunCommand fires the
com.termux.RUN_COMMAND intent to run `node ~/clyde/brain/dist/oneshot.js "<query>"` and
receives the result via PendingIntent; speak the result. Implement brain/src/oneshot.ts.
Use the AndroidManifest skeleton from docs/permissions.md. Give me step-by-step:
build the APK (scripts/build-apk.sh), install, set Clyde as the Digital assistant app, and
test by long-pressing home and asking for my battery level. Tick P1 in CLAUDE.md.
```

## P2 — Tier 0 + safety/confirm
```
Implement Phase P2.
App: LocalControlServer on 127.0.0.1:8766 (X-Clyde-Key, loopback only), DeviceIntents for
the Tier-0 intents, ConfirmSheet + a /confirm endpoint that blocks for user approval and
returns a one-time token. Brain: tier0-intents.ts and tier0-termuxapi.ts per
docs/mcp-tool-catalog.md, plus safety.ts (safe vs consequential, token enforcement, hard
stops) and the confirm() tool. capabilities() should report perms + tiers.
Test scripts/prompts for: set a timer, open an app, text a contact (must trigger confirm),
find the nearest pharmacy. Make consequential tools refuse to run without a valid token.
```

## P3 — Tier 1 accessibility
```
Implement Phase P3.
App: PhoneControlAccessibilityService (config in res/xml per docs) exposing dumpTree/tap/
setText/swipe/global/screenshot, surfaced through LocalControlServer /a11y/* endpoints.
Brain: tier1-accessibility.ts (ui_dump, tap, type_text, swipe, global_action, screenshot)
and the perception strategy from docs/architecture.md §5 (tree first; screenshot+vision
fallback when the tree is empty). Handle null root-window with retry/backoff.
Give me the steps to enable the accessibility service (including the Android 13+ "Allow
restricted settings" path) and 3 test prompts that drive a real app's UI.
```

## P4 — persistent brain (Model A) + Shizuku Tier 2
```
Implement Phase P4.
Switch app→brain to BrainClient streaming on 127.0.0.1:8765 with per-session context
(replace Model B as the default; keep oneshot for fallback). Brain: tier2-shizuku.ts using
`rish -c` (input_tap/input_text/input_key, uiautomator_dump, screencap, pm_grant,
settings_put, disable_phantom_killer) gated by capabilities().tier2. Add keep-alive:
termux/boot/start-brain.sh (Termux:Boot + termux-wake-lock) and document disabling the
phantom-process killer via rish. Test multi-turn context, an input_tap fallback, and that
the brain auto-starts after a reboot.
```

## P5 — router, overlay, root, proactive
```
Implement Phase P5.
GeminiRouter: openAssistantPicker() (deep-link ACTION_VOICE_INPUT_SETTINGS = the toggle) and
delegate_to_gemini (fire an intent to the Gemini app for image/Nano, capture result).
OverlayController: ask-about-screen region crop + status bubble. If the device is rooted,
add tier3-root.ts (su_shell, inject_event, make_persistent) gated by capabilities().tier3.
OPTIONAL: WakeWordService (openWakeWord/Porcupine FGS) and a NotificationListener for
proactive triage. Test: "make an image of a fox and set it as wallpaper" (Gemini generates,
Claude sets wallpaper); ask-about-screen; toggle to Gemini and back.
```

## Handy follow-up prompts
```
- "Run the app's unit tests and a lint pass; fix what you can."
- "Add a kill-switch: a notification action + a settings toggle that force-stops the
   orchestrator and invalidates all outstanding confirm tokens."
- "Write docs/RUNBOOK.md: how to start the brain, re-arm Shizuku after reboot, and recover
   if the assistant stops responding."
- "Review tier2/tier3 tools for prompt-injection risk; ensure no consequential shell runs
   without a confirm token and that hard-stops in safety.ts can't be bypassed."
```
