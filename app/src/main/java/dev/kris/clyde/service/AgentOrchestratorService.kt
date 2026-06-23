package dev.kris.clyde.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.kris.clyde.R
import dev.kris.clyde.bridge.BrainClient
import dev.kris.clyde.bridge.LocalControlServer
import dev.kris.clyde.overlay.OverlayController
import dev.kris.clyde.runtime.BrainRunner
import dev.kris.clyde.runtime.EmbeddedRuntime
import dev.kris.clyde.util.Prefs
import dev.kris.clyde.voice.VoiceIO
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Session owner: hosts LocalControlServer (8766), runs voice, talks to the brain. */
class AgentOrchestratorService : Service() {

    companion object {
        const val ACTION_ASSIST = "dev.kris.clyde.ASSIST"
        const val ACTION_KILL = "dev.kris.clyde.KILL"
        const val ACTION_RESTART_BRAIN = "dev.kris.clyde.RESTART_BRAIN"
        const val ACTION_REMINDER_FIRED = "dev.kris.clyde.REMINDER_FIRED"
        private const val CHANNEL = "clyde_orchestrator"
        private const val NOTIF_ID = 42
        private const val TAG = "Clyde"
        private const val BRAIN_VERSION = "0.1.0" // structural runtime version (node/CLI/libs layout)

        // ask_user voice-pick: number/ordinal words → 1-based option position, and filler words to ignore.
        private val NUMBER_WORDS = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
            "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6,
            "1" to 1, "2" to 2, "3" to 3, "4" to 4, "5" to 5, "6" to 6,
        )
        private val STOPWORDS = setOf(
            "the", "a", "an", "to", "of", "and", "or", "please", "option", "number", "choice",
            "one", "just", "uh", "um", "i", "want", "use", "do", "it", "that", "this", "my",
            "let", "lets", "go", "with", "can", "you",
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var voice: VoiceIO
    private lateinit var overlay: OverlayController
    private var server: LocalControlServer? = null
    private val brain by lazy { BrainRunner(applicationContext) }
    private val sessionId = "app-${System.currentTimeMillis()}"
    private var listenRetries = 0 // transient STT errors retry quietly a couple of times before nagging
    private var askListenRetries = 0 // same quiet-retry idea for the ask_user voice-pick listen loop

    override fun onCreate() {
        super.onCreate()
        voice = VoiceIO(this)
        overlay = OverlayController(applicationContext)
        // Mic button: stop any speech, reset to Listening, and listen again (never just close).
        overlay.onMic = { voice.stopSpeaking(); overlay.showSummon(); startListening() }
        // Typed message: stop speech + listening, show it, and send to the brain.
        overlay.onSend = { t -> voice.stopSpeaking(); voice.stopListening(); overlay.transcript(t); handle(t) }
        // Dismissed (tapped outside / closed): stop talking and listening.
        overlay.onDismiss = { voice.stopSpeaking(); voice.stopListening() }
        // ask_user: when a question appears, listen for a spoken choice; stop listening once it resolves.
        overlay.onAskShown = { _, options -> startAskVoice(options) }
        overlay.onAskClosed = { voice.stopListening() }
        startInForeground()
        startServer()
        maybeStartEmbeddedBrain()
    }

    /** If this build bundles the embedded runtime, extract it once and run the brain in-process.
     *  No-op on builds without a bundled bootstrap — those use an external Termux brain instead. */
    private fun maybeStartEmbeddedBrain() {
        if (!EmbeddedRuntime.isBundled(applicationContext)) return
        scope.launch(Dispatchers.IO) {
            if (EmbeddedRuntime.ensureInstalled(applicationContext, BRAIN_VERSION)) {
                // Always pull the latest brain from THIS APK (keyed on app version) so a brain fix
                // ships without re-unpacking the whole runtime. Without this the brain went stale.
                EmbeddedRuntime.refreshBrain(applicationContext, appVersionTag())
                brain.start(Prefs.clydeKey)
                // Poll up to 8 s so startup errors land in logcat (and the notification) rather than
                // disappearing silently. The real diagnostic is in BrainRunner.diag.
                val deadline = System.currentTimeMillis() + 8_000L
                while (!brain.isRunning() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(300)
                }
                if (!brain.isRunning()) {
                    val diag = BrainRunner.diag.ifBlank { "no output captured" }
                    Log.e(TAG, "Brain never came up (8 s timeout):\n$diag")
                    updateNotification("Brain offline — tap Clyde to see error")
                }
            }
        }
    }

    /** A stable per-build tag (the app's versionCode) used to refresh the bundled brain on update. */
    private fun appVersionTag(): String = runCatching {
        val pi = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
        "v${pi.longVersionCode}"
    }.getOrDefault(BRAIN_VERSION)

    private fun startServer() {
        if (server != null) return
        try {
            server = LocalControlServer(
                ctx = applicationContext,
                voice = voice,
                key = Prefs.clydeKey,
                confirmHandler = { summary, details, action, params -> overlay.confirmBlocking(summary, details, action, params) },
                askHandler = { question, options ->
                    val a = overlay.askBlocking(question, options)
                    JSONObject().put("index", a.index).put("label", a.label).put("text", a.text).put("via", a.via).put("cancelled", a.cancelled)
                },
                overlayStatus = { text, _ -> overlay.status(text) },
                validateIntentToken = { token, action, body -> overlay.validateIssuedToken(token, action, body) },
                invalidateIntentToken = { token -> overlay.invalidateIssuedToken(token) },
                pointAt = { x, y -> overlay.pointAt(x, y) },
                // Hide Clyde's own overlay for the capture so the model sees the app, never Clyde's panel.
                screenshot = {
                    val svc = PhoneControlAccessibilityService.instance
                    if (svc == null) null
                    else { overlay.beginCapture(); try { svc.screenshotBase64() } finally { overlay.endCapture() } }
                },
            ).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            Log.i(TAG, "LocalControlServer up on 127.0.0.1:${LocalControlServer.PORT}")
        } catch (e: Exception) {
            Log.e(TAG, "LocalControlServer failed to start", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ASSIST -> beginAssist()
            ACTION_KILL -> {
                overlay.cancelPendingConfirm() // unpark any blocked /confirm worker so later turns aren't auto-denied
                overlay.cancelPendingAsk() // likewise unpark a blocked /ask worker
                overlay.clearIssuedTokens()
                overlay.hide()
                voice.stopListening()
            }
            // Token just pasted / fresh login → bounce the brain so it relaunches WITH creds loaded.
            // Off the main thread: stop() now blocks until the old process dies (frees port 8765).
            ACTION_RESTART_BRAIN -> scope.launch(Dispatchers.IO) { brain.stop(); maybeStartEmbeddedBrain() }
            ACTION_REMINDER_FIRED -> {
                val text = intent.getStringExtra("text").orEmpty()
                val action = intent.getStringExtra("action").orEmpty()
                voice.stopSpeaking()
                overlay.showSummon()
                overlay.answer("Reminder: $text")
                voice.speak("Reminder. $text")
                // If the reminder carried an action ("at 6pm, start my commute playlist"), run it now —
                // consequential steps still surface a confirm as usual.
                if (action.isNotBlank()) handle(action)
            }
        }
        return START_STICKY
    }

    private fun beginAssist() {
        voice.stopSpeaking() // re-summoning while Clyde is talking cuts it off and listens fresh
        overlay.showSummon()
        // If the embedded brain hasn't come up, show its startup diagnostics immediately instead of
        // falling into voice-listen → connection-refused → generic "something went wrong" message.
        if (EmbeddedRuntime.isBundled(applicationContext) && !brain.isRunning()) {
            overlay.answer("Brain offline:\n${BrainRunner.diag.take(600).ifBlank { "no output captured" }}")
            return
        }
        startListening()
    }

    /** Start (or restart) a voice-listen cycle. Reused by the assist trigger and the mic button.
     *  [fresh] true resets the retry counter (a user-initiated listen); the quiet auto-retry passes false. */
    private fun startListening(fresh: Boolean = true) {
        if (fresh) listenRetries = 0
        voice.listen(
            onPartial = { overlay.transcript(it) },
            onRms = { overlay.amplitude(it) },
            onFinal = { text -> listenRetries = 0; if (text.isNotBlank()) { overlay.transcript(text); handle(text) } },
            onError = { err ->
                val fatal = err == "mic-permission" || err == "speech recognition unavailable"
                if (!fatal && listenRetries < 2 && overlay.isShowing()) {
                    // Transient STT hiccups (busy / no-speech / network / stt-*) are common; retry quietly
                    // a couple of times before EVER nagging, so one miss doesn't read as "didn't catch that".
                    listenRetries++
                    Log.w(TAG, "STT retry $listenRetries after: $err")
                    scope.launch { delay(300); if (overlay.isShowing()) startListening(fresh = false) }
                } else {
                    listenRetries = 0
                    when (err) {
                        "mic-permission" -> overlay.status("Mic access is off — enable it for Clyde in Settings")
                        "speech recognition unavailable" -> overlay.status("Speech isn't available on this device")
                        "network" -> overlay.status("No connection for speech recognition")
                        "no-speech" -> {} // heard nothing even after retries → just go quiet, no nag
                        else -> overlay.status("Didn't catch that — tap the mic to try again")
                    }
                    Log.w(TAG, "Speech recognition error (final): $err")
                }
            },
        )
    }

    /** When Clyde finishes speaking, listen for a reply automatically — so it's a back-and-forth
     *  conversation, no re-summoning between turns. If the user says nothing, it just goes quiet. */
    private fun onSpeechFinished() {
        if (!overlay.isShowing()) return // user dismissed while it was talking → don't reopen the mic
        overlay.listenAgain()
        startListening()
    }

    // ── ask_user voice-pick ───────────────────────────────────────────────
    /** Listen for the user's spoken answer to an ask_user question and resolve it to an option. A real
     *  utterance that matches no option falls to the LAST ("Other") option, carrying their words; mere
     *  silence just retries (so a pause doesn't end the question). Tapping a chip resolves it in parallel. */
    private fun startAskVoice(options: List<String>) {
        askListenRetries = 0
        voice.stopSpeaking()
        listenForChoice(options)
    }

    private fun listenForChoice(options: List<String>) {
        if (!overlay.isAsking()) return
        voice.listen(
            onPartial = { partial -> if (overlay.isAsking()) overlay.updateAskTranscript(partial, matchOption(partial, options)) },
            onRms = { if (overlay.isAsking()) overlay.amplitude(it) },
            onFinal = { said ->
                if (overlay.isAsking()) {
                    val idx = matchOption(said, options)
                    // matched an option → that one; no match → the last (catch-all "Other"), carrying their words
                    overlay.answerAskByVoice(if (idx >= 0) idx else options.lastIndex, said)
                }
            },
            onError = { err ->
                if (overlay.isAsking()) {
                    val transient = err == "no-speech" || err == "busy" || err == "network" || err.startsWith("stt-")
                    when {
                        transient && askListenRetries < 4 -> {
                            askListenRetries++ // a brief silence shouldn't end the question — keep the mic open
                            scope.launch { delay(300); if (overlay.isAsking()) listenForChoice(options) }
                        }
                        err == "mic-permission" || err == "speech recognition unavailable" ->
                            overlay.updateAskTranscript("Mic is off — tap an option", -1)
                        else -> overlay.updateAskTranscript("Tap an option to choose", -1)
                    }
                }
            },
        )
    }

    /** Map a spoken phrase to an option index, or -1 if it matches none. Considers positional phrasing
     *  ("the second one", "option 3", a bare number) and label/keyword overlap. Deterministic — the
     *  no-match case is what routes a free-form answer to the catch-all option. */
    private fun matchOption(saidRaw: String, options: List<String>): Int {
        val said = normalizeSpeech(saidRaw)
        if (said.isBlank()) return -1
        val tokens = said.split(" ").filter { it.isNotBlank() }
        // 1) positional — strongest when phrased as a position ("option/number/choice N", "… one") or a
        //    very short utterance that's basically just a number/ordinal.
        val positional = said.contains("option") || said.contains("number") || said.contains("choice") ||
            said.endsWith(" one") || tokens.size <= 2
        if (positional) {
            for (tok in tokens) NUMBER_WORDS[tok]?.let { n -> if (n in 1..options.size) return n - 1 }
        }
        // 2) label / keyword overlap, scored; the best option wins if it clears the threshold.
        var best = -1
        var bestScore = 0.0
        options.forEachIndexed { i, optRaw ->
            val opt = normalizeSpeech(optRaw)
            if (opt.isNotBlank()) {
                val score = labelScore(said, tokens, opt)
                if (score > bestScore) { bestScore = score; best = i }
            }
        }
        return if (bestScore >= 0.6) best else -1
    }

    private fun labelScore(said: String, saidTokens: List<String>, opt: String): Double {
        if (said == opt) return 1.0
        if (said.contains(opt)) return 0.92
        if (opt.length >= 3 && opt.contains(said)) return 0.85
        val optTokens = opt.split(" ").filter { it.length > 1 && it !in STOPWORDS }
        if (optTokens.isEmpty()) return 0.0
        val present = optTokens.count { saidTokens.contains(it) }
        val frac = present.toDouble() / optTokens.size
        return when {
            frac >= 1.0 -> 0.9
            frac >= 0.5 -> 0.7
            saidTokens.contains(optTokens.first()) -> 0.6
            else -> frac * 0.6
        }
    }

    private fun normalizeSpeech(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun handle(text: String) {
        overlay.status("Thinking…")
        scope.launch {
            BrainClient.query(text, sessionId, Prefs.assistantModel) { ev ->
                when (ev.optString("type")) {
                    "status" -> overlay.status(ev.optString("text"))
                    "action" -> overlay.status(ev.optString("summary").ifBlank { ev.optString("tool") })
                    "need_confirm" -> overlay.status(ev.optString("summary").ifBlank { "Waiting for your OK…" })
                    "suggestions" -> {
                        val arr = ev.optJSONArray("items")
                        if (arr != null) {
                            val items = ArrayList<String>()
                            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { items.add(it) }
                            overlay.suggestions(items)
                        }
                    }
                    // ignore a blank final (e.g. a turn that ended with no answer) — keep the last status
                    "final" -> ev.optString("text").trim().takeIf { it.isNotEmpty() }?.let { overlay.answer(it); voice.speak(it) { onSpeechFinished() } }
                    "error" -> {
                        val detail = ev.optString("detail").trim()
                        Log.w(TAG, "brain error: ${ev.optString("text")} | detail=$detail")
                        // Surface the real reason on screen (detail) so failures are diagnosable instead
                        // of an opaque "something went wrong"; speak a short, friendly version.
                        val shown = if (detail.isNotEmpty()) "Sorry — $detail" else "Sorry, something went wrong — try again."
                        overlay.answer(shown); voice.speak("Sorry, something went wrong.") { onSpeechFinished() }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Clyde", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Clyde")
            .setContentText("Assistant ready")
            .setSmallIcon(R.drawable.ic_clyde_logo)
            .setOngoing(true)
            .build()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notif, type)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            // A microphone-typed FGS can throw if there's no while-in-use window. We MUST still
            // fulfil the startForeground contract → retry as SPECIAL_USE only (no such requirement).
            Log.w(TAG, "typed startForeground failed; retrying SPECIAL_USE only", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIF_ID, notif)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground failed entirely", e2)
            }
        }
    }

    private fun updateNotification(text: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Clyde")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_clyde_logo)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlay.cancelPendingConfirm() // release a parked confirm worker before closing the server
        overlay.cancelPendingAsk()
        brain.stop()
        server?.stop()
        voice.destroy()
        overlay.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
