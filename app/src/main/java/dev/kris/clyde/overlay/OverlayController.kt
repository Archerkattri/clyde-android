package dev.kris.clyde.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.kris.clyde.R
import dev.kris.clyde.ui.Body
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.ClydeTheme
import dev.kris.clyde.ui.Display
import dev.kris.clyde.ui.Mono
import dev.kris.clyde.ui.Serif
import dev.kris.clyde.ui.pressable
import dev.kris.clyde.ui.reduceMotion
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import org.json.JSONObject

enum class OverlayMode { Hidden, Summon, Confirm, Ask }

data class OverlayUi(
    val mode: OverlayMode = OverlayMode.Hidden,
    val transcript: String = "",
    val status: String = "",
    val answer: String = "",
    val clawd: ClawdState = ClawdState.Idle,
    val scene: String = "", // non-blank → render the composed scene engine instead of the hero state

    val confirmSummary: String = "",
    val confirmDetails: String? = null,
    val confirmEffect: String = "",

    // ask_user: one multiple-choice question answered by voice or tap.
    val askQuestion: String = "",
    val askOptions: List<String> = emptyList(),
    val askTranscript: String = "", // live partial speech while choosing
    val askHighlight: Int = -1, // option the current speech matches (live feedback), -1 = none

    val suggestions: List<String> = emptyList(), // tappable follow-up chips shown under an answer
    val amplitude: Float = 0f, // live mic level 0..1 while listening (drives the voice light)
    val steps: List<String> = emptyList(), // recent action/status lines — the "show your work" feed
    val plannedSteps: List<String> = emptyList(), // up-front plan for a multi-step task (checked off live)
    val actionsSeen: Int = 0, // real tool actions fired this turn → which planned step is current
)

/** The user's answer to an ask_user question — by voice (verbatim words in [text]) or by tapping. */
data class AskAnswer(val index: Int, val label: String, val text: String, val via: String, val cancelled: Boolean)

/** Hosts the summon/confirm overlay in a WindowManager window (Compose + lifecycle owners). */
class OverlayController(private val appCtx: Context) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val main = Handler(Looper.getMainLooper())
    private val wm = appCtx.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var pointerView: ComposeView? = null
    private val clearPointer = Runnable { detachPointer() }
    private val ui = mutableStateOf(OverlayUi())
    // Set true on dismiss so a late event (a brain status/answer or a confirm arriving AFTER the user
    // closed the popup) can't re-attach it; cleared by a fresh user-initiated summon.
    @Volatile private var dismissed = false
    // True while Clyde is driving the screen: the window passes touches THROUGH to the app and isn't
    // focused, so injected taps reach the app (not Clyde's own glass) and the screen reads cleanly.
    @Volatile private var passthrough = false

    /** Wired by the service. [onMic] = stop any speech + listen again; [onSend] = a typed message;
     *  [onDismiss] = the popup was dismissed (stop speech + listening). */
    var onMic: () -> Unit = {}
    var onSend: (String) -> Unit = {}
    var onDismiss: () -> Unit = {}
    /** ask_user hooks (wired by the service): [onAskShown] starts a voice-listen for the choice;
     *  [onAskClosed] stops it once the question resolves (tap / voice / dismiss). */
    var onAskShown: (question: String, options: List<String>) -> Unit = { _, _ -> }
    var onAskClosed: () -> Unit = {}

    private val confirmResult = ArrayBlockingQueue<Pair<Boolean, String?>>(1)
    private val confirmPending = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var pendingAction: String = ""
    @Volatile private var pendingParams: JSONObject = JSONObject()

    // ask_user uses the same park/resolve bridge as confirm: the NanoHTTPD worker blocks in
    // askBlocking() until a tap (UI) or a voice match (service) resolves it. Serialized via askPending.
    private val askResult = ArrayBlockingQueue<AskAnswer>(1)
    private val askPending = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var pendingOptions: List<String> = emptyList()

    // Bind to a hash of ALL approved (non-token) args — mirrors the brain's argsHash so the app's
    // independent check is exactly as strong: an empty/partial approval can't authorize a populated
    // fire, and extra/changed/missing body keys are all rejected (key-order independent).
    private data class IssuedToken(val action: String, val argsHash: String, val exp: Long)
    // tokens the user actually approved — the app validates intents against THIS, not the brain.
    private val issuedTokens = java.util.concurrent.ConcurrentHashMap<String, IssuedToken>()
    private val tokenTtlMs = 75_000L

    /** Stable hash of a body's args, EXCLUDING "token" — sorted keys so order can't matter. */
    private fun argsHash(obj: JSONObject): String {
        val keys = ArrayList<String>()
        val it = obj.keys()
        while (it.hasNext()) { val k = it.next(); if (k != "token") keys.add(k) }
        keys.sort()
        val sb = StringBuilder()
        // JSONObject.quote self-delimits each pair, so no separator can collide with arg content
        for (k in keys) sb.append(JSONObject.quote(k)).append(':').append(JSONObject.quote(obj.opt(k)?.toString() ?: "null")).append(',')
        return MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    }

    // After an answer, let the success crab play once then settle to idle (don't hop forever).
    private val settle = Runnable {
        if (ui.value.clawd == ClawdState.Success) ui.value = ui.value.copy(clawd = ClawdState.Idle)
    }

    /** Non-destructive, action+args-bound, TTL check: does this token authorize firing (action, body)?
     *  The intent must match the approved action AND the approved params (independent of the brain).
     *  Does NOT consume — call [invalidateIssuedToken] only after the action actually succeeds. */
    fun validateIssuedToken(token: String, action: String, body: JSONObject): Boolean {
        val t = issuedTokens[token] ?: return false
        if (t.action != action || System.currentTimeMillis() > t.exp) return false
        return t.argsHash == argsHash(body) // full args must match — not just the approved subset
    }

    /** Burn a token after a SUCCESSFUL action (enforces single-use without burning failed attempts). */
    fun invalidateIssuedToken(token: String) {
        issuedTokens.remove(token)
    }

    fun clearIssuedTokens() = issuedTokens.clear()

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun showSummon() = onMain {
        main.removeCallbacks(settle)
        dismissed = false
        ui.value = OverlayUi(mode = OverlayMode.Summon, status = "Listening", clawd = ClawdState.Listening)
        attach()
        syncWindowMode()
    }

    fun transcript(text: String) = onMain { if (dismissed) return@onMain; ui.value = ui.value.copy(transcript = text, suggestions = emptyList()) }
    fun status(text: String, isAction: Boolean = false) = onMain {
        if (dismissed) return@onMain
        if (ui.value.mode == OverlayMode.Confirm || ui.value.mode == OverlayMode.Ask) return@onMain // don't clobber an active modal
        main.removeCallbacks(settle)
        // Recognized activities (maps/music/camera/…) play a composed scene; core work keeps the hero state.
        val sc = overlayScene(text)
        // The "show your work" feed is REAL actions only. Generic thinking/status churn stays a single
        // ActivityLine — otherwise the app's "Thinking…" and the brain's "thinking…" stack as two
        // near-identical lines. Dedup case-insensitively too.
        val steps = if (!isAction || text.isBlank() || ui.value.steps.lastOrNull().equals(text, ignoreCase = true)) ui.value.steps else (ui.value.steps + text).takeLast(4)
        ui.value = ui.value.copy(mode = OverlayMode.Summon, status = text, answer = "", scene = sc, suggestions = emptyList(), steps = steps, actionsSeen = ui.value.actionsSeen + if (isAction) 1 else 0, clawd = ClawdState.Working)
        attach()
        syncWindowMode()
    }
    fun answer(text: String) = onMain {
        if (dismissed) return@onMain
        main.removeCallbacks(settle)
        ui.value = ui.value.copy(mode = OverlayMode.Summon, answer = text, status = "", scene = "", steps = emptyList(), plannedSteps = emptyList(), actionsSeen = 0, clawd = ClawdState.Success)
        attach()
        syncWindowMode()
        main.postDelayed(settle, 2200)
    }

    /** Show tappable follow-up chips under the current answer (from the brain's `suggestions` event). */
    fun suggestions(items: List<String>) = onMain {
        if (dismissed || ui.value.mode != OverlayMode.Summon) return@onMain // only alongside an answer
        ui.value = ui.value.copy(suggestions = items.take(3))
    }

    /** Live mic level (0..1) while listening — drives the voice light's reactive glow. */
    fun amplitude(level: Float) = onMain {
        if (ui.value.clawd != ClawdState.Listening) return@onMain
        ui.value = ui.value.copy(amplitude = level)
    }

    /** Show an up-front plan for a multi-step task; each step checks off as real actions fire. */
    fun plan(steps: List<String>) = onMain {
        if (dismissed) return@onMain
        if (ui.value.mode == OverlayMode.Confirm || ui.value.mode == OverlayMode.Ask) return@onMain
        main.removeCallbacks(settle)
        ui.value = ui.value.copy(mode = OverlayMode.Summon, plannedSteps = steps.take(7), actionsSeen = 0, answer = "", status = "Here's the plan", scene = "", suggestions = emptyList(), clawd = ClawdState.Working)
        attach()
        syncWindowMode()
    }

    /** True while the popup is on screen (not dismissed) — guards auto-listen after a spoken answer. */
    fun isShowing(): Boolean = !dismissed

    /** After Clyde finishes speaking, drop back to Listening (keeping the answer visible) so the user
     *  can reply by voice without re-summoning — a continuous back-and-forth conversation. */
    fun listenAgain() = onMain {
        if (dismissed) return@onMain
        main.removeCallbacks(settle)
        ui.value = ui.value.copy(mode = OverlayMode.Summon, status = "Listening", steps = emptyList(), plannedSteps = emptyList(), actionsSeen = 0, clawd = ClawdState.Listening)
        attach()
        syncWindowMode()
    }
    fun hide() = onMain { dismissed = true; runCatching { onDismiss() }; main.removeCallbacks(settle); main.removeCallbacks(clearPointer); detachPointer(); ui.value = OverlayUi(); detach() } // clear state so the next summon never shows a stale frame

    /** Briefly show a pointing Clawd at SCREEN pixel (x,y) — the spot Clyde is about to tap — so device
     *  automation is visible/auditable (a Gemini-can't). NOT_TOUCHABLE, so it never intercepts the tap. */
    fun pointAt(xPx: Int, yPx: Int) = onMain {
        if (!Settings.canDrawOverlays(appCtx)) return@onMain
        main.removeCallbacks(clearPointer)
        detachPointer()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        val cv = ComposeView(appCtx)
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeViewModelStoreOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        cv.setContent { ClydeTheme { ClawdSceneView(sceneKey = "pointing", size = 54.dp) } }
        val box = (66 * appCtx.resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            box, box,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = xPx - box / 2
        params.y = yPx - box // Clawd sits just above the point, claw reaching down to it
        runCatching { wm.addView(cv, params); pointerView = cv }
        main.postDelayed(clearPointer, 850)
    }

    private fun detachPointer() {
        pointerView?.let { runCatching { wm.removeView(it) } }
        pointerView = null
    }

    /** Called on a NanoHTTPD worker thread — blocks until the user approves/denies.
     *  Serialized: a second concurrent confirm is denied so a token never flows to the wrong waiter. */
    fun confirmBlocking(summary: String, details: String?, action: String, params: JSONObject): Pair<Boolean, String?> {
        if (dismissed) return Pair(false, null) // user closed the popup → auto-deny instead of re-showing it
        if (!confirmPending.compareAndSet(false, true)) return Pair(false, null)
        pendingAction = action
        pendingParams = params
        confirmResult.clear()
        onMain {
            ui.value = ui.value.copy(mode = OverlayMode.Confirm, confirmSummary = summary, confirmDetails = details, confirmEffect = effectLine(action, params), clawd = ClawdState.Error)
            attach()
            syncWindowMode() // a confirm must be tappable — never pass touches through
        }
        return try {
            val r = confirmResult.poll(90, TimeUnit.SECONDS)
            when {
                r != null -> r // a tap/cancel delivered a result (it already released confirmPending)
                // poll timed out: claim the gate. If we win, nobody resolved → genuine timeout.
                confirmPending.compareAndSet(true, false) -> {
                    onMain { ui.value = ui.value.copy(mode = OverlayMode.Summon) } // clear stale confirm UI
                    Pair(false, null)
                }
                // we lost the CAS: a resolve/cancel committed just as we timed out — take ITS result
                // (so an approval that minted a token is reported to the brain, never dropped).
                else -> confirmResult.poll(2, TimeUnit.SECONDS) ?: Pair(false, null)
            }
        } catch (_: InterruptedException) {
            confirmPending.compareAndSet(true, false)
            Pair(false, null)
        }
    }

    /** Unpark a worker blocked in [confirmBlocking] with a denial — used on KILL / teardown so the
     *  NanoHTTPD thread returns immediately and confirmPending is released (no ~90s stuck window). */
    fun cancelPendingConfirm() {
        if (confirmPending.compareAndSet(true, false)) {
            confirmResult.offer(Pair(false, null))
        }
    }

    /** Ask the user ONE multiple-choice question — blocks the calling (NanoHTTPD worker) thread until
     *  they answer (tap or voice) or dismiss/timeout. Mirrors [confirmBlocking]; [onAskShown] tells the
     *  service to start listening for a spoken choice. */
    fun askBlocking(question: String, options: List<String>): AskAnswer {
        if (dismissed) return AskAnswer(-1, "", "", "dismiss", true) // popup closed → don't reopen
        if (!askPending.compareAndSet(false, true)) return AskAnswer(-1, "", "", "busy", true)
        pendingOptions = options
        askResult.clear()
        onMain {
            main.removeCallbacks(settle)
            ui.value = ui.value.copy(
                mode = OverlayMode.Ask, askQuestion = question, askOptions = options,
                askTranscript = "", askHighlight = -1, answer = "", status = "Listening", scene = "",
                clawd = ClawdState.Listening,
            )
            attach()
            syncWindowMode() // a question must be tappable — never pass touches through
            runCatching { onAskShown(question, options) } // service starts the voice-listen (after the UI is up)
        }
        return try {
            val r = askResult.poll(150, TimeUnit.SECONDS)
            when {
                r != null -> r // a tap/voice/cancel delivered a result (it already released askPending)
                askPending.compareAndSet(true, false) -> { clearAskUi(); runCatching { onAskClosed() }; AskAnswer(-1, "", "", "timeout", true) }
                else -> askResult.poll(2, TimeUnit.SECONDS) ?: AskAnswer(-1, "", "", "timeout", true)
            }
        } catch (_: InterruptedException) {
            askPending.compareAndSet(true, false)
            AskAnswer(-1, "", "", "timeout", true)
        }
    }

    /** Tap on an option chip → resolve with that option (via "tap"). */
    fun pickAsk(index: Int) {
        val opts = pendingOptions
        if (index in opts.indices) resolveAsk(AskAnswer(index, opts[index], opts[index], "tap", false))
    }

    /** Voice match (from the service): the user said [said], which resolved to option [index]. For the
     *  no-match / catch-all case the service passes the LAST option index and the verbatim [said]. */
    fun answerAskByVoice(index: Int, said: String) {
        val opts = pendingOptions
        if (index in opts.indices) resolveAsk(AskAnswer(index, opts[index], said, "voice", false))
    }

    /** Dismissed (tap-outside / back) → resolve cancelled so the brain's /ask returns promptly. */
    fun cancelAsk() = resolveAsk(AskAnswer(-1, "", "", "dismiss", true))

    /** Live feedback while the user speaks: the partial transcript and which option it currently matches. */
    fun updateAskTranscript(text: String, highlight: Int) = onMain {
        if (ui.value.mode != OverlayMode.Ask) return@onMain
        ui.value = ui.value.copy(askTranscript = text, askHighlight = highlight)
    }

    /** True while a question is on screen awaiting an answer (guards the service's voice-listen loop). */
    fun isAsking(): Boolean = askPending.get()

    private fun resolveAsk(answer: AskAnswer) {
        if (!askPending.compareAndSet(true, false)) return // first resolver wins; ignore late tap/voice
        askResult.offer(answer)
        onMain {
            ui.value = ui.value.copy(
                mode = OverlayMode.Summon, askQuestion = "", askOptions = emptyList(),
                askTranscript = "", askHighlight = -1,
                status = if (answer.cancelled) "Cancelled" else "Working",
                clawd = if (answer.cancelled) ClawdState.Idle else ClawdState.Working,
            )
            syncWindowMode()
        }
        runCatching { onAskClosed() } // stop the voice-listen
    }

    private fun clearAskUi() = onMain {
        if (ui.value.mode == OverlayMode.Ask)
            ui.value = ui.value.copy(mode = OverlayMode.Summon, askQuestion = "", askOptions = emptyList(), askTranscript = "", askHighlight = -1)
    }

    /** Unpark a worker blocked in [askBlocking] with a cancel — used on KILL / teardown. */
    fun cancelPendingAsk() {
        if (askPending.compareAndSet(true, false)) askResult.offer(AskAnswer(-1, "", "", "dismiss", true))
    }

    /** What the approval ACTUALLY authorizes, built from the params the token is bound to (never the
     *  brain's prose summary) — so the user reads the real URL / number / recipient, not a spoofable
     *  caption. A compromised or prompt-injected brain can't show one thing and authorize another. */
    private fun effectLine(action: String, p: JSONObject): String = when (action) {
        "open_url" -> "Opens  " + p.optString("url")
        "start_call" -> "Calls  " + p.optString("number").ifBlank { p.optString("to") }
        "send_sms" -> "Texts ${p.optString("to")}:  “${p.optString("body").take(80)}”"
        "share_text" -> "Shares to  " + p.optString("targetPackage").ifBlank { "another app" }
        "add_calendar_event" -> "Adds event  “${p.optString("title")}”"
        "delegate_to_gemini" -> "Asks Gemini:  “${p.optString("prompt").take(80)}”"
        "" -> ""
        else -> {
            // unknown/UI action → show the raw approved params so nothing the token grants is hidden
            val keys = ArrayList<String>()
            val iter = p.keys()
            while (iter.hasNext()) { val k = iter.next(); if (k != "token") keys.add(k) }
            keys.sort()
            keys.joinToString("   ") { k -> "$k=" + p.opt(k).toString().take(50) }
        }
    }

    private fun resolveConfirm(approved: Boolean) {
        if (!confirmPending.compareAndSet(true, false)) return // first tap only — ignore double/late taps
        val token = if (approved) randomToken() else null
        if (approved && token != null) {
            issuedTokens[token] = IssuedToken(pendingAction, argsHash(pendingParams), System.currentTimeMillis() + tokenTtlMs)
        }
        confirmResult.offer(Pair(approved, token))
        onMain {
            ui.value = ui.value.copy(mode = OverlayMode.Summon, status = if (approved) "Working" else "Cancelled", scene = "", clawd = if (approved) ClawdState.Working else ClawdState.Idle)
            syncWindowMode() // approved → resume touch-through automation; denied → stay interactive
        }
    }

    private fun attach() {
        if (view != null) return
        val cv = ComposeView(appCtx)
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeViewModelStoreOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        cv.setContent {
            ClydeTheme {
                OverlayRoot(
                    ui = ui.value,
                    onApprove = { resolveConfirm(true) },
                    onDeny = { resolveConfirm(false) },
                    onClose = { hide() },
                    onMic = { onMic() },
                    onSend = { onSend(it) },
                    onAskPick = { pickAsk(it) },
                    onAskCancel = { cancelAsk() },
                    onSuggestion = { onSend(it) }, // a tapped follow-up chip is just a pre-filled message
                )
            }
        }
        // Back gesture / back button dismisses the popup (the focusable window receives the key);
        // in a confirm, back denies — same as tapping outside.
        cv.isFocusableInTouchMode = true
        cv.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                when (ui.value.mode) {
                    OverlayMode.Confirm -> resolveConfirm(false)
                    OverlayMode.Ask -> cancelAsk()
                    else -> hide()
                }
                true
            } else false
        }
        try {
            wm.addView(cv, buildParams())
            view = cv
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        } catch (e: Exception) {
            Log.e("Clyde", "overlay addView failed; SYSTEM_ALERT_WINDOW may not be granted", e)
        }
    }

    /** Window flags for the current mode. When [passthrough] (Clyde is driving the screen) the window
     *  is NOT_FOCUSABLE + NOT_TOUCHABLE so injected taps reach the app behind and Clyde never reads or
     *  taps its own glass; otherwise it's interactive (focusable + dim) for voice/typing/approve. */
    private fun buildParams(): WindowManager.LayoutParams {
        val flags = if (passthrough)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        else
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        )
        // Extend the window THROUGH the camera cutout/notch so it reports the real cutout insets — this
        // is what lets Clawd free-fall from the actual front camera (else the cutout is null and the drop
        // starts from a generic top-center point below the status bar, i.e. "not from the camera").
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        if (!passthrough) {
            // Focusable so the typed-message field can raise the keyboard; ADJUST_RESIZE lifts the panel
            // above it. Voice stays first — the keyboard only appears if the user taps the text field.
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
            params.dimAmount = 0.30f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    params.blurBehindRadius = (28 * appCtx.resources.displayMetrics.density).toInt()
                } catch (_: Exception) {
                }
            }
        }
        return params
    }

    /** Pass touches through to the app ONLY while actively working (driving the screen); interactive
     *  in Listening / Confirm / answer so the user can speak, type, or approve. */
    private fun syncWindowMode() {
        val u = ui.value
        setPassthrough(u.mode == OverlayMode.Summon && u.clawd == ClawdState.Working)
    }

    private fun setPassthrough(p: Boolean) {
        if (passthrough == p) return
        passthrough = p
        val cv = view ?: return
        runCatching { wm.updateViewLayout(cv, buildParams()) }
    }

    /** Hide Clyde's own glass for ONE screenshot so the capture shows the app, not Clyde's panel.
     *  Blocks the calling (worker) thread briefly until the window manager has recomposited without us. */
    fun beginCapture() {
        val latch = java.util.concurrent.CountDownLatch(1)
        onMain {
            val cv = view
            if (cv == null) { latch.countDown(); return@onMain }
            cv.visibility = View.INVISIBLE
            (cv.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
                lp.dimAmount = 0f
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv() and
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                runCatching { wm.updateViewLayout(cv, lp) }
            }
            cv.post { cv.post { latch.countDown() } } // two frames → our content is gone from the buffer
        }
        runCatching { latch.await(400, TimeUnit.MILLISECONDS) }
    }

    fun endCapture() = onMain {
        val cv = view ?: return@onMain
        cv.visibility = View.VISIBLE
        runCatching { wm.updateViewLayout(cv, buildParams()) }
    }

    private fun detach() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        cancelPendingConfirm() // release any worker parked in confirmBlocking() before tearing down
        main.removeCallbacks(clearPointer)
        detachPointer()
        detach()
        clearIssuedTokens()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun randomToken(): String {
        val b = ByteArray(6)
        SecureRandom().nextBytes(b)
        return "tok_" + b.joinToString("") { "%02x".format(it) }
    }
}

// ─────────────────────────── overlay UI ───────────────────────────

@Composable
private fun OverlayRoot(ui: OverlayUi, onApprove: () -> Unit, onDeny: () -> Unit, onClose: () -> Unit, onMic: () -> Unit, onSend: (String) -> Unit, onAskPick: (Int) -> Unit, onAskCancel: () -> Unit, onSuggestion: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        // tap-outside: dismiss (summon) / deny (confirm) / cancel (ask)
        Box(
            Modifier.fillMaxSize().clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ) {
                when (ui.mode) {
                    OverlayMode.Confirm -> onDeny()
                    OverlayMode.Ask -> onAskCancel()
                    else -> onClose()
                }
            }
        )
        when (ui.mode) {
            OverlayMode.Summon -> SummonPanel(ui, onMic, onSend, onSuggestion)
            OverlayMode.Confirm -> ConfirmPanel(ui, onApprove, onDeny)
            OverlayMode.Ask -> AskPanel(ui, onAskPick)
            OverlayMode.Hidden -> {}
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SummonPanel(ui: OverlayUi, onMic: () -> Unit, onSend: (String) -> Unit, onSuggestion: (String) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { shown = true }
    val anim by animateFloatAsState(if (shown) 1f else 0f, tween(if (reduceMotion()) 0 else 300), label = "summon")
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(12.dp)
            .graphicsLayer { translationY = (1f - anim) * 60f; alpha = anim },
    ) {
        // Clawd free-falls from the device's real camera cutout and lands (with a bounce) on the box edge.
        ClawdPerch(ui)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 26.dp)
                .background(ClydeColor.Paper, RoundedCornerShape(22.dp))
                .border(1.dp, ClydeColor.Line2, RoundedCornerShape(22.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Clyde", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ClydeColor.Ink)
                Spacer(Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_claude_mark), contentDescription = null, tint = ClydeColor.TerracottaDeep, modifier = Modifier.size(11.dp))
                Spacer(Modifier.size(4.dp))
                Text("Claude", fontFamily = Mono, fontSize = 10.sp, color = ClydeColor.TerracottaDeep)
            }
            Spacer(Modifier.height(8.dp))
            // During a multi-step task, show the recent steps ("show your work"); otherwise one status line.
            if (ui.clawd == ClawdState.Working && (ui.plannedSteps.isNotEmpty() || ui.steps.size > 1)) StepsFeed(ui) else ActivityLine(ui)
            Spacer(Modifier.height(10.dp))
            when {
                ui.answer.isNotBlank() -> Row(Modifier.height(IntrinsicSize.Min)) {
                    // terracotta rule — the "Claude is speaking" mark
                    Box(Modifier.width(3.dp).fillMaxHeight().background(ClydeColor.Terracotta, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.size(12.dp))
                    Text(ui.answer, fontFamily = Serif, fontSize = 16.sp, lineHeight = 23.sp, color = ClydeColor.Ink)
                }
                ui.transcript.isNotBlank() -> Text(ui.transcript, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = ClydeColor.Ink)
                else -> {} // the ActivityLine above carries the live status now
            }
            // Follow-up suggestion chips under an answer — tap to ask the next thing (rich-response parity).
            if (ui.answer.isNotBlank() && ui.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.suggestions.forEach { s ->
                        Box(
                            Modifier
                                .background(ClydeColor.Panel2, RoundedCornerShape(999.dp))
                                .border(1.dp, ClydeColor.Blue, RoundedCornerShape(999.dp))
                                .pressable(label = s) { onSuggestion(s) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(s, fontFamily = Body, fontSize = 13.sp, color = ClydeColor.Blue, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().background(ClydeColor.Panel2, RoundedCornerShape(999.dp))
                    .border(1.dp, ClydeColor.Blue, RoundedCornerShape(999.dp)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoiceLight(if (ui.clawd == ClawdState.Listening) ui.amplitude else 0f)
                Spacer(Modifier.size(10.dp))
                // Type a message OR tap the mic. The button is Send when there's text, else the mic
                // (which stops any speech and listens again — so it never just closes the popup).
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Body, fontSize = 14.sp, color = ClydeColor.Ink),
                    cursorBrush = SolidColor(ClydeColor.Blue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        val t = text.trim(); if (t.isNotEmpty()) { onSend(t); text = "" }
                    }),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Ask, or type a message", fontFamily = Body, fontSize = 14.sp, color = ClydeColor.Muted)
                        inner()
                    },
                )
                Spacer(Modifier.size(8.dp))
                val t = text.trim()
                Box(
                    Modifier.size(36.dp).background(ClydeColor.Blue, RoundedCornerShape(18.dp))
                        .pressable(label = if (t.isEmpty()) "Listen again" else "Send") {
                            if (t.isEmpty()) onMic() else { onSend(t); text = "" }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (t.isEmpty()) Icon(painterResource(R.drawable.ic_mic), contentDescription = "listen again", tint = Color(0xFF06303C), modifier = Modifier.size(18.dp))
                    else Text("↑", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF06303C))
                }
            }
        }
    }
}

/** Clawd dropping from the device's real camera cutout onto the box edge. Reads the top-most
 *  DisplayCutout bounding rect (the punch-hole/notch) for the camera X/Y; free-falls there with a
 *  spring bounce on first appearance, then rests on the perch as the live scene/state. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ClawdPerch(ui: OverlayUi) {
    val view = LocalView.current
    val reduce = reduceMotion()
    var rest by remember { mutableStateOf(Offset.Zero) }
    var camera by remember { mutableStateOf(Offset.Zero) }
    var placed by remember { mutableStateOf(false) }
    val drop = remember { Animatable(1f) } // 1 = at the camera, 0 = landed on the perch
    LaunchedEffect(placed) {
        if (placed) {
            if (reduce) drop.snapTo(0f) else drop.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 240f))
        }
    }
    val mod = Modifier
        .align(Alignment.TopCenter)
        .offset(y = (-30).dp)
        .onGloballyPositioned { coords ->
            rest = coords.positionInWindow()
            if (!placed) {
                // insets are settled by layout time — find the camera (top-most cutout rect)
                val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) view.rootWindowInsets?.displayCutout else null
                val r = cutout?.boundingRects?.minByOrNull { it.top }
                camera = if (r != null) Offset(r.exactCenterX(), r.exactCenterY())
                else Offset((if (view.width > 0) view.width else 1080) / 2f, 0f) // top-center fallback
                placed = true
            }
        }
        .graphicsLayer {
            if (placed && drop.value != 0f) {
                val cx = rest.x + size.width / 2f
                val cy = rest.y + size.height / 2f
                translationX = (camera.x - cx) * drop.value
                translationY = (camera.y - cy) * drop.value
                rotationZ = -10f * drop.value // a small tumble while falling, settles upright
            }
        }
    if (ui.scene.isNotBlank()) ClawdSceneView(sceneKey = ui.scene, size = 58.dp, modifier = mod)
    else ClawdView(state = ui.clawd, size = 58.dp, modifier = mod)
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ConfirmPanel(ui: OverlayUi, onApprove: () -> Unit, onDeny: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val anim by animateFloatAsState(if (shown) 1f else 0f, tween(if (reduceMotion()) 0 else 300), label = "confirm")
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)
            .graphicsLayer { translationY = (1f - anim) * 60f; alpha = anim },
    ) {
        ClawdView(state = ClawdState.Error, size = 54.dp, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-28).dp))
        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp)
                .background(ClydeColor.Paper, RoundedCornerShape(22.dp))
                .border(2.dp, ClydeColor.Terracotta, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Text("CLYDE WANTS TO", fontFamily = Mono, fontSize = 10.sp, color = ClydeColor.Muted)
            Spacer(Modifier.height(8.dp))
            Text(ui.confirmSummary, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp, color = ClydeColor.Ink)
            if (!ui.confirmDetails.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(ui.confirmDetails, fontFamily = Body, fontSize = 13.sp, color = ClydeColor.Muted)
            }
            if (ui.confirmEffect.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .background(ClydeColor.Panel2, RoundedCornerShape(10.dp))
                        .border(1.dp, ClydeColor.Line2, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    Text("EXACTLY WHAT HAPPENS", fontFamily = Mono, fontSize = 9.sp, color = ClydeColor.Muted)
                    Spacer(Modifier.height(4.dp))
                    Text(ui.confirmEffect, fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp, color = ClydeColor.Ink)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).border(1.dp, ClydeColor.Line2, RoundedCornerShape(11.dp)).pressable(label = "Don't") { onDeny() }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Don't", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ClydeColor.Muted) }
                Box(
                    Modifier.weight(1.4f).background(ClydeColor.Blue, RoundedCornerShape(11.dp)).pressable(label = "Approve") { onApprove() }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Approve", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF06303C)) }
            }
        }
    }
}

/** ask_user: one question + tappable numbered options, answerable by voice or tap. Blue (live/listening)
 *  rather than terracotta (confirm) — a question isn't a consequence. The last option is the free-form
 *  catch-all: if the user says something matching no option, the service routes it here with their words.
 *  [ui.askHighlight] highlights the option the live speech currently matches. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.AskPanel(ui: OverlayUi, onPick: (Int) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val anim by animateFloatAsState(if (shown) 1f else 0f, tween(if (reduceMotion()) 0 else 300), label = "ask")
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)
            .graphicsLayer { translationY = (1f - anim) * 60f; alpha = anim },
    ) {
        ClawdView(state = ClawdState.Listening, size = 54.dp, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-28).dp))
        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp)
                .background(ClydeColor.Paper, RoundedCornerShape(22.dp))
                .border(1.dp, ClydeColor.Blue, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Text("CLYDE ASKS", fontFamily = Mono, fontSize = 10.sp, color = ClydeColor.Muted)
            Spacer(Modifier.height(8.dp))
            Text(ui.askQuestion, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp, color = ClydeColor.Ink)
            Spacer(Modifier.height(14.dp))
            ui.askOptions.forEachIndexed { i, opt ->
                val hot = i == ui.askHighlight
                val isOther = i == ui.askOptions.lastIndex && ui.askOptions.size >= 2
                val onBlue = Color(0xFF06303C)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .background(if (hot) ClydeColor.Blue else ClydeColor.Panel2, RoundedCornerShape(12.dp))
                        .border(1.dp, if (hot) ClydeColor.Blue else ClydeColor.Line2, RoundedCornerShape(12.dp))
                        .pressable(label = opt) { onPick(i) }
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(22.dp)
                            .background(if (hot) onBlue else ClydeColor.Paper, RoundedCornerShape(11.dp))
                            .border(1.dp, if (hot) onBlue else ClydeColor.Line2, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (hot) ClydeColor.Blue else ClydeColor.Muted) }
                    Spacer(Modifier.size(11.dp))
                    Text(
                        opt, fontFamily = Body, fontSize = 15.sp,
                        fontWeight = if (hot) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (hot) onBlue else ClydeColor.Ink,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (isOther) {
                        Spacer(Modifier.size(8.dp))
                        Text("say anything", fontFamily = Mono, fontSize = 9.sp, color = if (hot) onBlue else ClydeColor.Muted)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            // Listening indicator + live transcript so the user sees they can talk (and what's heard).
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoiceLight(ui.amplitude)
                Spacer(Modifier.size(10.dp))
                val heard = ui.askTranscript.isNotBlank()
                Text(
                    if (heard) ui.askTranscript else "Listening… say an option, or tap one",
                    fontFamily = if (heard) Display else Mono,
                    fontWeight = if (heard) FontWeight.Medium else FontWeight.Normal,
                    fontSize = if (heard) 15.sp else 11.sp,
                    color = if (heard) ClydeColor.Ink else ClydeColor.Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Claude-Code-style live status: a state dot + one short line of exactly what Clyde is doing right
 *  now (listening / thinking / the current action), so it's never a mystery before the screen changes. */
@Composable
private fun ActivityLine(ui: OverlayUi) {
    val (label, color, active) = when (ui.clawd) {
        ClawdState.Listening -> Triple("Listening…", ClydeColor.Blue, true)
        ClawdState.Working, ClawdState.Navigating -> Triple(ui.status.ifBlank { "Working…" }, ClydeColor.Blue, true)
        ClawdState.Success -> Triple("Done", Color(0xFF788C5D), false)
        ClawdState.Error -> Triple(ui.status.ifBlank { "Something went wrong" }, ClydeColor.Terracotta, false)
        ClawdState.Idle -> Triple(ui.status.ifBlank { "Ready" }, ClydeColor.Muted, false)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulseDot(color, active)
        Spacer(Modifier.size(7.dp))
        Text(label, fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Show your work": the last few actions Clyde has taken this turn — prior steps marked done (green),
 *  the current one pulsing — step-by-step transparency during a multi-step task. */
@Composable
private fun StepsFeed(ui: OverlayUi) {
    // With an up-front plan, render it and check steps off by how many real actions have fired (approximate
    // — a step can take several actions); otherwise the recent-actions "show your work" feed.
    val planned = ui.plannedSteps
    if (planned.isNotEmpty()) {
        val current = (ui.actionsSeen - 1).coerceIn(0, planned.lastIndex)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            planned.forEachIndexed { i, s ->
                val done = ui.actionsSeen > planned.size || i < current
                val isCurrent = !done && i == current && ui.actionsSeen >= 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        done -> Text("✓", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF788C5D))
                        isCurrent -> PulseDot(ClydeColor.Blue, true)
                        else -> Text("○", fontFamily = Mono, fontSize = 10.sp, color = ClydeColor.Muted)
                    }
                    Spacer(Modifier.size(7.dp))
                    Text(s, fontFamily = Mono, fontSize = 11.sp, color = if (isCurrent) ClydeColor.Ink else ClydeColor.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ui.steps.forEachIndexed { i, s ->
            val latest = i == ui.steps.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (latest) PulseDot(ClydeColor.Blue, true)
                else Box(Modifier.size(6.dp).background(Color(0xFF788C5D), RoundedCornerShape(999.dp)))
                Spacer(Modifier.size(7.dp))
                Text(s, fontFamily = Mono, fontSize = 11.sp, color = if (latest) ClydeColor.Ink else ClydeColor.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PulseDot(color: Color, active: Boolean) {
    if (!active || reduceMotion()) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(999.dp)))
        return
    }
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dotA")
    Box(
        Modifier.size(8.dp)
            .graphicsLayer { alpha = a; scaleX = 0.7f + 0.3f * a; scaleY = 0.7f + 0.3f * a }
            .background(color, RoundedCornerShape(999.dp)),
    )
}

/** Voice-as-light: a blue pool that breathes (amplitude stand-in), not a literal waveform. */
@Composable
private fun VoiceLight(amplitude: Float = 0f) {
    if (reduceMotion()) {
        Box(Modifier.width(40.dp).height(16.dp).background(ClydeColor.Blue, RoundedCornerShape(999.dp)))
        return
    }
    val t = rememberInfiniteTransition(label = "voice")
    // Idle breathe keeps it alive; the real mic level takes over and dominates when the user is speaking.
    val idle by t.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(820), RepeatMode.Reverse),
        label = "voiceIdle",
    )
    val live by animateFloatAsState(amplitude.coerceIn(0f, 1f), tween(120), label = "voiceAmp")
    val a = maxOf(idle, 0.45f + 0.55f * live)
    Box(
        Modifier
            .width(40.dp)
            .height(16.dp)
            .graphicsLayer { alpha = a; scaleX = 0.78f + 0.22f * a }
            .background(ClydeColor.Blue, RoundedCornerShape(999.dp)),
    )
}
