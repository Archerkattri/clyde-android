# 07 — Build phases

Build in order. Each phase is independently testable on the device. Don't skip ahead — the value of the staged plan is that you always have something working.

---

### P0 — Scaffold & "hello brain" (no Android UI yet)
**Do:** create the repo (`02`), drop in `CLAUDE.md`. Stand up `brain/` with the Agent SDK, subscription login, one tool (`get_battery` via termux-api), and `server.ts` on 8765. 
**Test:** `curl` the brain → it answers a plain question and can call `get_battery`. Confirm `/status` shows **subscription**, no API key.
**Done when:** the brain reasons + calls one real device tool, on your plan.

---

### P1 — Trigger + voice + one-shot brain (end-to-end skeleton)
**Do:** build `AssistEntryActivity` (ACTION_ASSIST) + `AgentOrchestratorService` + `VoiceIO`. Wire **Model B** (`TermuxRunCommand` → `node oneshot.ts`). Speak the reply.
**Test:** set Clyde as Digital assistant app → long-press home → speak "what's my battery?" → hear the answer.
**Done when:** the assist gesture summons Claude and talks back. **This is the moment it "replaces Gemini."**

---

### P2 — Tier 0 device control (intents + Termux:API)
**Do:** `LocalControlServer` (8766) + `DeviceIntents`; implement Tier-0 tools (alarm, timer, call, sms, navigate, launch_app, calendar) and the Termux:API tools. Add the **safety/confirm** model (`ConfirmSheet` + `confirm()` + tokens) now, while the surface is small.
**Test:** "set a 10-minute timer", "open Spotify", "text Mom 'on my way'" (with confirm), "where's the nearest pharmacy".
**Done when:** deterministic phone tasks work with confirmations on consequential ones.

---

### P3 — Tier 1 accessibility (see + tap any app)
**Do:** `PhoneControlAccessibilityService` + the a11y endpoints; tools `ui_dump`, `tap`, `type_text`, `swipe`, `global_action`, `screenshot`. Implement the perception strategy (tree first, screenshot fallback). Wire `capabilities()`.
**Test:** "in this app, scroll down and tap the first result", "go back", "take a screenshot and tell me what's on screen".
**Done when:** Claude can drive arbitrary app UI under supervision.

---

### P4 — Persistent brain (Model A) + Shizuku Tier 2
**Do:** switch app→brain to `BrainClient` streaming on 8765 (persistent session, context). Add Tier-2 tools via `rish` (`input_*`, `pm_grant`, `settings_put`, `uiautomator_dump`, `disable_phantom_killer`). Add keep-alive (Termux:Boot, wakelock, phantom-killer off).
**Test:** multi-turn ("open settings… now turn on Bluetooth… now go back"); a task needing `input_tap` where accessibility couldn't reach; reboot → brain auto-starts.
**Done when:** low-latency, context-keeping agent with ADB-level reach, surviving reboots.

---

### P5 — Polish: Gemini router, overlay, root (optional), proactive
**Do:** `GeminiRouter` (toggle deep-link + `delegate_to_gemini` for image/Nano); ask-about-screen overlay; Tier-3 `su` tools if rooted; optional `WakeWordService` (Porcupine/openWakeWord) and `NotificationListener` for proactive triage (novel ideas #4/#8).
**Test:** "make an image of a fox and set it as wallpaper" (Gemini generates → Claude sets); "what's on my screen?" via overlay; (rooted) a secure-screen action; (optional) wake word.
**Done when:** the router works, Gemini is one delegate-call away, and the assistant feels complete.

---

## Definition of done (the whole thing)
- Summon Claude via assist gesture in Gemini's place; toggle back to Gemini in two taps.
- Runs entirely on your **subscription** (verified `/status`, no API key anywhere).
- Tier 0–2 device control working (Tier 3 if rooted), with confirmations on consequential actions.
- Persistent brain survives reboot; supervised multi-step tasks succeed often enough to be useful.
- `delegate_to_gemini` covers image/video/Nano gaps.

## Testing notes
- Keep a **kill switch**: a notification action + a hardware gesture that force-stops the orchestrator and revokes the current confirm tokens.
- Log every consequential action with its confirm token and result.
- Expect ~40–70% on long multi-step UI chains — test with retries and always-visible "about to do X" status.
