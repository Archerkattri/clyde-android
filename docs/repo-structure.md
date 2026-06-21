# 02 — Repo structure

Create this layout at the repo root. Claude Code will fill in the files during the build phases.

```
clyde/                                   # rename to your app name
├─ CLAUDE.md                             # repo memory — Claude Code auto-loads (provided in packet)
├─ README.md                             # short human readme
├─ .gitignore
├─ docs/                                 # copy the handoff packet here for Claude Code to reference
│   ├─ architecture.md                   # = 01-architecture.md
│   ├─ mcp-tool-catalog.md               # = 04-mcp-tool-catalog.md
│   ├─ permissions.md                    # = 05-permissions-and-manifest.md
│   └─ build-phases.md                   # = 07-build-phases.md
│
├─ app/                                  # ── ANDROID SHELL (Kotlin + Gradle) ──
│   ├─ build.gradle.kts
│   ├─ src/main/AndroidManifest.xml
│   └─ src/main/java/dev/kris/clyde/
│       ├─ assist/
│       │   └─ AssistEntryActivity.kt           # ACTION_ASSIST entry; transparent; starts a session
│       ├─ service/
│       │   ├─ AgentOrchestratorService.kt      # foreground service; session owner; talks to brain
│       │   ├─ PhoneControlAccessibilityService.kt  # Tier-1 hands: read tree, tap, swipe, global, screenshot
│       │   └─ WakeWordService.kt               # OPTIONAL (later phase): Porcupine/openWakeWord FGS
│       ├─ bridge/
│       │   ├─ LocalControlServer.kt            # embedded HTTP on 127.0.0.1:8766 (app's action API)
│       │   ├─ BrainClient.kt                   # talks to brain on 127.0.0.1:8765 (Model A)
│       │   └─ TermuxRunCommand.kt              # fires Termux RUN_COMMAND intent (Model B fallback)
│       ├─ voice/
│       │   ├─ SpeechRecognizerIO.kt            # STT (Android SpeechRecognizer; swappable for Vosk)
│       │   └─ TextToSpeechIO.kt                # TTS (Android TextToSpeech; swappable for Piper)
│       ├─ overlay/
│       │   ├─ OverlayController.kt             # ask-about-screen crop + status bubble
│       │   └─ ConfirmSheet.kt                  # consequential-action approval UI + token
│       ├─ router/
│       │   └─ GeminiRouter.kt                  # toggle deep-link + delegate_to_gemini
│       ├─ intents/
│       │   └─ DeviceIntents.kt                 # Tier-0 intent helpers (alarm/timer/call/sms/nav/calendar)
│       ├─ caps/
│       │   └─ CapabilityProbe.kt               # detect accessibility/Shizuku/root/overlay state
│       ├─ settings/
│       │   └─ SettingsActivity.kt              # setup wizard, permission grants, brain status
│       └─ util/Json.kt, Log.kt, Prefs.kt
│   └─ src/main/res/xml/
│       ├─ accessibility_service_config.xml     # canRetrieveWindowContent, canPerformGestures
│       └─ shortcuts.xml
│
├─ brain/                                # ── CLAUDE BRAIN (TypeScript, runs in Termux) ──
│   ├─ package.json                      # @anthropic-ai/claude-agent-sdk, mcp sdk, ws/http
│   ├─ tsconfig.json
│   ├─ src/
│   │   ├─ server.ts                     # localhost:8765 agent server (Model A)
│   │   ├─ oneshot.ts                    # `node oneshot.ts "<query>"` for Model B
│   │   ├─ agent.ts                      # Agent SDK query loop + system prompt + tool wiring
│   │   ├─ appClient.ts                  # calls app's LocalControlServer (127.0.0.1:8766)
│   │   ├─ capabilities.ts               # tier detection (asks app + probes rish/su)
│   │   ├─ safety.ts                     # safe vs consequential; confirm() gating; hard stops
│   │   └─ tools/
│   │       ├─ index.ts                  # registers all tools; filters by live capabilities
│   │       ├─ tier0-intents.ts          # set_alarm, start_call, send_sms, navigate_to, launch_app…
│   │       ├─ tier0-termuxapi.ts        # get_battery, get_location, clipboard, notify, torch, sensors…
│   │       ├─ tier1-accessibility.ts    # ui_dump, tap, type_text, swipe, global_action, screenshot
│   │       ├─ tier2-shizuku.ts          # sh, input_tap, pm_grant, settings_put, uiautomator_dump
│   │       ├─ tier3-root.ts             # su_sh, inject_event, make_persistent
│   │       └─ gemini.ts                 # delegate_to_gemini
│   └─ system-prompt.md                  # the brain's operating instructions (mirrors CLAUDE.md safety)
│
├─ termux/                               # ── SETUP ──
│   ├─ setup.sh                          # installs node, agent sdk, claude login, rish, deps
│   ├─ boot/start-brain.sh               # Termux:Boot → wakelock + launch brain server
│   └─ rish-setup.md                     # copy rish + rish_shizuku.dex from Shizuku
│
└─ scripts/
    ├─ build-apk.sh                      # gradle assembleDebug (on-phone or PC)
    └─ install-apk.sh                    # adb/Shizuku install
```

## Module notes

- **`app/`** is a single-module Gradle Android project (min SDK 31 / Android 12, target latest). Pure Kotlin, no Compose required (plain Views are fine for the tiny UI; Compose optional).
- **`brain/`** is a standalone Node/TypeScript project that runs **inside Termux**, not bundled in the APK. It's the subscription-authed agent. Keep it in the same git repo for convenience.
- **`docs/`** — copy the relevant packet files in so Claude Code can read the contracts while coding. `CLAUDE.md` references them.
- Loopback ports are the integration contract: **brain = 8765**, **app = 8766**. Keep them consistent everywhere (they're in `CLAUDE.md`).
