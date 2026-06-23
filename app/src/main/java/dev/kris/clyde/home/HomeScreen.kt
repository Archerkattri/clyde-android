package dev.kris.clyde.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kris.clyde.bridge.BrainClient
import dev.kris.clyde.caps.CapabilityProbe
import dev.kris.clyde.overlay.ClawdSceneView
import dev.kris.clyde.runtime.EmbeddedRuntime
import dev.kris.clyde.runtime.CodexAuth
import dev.kris.clyde.bridge.TermuxRunCommand
import dev.kris.clyde.router.GeminiRouter
import dev.kris.clyde.service.AgentOrchestratorService
import dev.kris.clyde.wake.WakeWordService
import dev.kris.clyde.ui.Body
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.ClydeLogo
import dev.kris.clyde.ui.Display
import dev.kris.clyde.ui.Eyebrow
import dev.kris.clyde.ui.FitToScreen
import dev.kris.clyde.ui.Mono
import dev.kris.clyde.ui.PrimaryButton
import dev.kris.clyde.ui.pressable
import dev.kris.clyde.ui.reduceMotion
import dev.kris.clyde.util.Prefs
import kotlinx.coroutines.launch

/** Panel 08 — Home / control center. */
@Composable
fun HomeScreen(onAsk: () -> Unit, onConnectBrain: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val caps = CapabilityProbe.rememberCaps()
    val accessibilityOn = caps?.accessibility == true
    val shizukuOn = caps?.shizuku == true
    val rootOn = caps?.root == true
    var online by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) { online = BrainClient.healthz() }

    FitToScreen {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Clawd is the live status face: calm idle when the brain is online, a little down when
            // it's offline, thinking while the first health check is still in flight.
            ClawdSceneView(sceneKey = when (online) { true -> "idle"; false -> "sad"; null -> "thinking" }, size = 44.dp)
            Spacer(Modifier.size(8.dp))
            Row {
                Text("Clyde", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 27.sp, letterSpacing = (-0.03).em, color = ClydeColor.Ink)
                Text(".", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 27.sp, color = ClydeColor.Blue)
            }
            Spacer(Modifier.weight(1f))
            val statusText = when (online) { true -> "online"; false -> "brain offline"; null -> "…" }
            val statusColor = if (online == true) ClydeColor.BlueText else ClydeColor.Muted
            Text(statusText, fontFamily = Mono, fontSize = 11.sp, color = statusColor)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Claude, with hands. Ask anywhere; you approve what matters.",
            fontFamily = Body,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = ClydeColor.Muted,
        )

        // Deferred-setup breadcrumb: if the brain isn't reachable, give a real way back to finish it.
        if (online == false) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClydeColor.BlueTint, RoundedCornerShape(12.dp))
                    .border(1.dp, ClydeColor.Blue.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .pressable(label = "Finish connecting the brain") { onConnectBrain() }
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Finish connecting the brain", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ClydeColor.Ink)
                    Text("Clyde can't think until its brain is online", fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
                }
                Text("Set up", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClydeColor.BlueText)
            }
        }

        Spacer(Modifier.height(20.dp))
        Eyebrow("what clyde can do")
        Spacer(Modifier.height(6.dp))
        CapabilityRow("Apps, alarms, timers, navigation", "built-in", live = true, on = true)
        CapabilityRow("See & tap the screen", if (accessibilityOn) "on" else "tap to enable", live = accessibilityOn, on = accessibilityOn)
        CapabilityRow("Type, install, change settings", if (shizukuOn) "on" else "off", live = shizukuOn, on = shizukuOn)
        CapabilityRow("Unrestricted (root)", if (rootOn) "on" else "off", live = rootOn, on = rootOn)

        // The Termux brain-key card is only meaningful on the COMPANION build; the embedded build
        // passes the key to the in-app brain via env, so hide it there (no Termux to sync to).
        if (!EmbeddedRuntime.isBundled(ctx)) {
        Spacer(Modifier.height(16.dp))
        Eyebrow("brain key")
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClydeColor.Panel2, RoundedCornerShape(12.dp))
                .border(1.dp, ClydeColor.Line, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Pair the brain", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ClydeColor.Ink)
                Text(Prefs.clydeKey.take(10) + "…", fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
            }
            Text(
                "Copy",
                fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClydeColor.BlueText,
                modifier = Modifier.pressable(label = "Copy brain key") {
                    val clip = ctx.getSystemService(ClipboardManager::class.java)
                    clip?.setPrimaryClip(ClipData.newPlainText("CLYDE_KEY", Prefs.clydeKey))
                }.padding(8.dp),
            )
            Text(
                "Sync",
                fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClydeColor.BlueText,
                modifier = Modifier.pressable(label = "Sync key to Termux") {
                    val k = Prefs.clydeKey
                    TermuxRunCommand.runInTermux(
                        ctx,
                        "mkdir -p ~/clyde/brain; touch ~/clyde/brain/.env; " +
                            "if grep -q '^CLYDE_KEY=' ~/clyde/brain/.env; then " +
                            "sed -i \"s/^CLYDE_KEY=.*/CLYDE_KEY=$k/\" ~/clyde/brain/.env; " +
                            "else echo \"CLYDE_KEY=$k\" >> ~/clyde/brain/.env; fi",
                        background = true,
                    )
                }.padding(8.dp),
            )
        }
        } // end: brain-key card (companion build only)

        Spacer(Modifier.height(16.dp))
        Eyebrow("brain")
        Spacer(Modifier.height(6.dp))
        var backend by remember { mutableStateOf(Prefs.backend) }
        Segmented(
            options = listOf("claude" to "Claude", "codex" to "Codex"),
            selected = backend,
            onSelect = { backend = it; Prefs.backend = it },
        )
        Spacer(Modifier.height(8.dp))
        Eyebrow("model")
        Spacer(Modifier.height(6.dp))
        var claudeModel by remember { mutableStateOf(Prefs.assistantModel) }
        var codexModel by remember { mutableStateOf(Prefs.codexModel) }
        val models = if (backend == "codex")
            listOf("gpt-5.4" to "GPT-5.4", "gpt-5.4-mini" to "Mini", "gpt-5.3-codex" to "Codex")
        else
            listOf("opus" to "Opus", "sonnet" to "Sonnet", "haiku" to "Haiku")
        Segmented(
            options = models,
            selected = if (backend == "codex") codexModel else claudeModel,
            onSelect = {
                if (backend == "codex") { codexModel = it; Prefs.codexModel = it }
                else { claudeModel = it; Prefs.assistantModel = it }
            },
        )
        Spacer(Modifier.height(5.dp))
        Text(
            if (backend == "codex") "Codex runs on your ChatGPT subscription · GPT-5.4 default"
            else "Opus — most capable · Sonnet — balanced · Haiku — fastest",
            fontFamily = Mono, fontSize = 10.sp, color = ClydeColor.Muted,
        )
        if (backend == "codex") {
            Spacer(Modifier.height(10.dp))
            CodexSignIn()
        }

        Spacer(Modifier.height(16.dp))
        Eyebrow("hands-free")
        Spacer(Modifier.height(6.dp))
        var wake by remember { mutableStateOf(Prefs.wakeWord) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClydeColor.Panel2, RoundedCornerShape(12.dp))
                .border(1.dp, ClydeColor.Line, RoundedCornerShape(12.dp))
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("“Hey Clyde” wake word", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ClydeColor.Ink)
                Text(
                    if (wake) "Listening for “Hey Clyde”. Downloads a ~40 MB voice model once."
                    else "Say “Hey Clyde” to summon — off by default (uses the mic + battery).",
                    fontFamily = Mono, fontSize = 10.sp, color = ClydeColor.Muted,
                )
            }
            Box(
                Modifier
                    .background(if (wake) ClydeColor.Blue else Color.Transparent, RoundedCornerShape(9.dp))
                    .then(if (wake) Modifier else Modifier.border(1.dp, ClydeColor.Line2, RoundedCornerShape(9.dp)))
                    .pressable(label = if (wake) "Turn off Hey Clyde" else "Turn on Hey Clyde") {
                        wake = !wake
                        Prefs.wakeWord = wake
                        if (wake) WakeWordService.startIfEnabled(ctx) else WakeWordService.stop(ctx)
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (wake) "On" else "Off", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (wake) Color(0xFF06303C) else ClydeColor.Muted) }
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton("Ask Clyde", onClick = onAsk)
        Spacer(Modifier.height(12.dp))

        // Claude ⇄ Gemini toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClydeColor.Panel2, RoundedCornerShape(12.dp))
                .border(1.dp, ClydeColor.Line, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier.weight(1f).background(ClydeColor.Blue, RoundedCornerShape(9.dp)).padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Clyde", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF06303C)) }
            Box(
                Modifier.weight(1f).pressable(label = "Switch to Gemini") { GeminiRouter.openAssistantPicker(ctx) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Gemini", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClydeColor.Muted) }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, ClydeColor.Line2, RoundedCornerShape(11.dp))
                .pressable(label = "Stop Clyde and revoke tokens") {
                    scope.launch { BrainClient.kill() }
                    val i = Intent(ctx, AgentOrchestratorService::class.java).setAction(AgentOrchestratorService.ACTION_KILL)
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                    }
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Stop Clyde · revoke tokens", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ClydeColor.Danger) }
    }
    }
}

/** Codex (ChatGPT subscription) sign-in — runs `codex login` in the embedded runtime, opens the OAuth
 *  page in the real default browser, and shows the device code. No-op until the codex binary ships. */
@Composable
private fun CodexSignIn() {
    val ctx = LocalContext.current
    val auth = remember { CodexAuth(ctx) }
    val signedIn = remember { auth.isSignedIn() }
    var active by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth()
            .background(ClydeColor.Panel2, RoundedCornerShape(12.dp))
            .border(1.dp, ClydeColor.Line, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text("Codex sign-in", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClydeColor.Ink)
        Spacer(Modifier.height(6.dp))
        if (signedIn) {
            Text("Signed in on your ChatGPT subscription.", fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
        } else {
            Box(
                Modifier.background(ClydeColor.Blue, RoundedCornerShape(9.dp))
                    .pressable(label = "Sign in to Codex") {
                        if (!active) {
                            active = true; status = "starting…"
                            auth.start(
                                onLine = { status = it.trim().take(90) }, // the device code prints here
                                onUrl = { url -> dev.kris.clyde.util.Browser.openDefault(ctx, url) },
                                onResult = { ok -> active = false; status = if (ok) "signed in" else "sign-in didn't complete — try again" },
                            )
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(if (active) "Signing in…" else "Sign in to Codex", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF06303C)) }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(status, fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
            }
        }
    }
}

/** A pill segmented control: options are (id, label); the selected id is filled blue. */
@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClydeColor.Panel2, RoundedCornerShape(12.dp))
            .border(1.dp, ClydeColor.Line, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (id, label) ->
            val sel = selected == id
            Box(
                Modifier
                    .weight(1f)
                    .background(if (sel) ClydeColor.Blue else Color.Transparent, RoundedCornerShape(9.dp))
                    .then(if (sel) Modifier else Modifier.pressable(label = "Use $label") { onSelect(id) })
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (sel) Color(0xFF06303C) else ClydeColor.Muted) }
        }
    }
}

@Composable
private fun CapabilityRow(name: String, status: String, live: Boolean, on: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.dp, Color.Transparent)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val animate = live && !reduceMotion()
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (animate) {
                val t = rememberInfiniteTransition(label = "cap")
                val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "capP")
                Box(
                    Modifier
                        .size(16.dp)
                        .graphicsLayer { alpha = 0.4f * (1f - p); scaleX = 0.5f + p; scaleY = 0.5f + p }
                        .background(ClydeColor.Blue, CircleShape),
                )
            }
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (live) ClydeColor.Blue else if (on) ClydeColor.BlueDeep else ClydeColor.Line2,
                        CircleShape,
                    ),
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            name,
            fontFamily = Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.5f.sp,
            color = if (on) ClydeColor.Ink else ClydeColor.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(status, fontFamily = Body, fontSize = 12.5f.sp, color = if (live) ClydeColor.BlueText else ClydeColor.Muted)
    }
}
