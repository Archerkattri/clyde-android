# 03 — Component specs

Class-by-class. "Contract" = the exact interface another component depends on. Claude Code should treat contracts as fixed and implement around them.

---

## APP SHELL (`app/`)

### AssistEntryActivity
- **Role:** the trigger target. Registered for `android.intent.action.ASSIST` (+ `android.intent.action.VOICE_COMMAND`). Transparent, `noHistory`, `excludeFromRecents`.
- **On launch:** start `AgentOrchestratorService` (foreground), then either (a) start STT for a spoken query, or (b) show a minimal text/voice input. Hand the query to the service. Finish immediately (don't hold UI).
- **Why an Activity, not VoiceInteractionService:** the Activity path is sideload-able and gets you into the Digital-assistant picker (Tier A). Full `VoiceInteractionService` needs platform signing (Tier 4) — leave a `TODO` seam.

### AgentOrchestratorService (foreground service, type `microphone`+`specialUse`)
- **Role:** session owner for one interaction. Persistent notification.
- **Responsibilities:** receive query → send to brain (`BrainClient` Model A, or `TermuxRunCommand` Model B) → receive streamed status/answer → drive `TextToSpeechIO` and `OverlayController` (status bubble) → handle `confirm()` requests by showing `ConfirmSheet`.
- **Also hosts `LocalControlServer`** (starts it on create; stops on destroy).
- **Contract (to brain):** see BrainClient/LocalControlServer protocols below.

### LocalControlServer  — **127.0.0.1:8766** (the app's action API the brain calls)
Embedded HTTP (use NanoHTTPD or Ktor embedded). JSON in/out. Every endpoint returns `{ ok: bool, result?: any, error?: string }`.

| Method + path | Body | Does |
|---|---|---|
| `GET /caps` | – | returns `CapabilityProbe` snapshot: `{accessibility, shizuku, root, overlay, perms:{sms,call,…}}` |
| `POST /a11y/dump` | `{}` | returns serialized node tree (see schema in `04`) |
| `POST /a11y/tap` | `{x,y}` or `{nodeId}` | dispatchGesture / performAction click |
| `POST /a11y/type` | `{nodeId?, text}` | ACTION_SET_TEXT or focused-field type |
| `POST /a11y/swipe` | `{x1,y1,x2,y2,ms}` | dispatchGesture swipe |
| `POST /a11y/global` | `{action}` | performGlobalAction(BACK/HOME/RECENTS/NOTIFICATIONS/QUICK_SETTINGS/LOCK/SCREENSHOT) |
| `POST /a11y/screenshot` | `{}` | AccessibilityService.takeScreenshot → returns base64 PNG |
| `POST /intent/*` | varies | fire Tier-0 intents using the app's permissions (alarm/timer/call/sms/nav/calendar/launch) |
| `POST /speak` | `{text}` | TTS |
| `POST /confirm` | `{summary, details?}` | show ConfirmSheet; **blocks** until user approves/denies; returns `{approved, token?}` |
| `POST /gemini/delegate` | `{prompt, want:"image"|"text"}` | GeminiRouter delegate; returns result/intent status |
| `POST /overlay/status` | `{text, state}` | update status bubble |

- **Security:** bind to loopback only; require a shared secret header (`X-Clyde-Key`, generated at first run, stored in both app prefs and `brain/.env`) so other local apps can't call it.

### PhoneControlAccessibilityService
- **Role:** Tier-1 hands. Config: `canRetrieveWindowContent=true`, `canPerformGestures=true`, `accessibilityEventTypes=typeAllMask` (throttle with `notificationTimeout`).
- **Exposes (called by LocalControlServer, same process):**
  - `dumpTree(): NodeTree` — BFS from `getRootInActiveWindow()` + `getWindows()`; each node → `{id, role(className), text, desc, bounds, clickable, editable, scrollable, focused, package}`. Assign stable per-dump `nodeId`s.
  - `tap(nodeId|x,y)`, `setText(nodeId,text)`, `swipe(...)`, `global(action)`, `screenshot()`.
- **Limits to encode:** `getRootInActiveWindow()` may be null mid-transition → retry w/ backoff; FLAG_SECURE blanks screenshots (tree still readable); thin trees on Compose/WebView → signal caller to fall back to screenshot.

### BrainClient  — talks to **127.0.0.1:8765** (Model A)
- **Contract (app → brain):** `POST /query` `{text, sessionId, context?}` → streamed **NDJSON** lines:
  - `{type:"status", text}` (e.g., "reading screen")
  - `{type:"action", tool, summary}` (for the status bubble / logs)
  - `{type:"need_confirm", summary, details}` → app must call its own ConfirmSheet and `POST /confirm-result {token,...}` back to brain, OR the brain calls the app's `/confirm` directly (pick one path — default: **brain calls app `/confirm`**, simpler).
  - `{type:"final", text}` → speak it.
- Keep `sessionId` per app session for context continuity.

### TermuxRunCommand (Model B fallback)
- Fire `com.termux.RUN_COMMAND` intent → `bash -lc 'node ~/clyde/brain/oneshot.ts "<query>"'`, background, with a `PendingIntent` to receive `{stdout, exit}`. Requires `com.termux.permission.RUN_COMMAND` + Termux `allow-external-apps=true`. Use only until Model A is up.

### VoiceIO — SpeechRecognizerIO / TextToSpeechIO
- STT: `SpeechRecognizer` (on-device where available). Interface: `listen(): Flow<PartialText> ; final: String`. Swappable for Vosk (offline) later.
- TTS: `TextToSpeech` with `USAGE_ASSISTANT`. Interface: `speak(text)`, `stop()`. Swappable for Piper/sherpa-onnx later.

### OverlayController / ConfirmSheet
- `OverlayController`: SYSTEM_ALERT_WINDOW status bubble + region-crop for "ask about screen" → returns a screenshot region to the brain as context.
- `ConfirmSheet`: shows `summary`/`details`, Approve/Deny, optional "always allow this app/tool." On approve, returns a single-use `token` (random, short-TTL) that the brain must pass to the consequential tool. Encodes the safety model from `01 §7`.

### GeminiRouter
- `openAssistantPicker()` → deep-link `Settings.ACTION_VOICE_INPUT_SETTINGS` (the toggle).
- `delegate(prompt, want)` → fire intent to Gemini app (e.g., `ACTION_ASSIST`/share with text, or the Gemini app's launch intent with the prompt) for image gen / Nano features; best-effort capture result; return to brain.

### CapabilityProbe
- Detects: accessibility service enabled (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`), Shizuku bound (Shizuku API `pingBinder`), root (`which su` / Magisk), overlay permission, and which runtime perms are granted. Cached + refreshed; served at `/caps`.

---

## BRAIN (`brain/`, TypeScript in Termux)

### server.ts (Model A)
- HTTP server on `127.0.0.1:8765`, shared-secret header. `POST /query` → runs `agent.ts` and streams NDJSON back. Health `GET /healthz`.

### agent.ts
- Uses **`@anthropic-ai/claude-agent-sdk`** `query()` with: the system prompt (`system-prompt.md`), the tool set from `tools/index.ts` (filtered by `capabilities()`), streaming on. Maps SDK tool-calls/results to the NDJSON stream. Maintains per-`sessionId` context.
- **Auth:** relies on `~/.claude` subscription credentials from `claude login`. **Must not read `ANTHROPIC_API_KEY`** — assert it's unset at boot and warn loudly if present.

### appClient.ts
- Thin wrapper over the app's `127.0.0.1:8766` endpoints (caps, a11y/*, intent/*, speak, confirm, gemini/delegate). Adds the shared secret header.

### capabilities.ts
- Merges the app `/caps` with local probes (`rish -c true` for Shizuku, `su -c id` for root). Returns `{tier0:true, tier1:bool, tier2:bool, tier3:bool}` and a per-tool availability map. Tools/index.ts uses this to only expose live tools.

### safety.ts
- Classifies each tool `safe | consequential` (table in `04`). For consequential tools, requires a valid confirm token (obtained via `appClient.confirm()`), enforces hard-stops (no payments, no self-permission-grants, show URLs), and logs every consequential action.

### tools/*.ts
- Each file exports tool definitions (name, JSON schema, handler). Handlers call either `appClient` (Tier 0 intents / Tier 1 a11y / Gemini) or run `rish`/`su` shell (Tier 2/3) via child_process. See `04-mcp-tool-catalog.md` for the full list + schemas.

### oneshot.ts (Model B)
- `node oneshot.ts "<query>"`: runs one `agent.ts` turn, prints the final answer to stdout, exits. Same auth + tools.
