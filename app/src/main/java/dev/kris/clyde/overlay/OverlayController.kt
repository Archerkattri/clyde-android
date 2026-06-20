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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
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
import java.security.SecureRandom
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit

enum class OverlayMode { Hidden, Summon, Confirm }

data class OverlayUi(
    val mode: OverlayMode = OverlayMode.Hidden,
    val transcript: String = "",
    val status: String = "",
    val answer: String = "",
    val clawd: ClawdState = ClawdState.Idle,
    val confirmSummary: String = "",
    val confirmDetails: String? = null,
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
    private val confirmResult = SynchronousQueue<Pair<Boolean, String?>>()
    // tokens the user actually approved — the app validates intents against THIS, not the brain.
    private val issuedTokens = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val tokenTtlMs = 75_000L

    /** Single-use, TTL-bounded consume of an app-issued confirm token. */
    fun consumeIssuedToken(token: String): Boolean {
        val exp = issuedTokens.remove(token) ?: return false
        return System.currentTimeMillis() <= exp
    }

    fun clearIssuedTokens() = issuedTokens.clear()

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun showSummon() = onMain {
        ui.value = OverlayUi(mode = OverlayMode.Summon, status = "Listening", clawd = ClawdState.Listening)
        attach()
    }

    fun transcript(text: String) = onMain { ui.value = ui.value.copy(transcript = text) }
    fun status(text: String) = onMain { ui.value = ui.value.copy(mode = OverlayMode.Summon, status = text, answer = "", clawd = ClawdState.Working); attach() }
    fun answer(text: String) = onMain { ui.value = ui.value.copy(mode = OverlayMode.Summon, answer = text, status = "", clawd = ClawdState.Success); attach() }
    fun hide() = onMain { detach() }

    /** Called on a NanoHTTPD worker thread — blocks until the user approves/denies. */
    fun confirmBlocking(summary: String, details: String?): Pair<Boolean, String?> {
        onMain {
            ui.value = ui.value.copy(mode = OverlayMode.Confirm, confirmSummary = summary, confirmDetails = details, clawd = ClawdState.Error)
            attach()
        }
        return try {
            confirmResult.poll(90, TimeUnit.SECONDS) ?: Pair(false, null)
        } catch (_: InterruptedException) {
            Pair(false, null)
        }
    }

    private fun resolveConfirm(approved: Boolean) {
        val token = if (approved) randomToken() else null
        if (approved && token != null) issuedTokens[token] = System.currentTimeMillis() + tokenTtlMs
        confirmResult.offer(Pair(approved, token))
        onMain { ui.value = ui.value.copy(mode = OverlayMode.Summon, status = if (approved) "Working" else "Cancelled", clawd = if (approved) ClawdState.Working else ClawdState.Idle) }
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
            Log.e("Clyde", "overlay addView failed (SYSTEM_ALERT_WINDOW granted?)", e)
        }
    }

    private fun detach() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
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
    val anim by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "summon")
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(12.dp)
            .graphicsLayer { translationY = (1f - anim) * 60f; alpha = anim },
    ) {
        // Clawd perched on the top edge
        ClawdView(
            state = ui.clawd,
            size = 58.dp,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-30).dp),
        )
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
                ui.answer.isNotBlank() -> Text(ui.answer, fontFamily = Serif, fontSize = 16.sp, lineHeight = 23.sp, color = ClydeColor.Ink)
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

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ConfirmPanel(ui: OverlayUi, onApprove: () -> Unit, onDeny: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val anim by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "confirm")
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
            Text("CLYDE WANTS TO", fontFamily = Mono, fontSize = 10.sp, color = ClydeColor.Faint)
            Spacer(Modifier.height(8.dp))
            Text(ui.confirmSummary, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp, color = ClydeColor.Ink)
            if (!ui.confirmDetails.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(ui.confirmDetails, fontFamily = Body, fontSize = 13.sp, color = ClydeColor.Muted)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).border(1.dp, ClydeColor.Line2, RoundedCornerShape(11.dp)).clickable { onDeny() }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Don't", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ClydeColor.Muted) }
                Box(
                    Modifier.weight(1.4f).background(ClydeColor.Blue, RoundedCornerShape(11.dp)).clickable { onApprove() }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Approve", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF06303C)) }
            }
        }
    }
}

/** Voice-as-light: a blue pool that breathes (amplitude stand-in), not a literal waveform. */
@Composable
private fun VoiceLight() {
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
