package dev.kris.clyde.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
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

enum class OverlayMode { Hidden, Summon, Confirm }

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
)

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
    private val ui = mutableStateOf(OverlayUi())
    private val confirmResult = ArrayBlockingQueue<Pair<Boolean, String?>>(1)
    private val confirmPending = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var pendingAction: String = ""
    @Volatile private var pendingParams: JSONObject = JSONObject()

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
        ui.value = OverlayUi(mode = OverlayMode.Summon, status = "Listening", clawd = ClawdState.Listening)
        attach()
    }

    fun transcript(text: String) = onMain { ui.value = ui.value.copy(transcript = text) }
    fun status(text: String) = onMain {
        main.removeCallbacks(settle)
        // Recognized activities (maps/music/camera/…) play a composed scene; core work keeps the hero state.
        val sc = overlayScene(text)
        ui.value = ui.value.copy(mode = OverlayMode.Summon, status = text, answer = "", scene = sc, clawd = ClawdState.Working)
        attach()
    }
    fun answer(text: String) = onMain {
        main.removeCallbacks(settle)
        ui.value = ui.value.copy(mode = OverlayMode.Summon, answer = text, status = "", scene = "", clawd = ClawdState.Success)
        attach()
        main.postDelayed(settle, 2200)
    }
    fun hide() = onMain { main.removeCallbacks(settle); ui.value = OverlayUi(); detach() } // clear state so the next summon never shows a stale frame

    /** Called on a NanoHTTPD worker thread — blocks until the user approves/denies.
     *  Serialized: a second concurrent confirm is denied so a token never flows to the wrong waiter. */
    fun confirmBlocking(summary: String, details: String?, action: String, params: JSONObject): Pair<Boolean, String?> {
        if (!confirmPending.compareAndSet(false, true)) return Pair(false, null)
        pendingAction = action
        pendingParams = params
        confirmResult.clear()
        onMain {
            ui.value = ui.value.copy(mode = OverlayMode.Confirm, confirmSummary = summary, confirmDetails = details, confirmEffect = effectLine(action, params), clawd = ClawdState.Error)
            attach()
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
        onMain { ui.value = ui.value.copy(mode = OverlayMode.Summon, status = if (approved) "Working" else "Cancelled", scene = "", clawd = if (approved) ClawdState.Working else ClawdState.Idle) }
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
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        )
        params.dimAmount = 0.30f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.blurBehindRadius = (28 * appCtx.resources.displayMetrics.density).toInt()
            } catch (_: Exception) {
            }
        }
        try {
            wm.addView(cv, params)
            view = cv
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        } catch (e: Exception) {
            Log.e("Clyde", "overlay addView failed; SYSTEM_ALERT_WINDOW may not be granted", e)
        }
    }

    private fun detach() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        cancelPendingConfirm() // release any worker parked in confirmBlocking() before tearing down
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
private fun OverlayRoot(ui: OverlayUi, onApprove: () -> Unit, onDeny: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        // tap-outside: dismiss (summon) / deny (confirm)
        Box(
            Modifier.fillMaxSize().clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ) { if (ui.mode == OverlayMode.Confirm) onDeny() else onClose() }
        )
        when (ui.mode) {
            OverlayMode.Summon -> SummonPanel(ui, onClose)
            OverlayMode.Confirm -> ConfirmPanel(ui, onApprove, onDeny)
            OverlayMode.Hidden -> {}
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SummonPanel(ui: OverlayUi, onClose: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
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
                Text("✦ Claude", fontFamily = Mono, fontSize = 10.sp, color = ClydeColor.TerracottaDeep)
            }
            Spacer(Modifier.height(10.dp))
            when {
                ui.answer.isNotBlank() -> Row(Modifier.height(IntrinsicSize.Min)) {
                    // terracotta rule — the "Claude is speaking" mark
                    Box(Modifier.width(3.dp).fillMaxHeight().background(ClydeColor.Terracotta, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.size(12.dp))
                    Text(ui.answer, fontFamily = Serif, fontSize = 16.sp, lineHeight = 23.sp, color = ClydeColor.Ink)
                }
                ui.transcript.isNotBlank() -> Text(ui.transcript, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = ClydeColor.Ink)
                else -> Text(ui.status.ifBlank { "Listening" }, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = ClydeColor.Muted)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().background(ClydeColor.Panel2, RoundedCornerShape(999.dp))
                    .border(1.dp, ClydeColor.Blue, RoundedCornerShape(999.dp)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoiceLight()
                Spacer(Modifier.size(10.dp))
                Text(ui.status.ifBlank { "Listening" }, fontFamily = Body, fontSize = 14.sp, color = ClydeColor.Ink, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).background(ClydeColor.Blue, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, contentDescription = "mic", tint = Color(0xFF06303C), modifier = Modifier.size(18.dp))
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

/** Voice-as-light: a blue pool that breathes (amplitude stand-in), not a literal waveform. */
@Composable
private fun VoiceLight() {
    if (reduceMotion()) {
        Box(Modifier.width(40.dp).height(16.dp).background(ClydeColor.Blue, RoundedCornerShape(999.dp)))
        return
    }
    val t = rememberInfiniteTransition(label = "voice")
    val a by t.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(820), RepeatMode.Reverse),
        label = "voiceA",
    )
    Box(
        Modifier
            .width(40.dp)
            .height(16.dp)
            .graphicsLayer { alpha = a; scaleX = 0.78f + 0.22f * a }
            .background(ClydeColor.Blue, RoundedCornerShape(999.dp)),
    )
}
