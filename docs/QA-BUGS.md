# Clyde security + production audit

Adversarial audit (find → independent verify). **10 confirmed**, 0 false positives dismissed.

Severity: 0 critical · 0 high · 6 medium · 4 low

## 1. [MEDIUM] Setup progress is lost on rotation / dark-mode toggle / process death (remember, not rememberSaveable; no configChanges)
*lifecycle* — `app/src/main/java/dev/kris/clyde/MainActivity.kt`

**Problem:** ClydeRoot drives the entire app navigation from `var screen by remember { mutableStateOf(...) }`. `remember` does NOT survive Activity recreation, and MainActivity declares no `android:configChanges` in AndroidManifest.xml (lines 62-70), so any configuration change (rotation, switching light/dark mode, font-size change, locale change, multi-window resize) or a low-memory process kill recreates the Activity and resets `screen` to `if (Prefs.signedIn) Screen.Home else Screen.Login`. A user partway through onboarding (e.g. on BrainSetup/Verify/Setup/Grants) who hasn't yet had `Prefs.signedIn` set is thrown all the way back to Login; a signed-in user mid-Setup is bounced to Home. Worse on the Grants screen: rotating the phone while in the system overlay/accessibility settings and returning lands the user back at Home rather than the Grants step. None of the screen-local state (BrainSetupScreen's `attempt`/`loginActive`/`code`, VerifyScreen's `attempt`, SetupScreen's `step`) is `rememberSaveable` either, so all of it resets too.

**Fix:** Use `rememberSaveable` for the top-level `screen` (it's an enum — save its name/ordinal) and for the per-screen step/attempt state, OR hoist navigation into a ViewModel / Navigation-Compose back stack. At minimum, make the enum survivable so a rotation mid-onboarding doesn't restart the flow.

## 2. [MEDIUM] TextToSpeech/recognizer (VoiceIO) invoked off the main thread despite 'main-thread only' contract
*concurrency* — `app/src/main/java/dev/kris/clyde/voice/VoiceIO.kt`

**Problem:** VoiceIO's class doc states 'Create/call from the main thread.' but two call paths invoke voice.speak() off-main: (1) LocalControlServer.serve() handles /speak on a NanoHTTPD worker thread and calls voice.speak(body...) directly (LocalControlServer.kt:93) — no main-thread hop. (2) In AgentOrchestratorService.handle(), the onEvent callback passed to BrainClient.query runs inside query's `withContext(Dispatchers.IO)` block (BrainClient.kt:48), so the 'final'/'error' branches call voice.speak(it) (AgentOrchestratorService.kt:114,118) on an IO thread, not Main, even though the enclosing scope is Dispatchers.Main. overlay.answer/status are safe because OverlayController.onMain re-dispatches, but VoiceIO has no such guard. TextToSpeech is internally serviced but its init callback sets tts fields without happens-before vs. these off-thread reads, and the documented contract is being violated — leading to dropped/garbled utterances and potential lost first-utterance if speak races TTS init.

**Fix:** Make VoiceIO self-dispatch onto the main looper (mirror OverlayController.onMain): wrap speak/listen/stopListening bodies in a Handler(Looper.getMainLooper()) post when not already on main. Alternatively, in AgentOrchestratorService.handle wrap the speak calls in withContext(Dispatchers.Main) and have LocalControlServer post /speak to a main handler.

## 3. [MEDIUM] Confirm token burned brain-side before the app fires — retry after a failed consequential intent is wrongly denied
*contract* — `brain/src/agent.ts`

**Problem:** For every consequential tool, agent.ts canUseTool calls safety.consumeToken(...) which sets rec.used=true (safety.ts:64) BEFORE the tool handler runs and BEFORE the app actually fires the intent. The app side was deliberately built to NOT burn its own copy of the token on a failed fire: LocalControlServer.kt:84-89 only calls invalidateIntentToken on success, with the comment "token preserved — user can retry without re-approving." But the brain has already marked its token used. So when the app-side fire fails for a recoverable reason (e.g. SmsManager throws, DeviceIntents.sendSms returns false, an Activity-not-found on open_url/share_text), the model retries the same tool with the same token and the brain's consumeToken returns {ok:false, error:"token already used — call confirm() again"} (safety.ts:57). The retry is denied at the brain before it ever reaches the app, so the documented "retry without re-approving" behavior never actually works for any consequential intent. The two single-use token systems (brain Safety + app issuedTokens) have opposite burn-timing: brain burns on attempt, app burns on success.

**Fix:** Make the brain's token consumption match the app's burn-on-success semantics. Either (a) in canUseTool, VALIDATE the token (check exists/bound/unexpired) without setting used=true, and only mark it used after the tool handler reports the app fire succeeded; or (b) accept burn-on-attempt and remove the app-side 'preserve on failure / retry without re-approving' contract (LocalControlServer.kt:84-89) so both sides agree. Today they contradict each other.

## 4. [MEDIUM] Agent emits final "Done." with no real text when a turn ends without a success result (e.g. max turns)
*contract* — `brain/src/agent.ts`

**Problem:** finalText is only assigned from m.result on a 'result' message (agent.ts:112-113), and m.result is undefined for non-success result subtypes such as error_max_turns / error_during_execution. In those cases the loop completes normally (no throw), so the catch block isn't hit, and the code falls through to emit({type:"final", text: finalText || "Done."}) at line 125. The app then renders/speaks "Done." (AgentOrchestratorService.kt:114) even though the agent actually ran out of turns or aborted mid-task, telling the user the task succeeded when it didn't. maxTurns is 24 (agent.ts:63), so a long supervised UI-automation flow hitting the cap is a realistic trigger.

**Fix:** Inspect the result message subtype. On non-success subtypes (error_max_turns, error_during_execution, etc.) emit {type:"error", text: ...} or a final that conveys the truncation (e.g. "I ran out of steps before finishing.") instead of the misleading "Done." fallback.

## 5. [MEDIUM] LoginScreen copy hardcodes 'running in Termux' on every build, including embedded
*ux* — `app/src/main/java/dev/kris/clyde/login/LoginScreen.kt`

**Problem:** The body text states 'Next: a one-time setup gets Clyde's brain running in Termux' (line 78), the KDoc says 'Launches `claude login` in Termux' (line 37), and the footer suggests copying ~/.claude 'from a desktop'. BrainSetupScreen correctly detects embedded builds and shows the in-process EmbeddedBrainSetup ('Clyde's brain runs inside the app — nothing else to install'). But LoginScreen — the very first screen the user sees — unconditionally promises a Termux setup. On the flagship embedded packaging this directly contradicts the next screen and tells the user to install Termux when they never will. This is wrong-per-build copy at the most prominent point of the funnel.

**Fix:** Branch the LoginScreen subtitle/footer on EmbeddedRuntime.isBundled(ctx), mirroring BrainSetupScreen: embedded → 'sign in once with your Claude plan, the brain runs inside the app'; external → the Termux wording.

## 6. [MEDIUM] Mic denied is reported as 'Didn't catch that', and the summon overlay has no retry affordance
*ux* — `app/src/main/java/dev/kris/clyde/service/AgentOrchestratorService.kt`

**Problem:** beginAssist() calls voice.listen() with no RECORD_AUDIO check (the only checkSelfPermission is for the FGS type at line 141). When mic permission is denied or STT fails, SpeechRecognizer.onError fires and the handler sets overlay.status('Didn't catch that') for ALL error codes — including ERROR_INSUFFICIENT_PERMISSIONS and ERROR_RECOGNIZER_BUSY. So a permissions problem is mislabeled as a misheard phrase. Worse, the SummonPanel mic button and ask-bar Row (OverlayController.kt SummonPanel, lines ~371-382) have NO clickable/pressable handler — they are purely decorative — so once listening ends or errors there is no in-overlay way to re-trigger; the only action is tap-outside to dismiss. The user is stuck on a dead 'Didn't catch that' panel.

**Fix:** Map STT error codes to honest messages (distinguish permission/unavailable/no-speech), and make the mic button in SummonPanel actually restart listening (wire voice.listen via a callback) so the overlay has a real retry path instead of forcing a dismiss-and-regesture.

## 7. [LOW] EmbeddedBrainSetup poll loop never terminates after success and clobbers state set by the sign-in callback
*lifecycle* — `app/src/main/java/dev/kris/clyde/setup/BrainSetupScreen.kt`

**Problem:** The `LaunchedEffect(attempt)` loop is `while (true) { ... delay(2000) }` and only `return@LaunchedEffect`s on the two error timeouts (ticks>=45 / >=60). On the success path it never exits: it polls `EmbeddedRuntime.isInstalled`, `BrainClient.healthz()`, and `auth.isSignedIn()` forever, every 2s, for the entire time the user sits on this screen (a battery/CPU drain that also keeps poking the brain). It also fights the sign-in callback: the callback sets `signedIn = true` (set via onResult), but `auth.isSignedIn()` only flips true once credentials are written to disk, and the loop does `if (auth.isSignedIn()) signedIn = true` — it never sets it back to false, but combined with the off-thread write of `signedIn` from the callback (previous finding) there are two writers to the same state on different cadences. The loop should stop once `online==true && signedIn`.

**Fix:** Add a terminal condition: `if (online == true && signedIn) return@LaunchedEffect` (after updating the step cards), so the loop stops polling once setup is complete. Keep a single owner of each state var.

## 8. [LOW] answer()'s auto-settle and a racing hide() leave the next summon showing a stale Success/answer frame
*lifecycle* — `app/src/main/java/dev/kris/clyde/overlay/OverlayController.kt`

**Problem:** `answer(text)` sets `clawd=Success` and posts `settle` 2200ms later to flip Success→Idle, but it does NOT clear `answer`/`transcript`. If the user taps outside (OverlayRoot tap-target calls `onClose`→`hide()`) before the next assist, `hide()` (line 172) only `detach()`es the window and removes the `settle` callback — it does not reset `ui.value`. The class-level `ui` mutableState therefore still holds the previous turn's `answer`/`transcript`/`status`. On the next `showSummon()` the state is reset to a fresh OverlayUi, so that path is fine — but `status()`/`transcript()`/`answer()` all do `ui.value.copy(...)`, so any of them invoked without a preceding `showSummon()` (e.g. brain pushes /overlay/status or /speak between sessions) will re-attach the window carrying the previous turn's leftover `answer` string and render it in the new bubble until overwritten.

**Fix:** Reset the relevant fields when hiding (e.g. `ui.value = OverlayUi()` in hide()), or have status()/answer() that re-attach a hidden overlay start from a clean OverlayUi rather than copy()-ing stale fields.

## 9. [LOW] brain 'need_confirm' NDJSON event has no handler in the app (silent else branch)
*contract* — `app/src/main/java/dev/kris/clyde/service/AgentOrchestratorService.kt`

**Problem:** The integration contract (CLAUDE.md, types.ts AgentEvent) defines five streamed event types: status, delta, action, need_confirm, final, error. The app's when(ev.optString("type")) handles status/action/final/error and falls through to else -> {} for need_confirm and delta. delta is intentionally optional (TTS), but need_confirm is a first-class contract event the brain emits from the confirm tool (meta.ts:28) right before it blocks on app.confirm. The actual confirm UI happens to be driven by the separate inbound POST /confirm call, so nothing visibly breaks — but the streamed need_confirm carries the summary/details and is dropped, meaning the overlay shows no 'about to ask you something' affordance from the stream and any future reliance on it is silently a no-op. It is a declared-but-unconsumed contract event.

**Fix:** Add a `"need_confirm" ->` branch (e.g. overlay.status("Asking for your ok…") or drive a pre-confirm hint), or explicitly document in the contract that need_confirm is informational-only and the app intentionally ignores it because /confirm drives the UI. Right now the omission looks accidental given the symmetric handling of the other event types.

## 10. [LOW] reduceMotion() can't see live system changes and only catches scale==0, not Settings.System.transition/window scales
*ux* — `app/src/main/java/dev/kris/clyde/ui/Components.kt`

**Problem:** reduceMotion() reads Settings.Global.ANIMATOR_DURATION_SCALE once inside remember{} with no key, so the value is captured at first composition and never updates if the user toggles 'Remove animations' while the app is running. It also only treats exactly 0f as reduce-motion and ignores Settings.Global.TRANSITION_ANIMATION_SCALE / WINDOW_ANIMATION_SCALE. Many infinite/decorative animations (Clawd, VoiceLight, CapabilityRow pulse, screen transitions) gate on this single read, so a user who disables animations mid-session still gets the full motion set until process restart.

**Fix:** Drop the unkeyed remember (or key it on a lifecycle/resume signal) so the value re-reads, and consider treating any of the three animation-scale globals being 0 as reduce-motion.
