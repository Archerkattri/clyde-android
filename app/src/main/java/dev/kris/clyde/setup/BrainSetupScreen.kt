package dev.kris.clyde.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kris.clyde.bridge.BrainClient
import dev.kris.clyde.bridge.TermuxRunCommand
import dev.kris.clyde.overlay.ClawdSceneView
import dev.kris.clyde.runtime.ClaudeAuth
import dev.kris.clyde.runtime.EmbeddedRuntime
import dev.kris.clyde.service.AgentOrchestratorService
import dev.kris.clyde.ui.Body
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.Display
import dev.kris.clyde.ui.Eyebrow
import dev.kris.clyde.ui.FitToScreen
import dev.kris.clyde.ui.Mono
import dev.kris.clyde.ui.PrimaryButton
import dev.kris.clyde.ui.SecondaryLink
import dev.kris.clyde.ui.pressable
import dev.kris.clyde.util.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One-time companion setup: get Clyde's brain running in Termux without leaving the app for long.
 * Everything here is real — Termux detection, the loopback bootstrap the app actually serves, and a
 * live /healthz poll. The only hands-on steps are inherent: install Termux, paste one command,
 * finish `claude login` in the browser.
 */
@Composable
fun BrainSetupScreen(onConnected: () -> Unit, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    // Embedded build (runtime bundled in the APK): drive the in-app runtime + sign-in, no Termux.
    // Otherwise fall back to the external-Termux companion (curl bootstrap).
    if (EmbeddedRuntime.isBundled(ctx)) EmbeddedBrainSetup(onConnected, onSkip)
    else TermuxCompanionSetup(onConnected, onSkip)
}

@Composable
private fun TermuxCompanionSetup(onConnected: () -> Unit, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    val key = Prefs.clydeKey
    val command = "curl -fsS -H \"X-Clyde-Key: $key\" http://127.0.0.1:8766/bootstrap.sh | bash -s -- $key"

    var termuxInstalled by remember { mutableStateOf(TermuxRunCommand.isTermuxInstalled(ctx)) }
    var online by remember { mutableStateOf<Boolean?>(null) }

    // Bring up the orchestrator's loopback server so Termux can fetch /bootstrap.sh + /brain.tgz,
    // then keep re-checking Termux + the brain so the screen reflects reality (no faked status).
    LaunchedEffect(Unit) {
        val i = Intent(ctx, AgentOrchestratorService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }
        while (true) {
            termuxInstalled = TermuxRunCommand.isTermuxInstalled(ctx)
            online = BrainClient.healthz()
            delay(2500)
        }
    }

    FitToScreen {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ClawdSceneView(sceneKey = if (online == true) "success" else "working", size = 84.dp)
        }
        Spacer(Modifier.height(10.dp))
        Eyebrow("setup · connect the brain")
        Spacer(Modifier.height(6.dp))
        Text(
            "Set up Clyde's brain",
            fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 25.sp,
            letterSpacing = (-0.025).em, color = ClydeColor.Ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Clyde's thinking runs on your Claude subscription through a small helper in Termux. This is a one-time setup — afterward it starts on its own.",
            fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp, color = ClydeColor.Muted,
        )
        Spacer(Modifier.height(18.dp))

        // Step 1 — Termux
        StepCard(index = "1", title = "Install Termux", done = termuxInstalled) {
            if (termuxInstalled) {
                Text("Termux is installed.", fontFamily = Body, fontSize = 13.sp, color = ClydeColor.Muted)
            } else {
                Text(
                    "Termux isn't on Google Play — get it from F-Droid (also install Termux:API).",
                    fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, color = ClydeColor.Muted,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.border(1.dp, ClydeColor.Blue, RoundedCornerShape(10.dp))
                        .pressable(label = "Install Termux") { TermuxRunCommand.openTermuxInstall(ctx) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text("Install Termux", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClydeColor.BlueText) }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Step 2 — paste the bootstrap command
        StepCard(index = "2", title = "Paste one command in Termux", done = online == true) {
            Text(
                "Open Termux, paste this, and press enter. It installs the brain, writes your key, and tells you the last two steps.",
                fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, color = ClydeColor.Muted,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth()
                    .background(Color0E, RoundedCornerShape(10.dp))
                    .border(1.dp, Color1E, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(command, fontFamily = Mono, fontSize = 11.5f.sp, lineHeight = 17.sp, color = TermFg)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.background(ClydeColor.Blue, RoundedCornerShape(10.dp))
                        .pressable(label = "Copy command") {
                            val clip = ctx.getSystemService(ClipboardManager::class.java)
                            clip?.setPrimaryClip(ClipData.newPlainText("clyde bootstrap", command))
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) { Text("Copy command", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OnBlue) }
                Box(
                    Modifier.border(1.dp, ClydeColor.Line2, RoundedCornerShape(10.dp))
                        .pressable(label = "Open Termux") { TermuxRunCommand.openTermuxApp(ctx) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text("Open Termux", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClydeColor.Muted) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Then, in Termux: run “claude” (pick Pro/Max, finish login in the browser), then “npm run start”.",
                fontFamily = Body, fontSize = 12.5f.sp, lineHeight = 18.sp, color = ClydeColor.Muted,
            )
        }
        Spacer(Modifier.height(16.dp))

        // Live brain status — real /healthz poll
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(if (online == true) ClydeColor.Verda else ClydeColor.Line2, CircleShape))
            Spacer(Modifier.size(9.dp))
            Text(
                when (online) { true -> "brain online · 127.0.0.1:8765"; else -> "waiting for the brain…" },
                fontFamily = Mono, fontSize = 12.sp, color = if (online == true) ClydeColor.VerdaDeep else ClydeColor.Muted,
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Continue", onClick = onConnected, enabled = online == true)
        SecondaryLink("Set this up later", onClick = onSkip)
        Spacer(Modifier.height(8.dp))
    }
    }
}

@Composable
private fun EmbeddedBrainSetup(onConnected: () -> Unit, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { ClaudeAuth(ctx) }
    var runtimeReady by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf<Boolean?>(null) }
    var signedIn by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") } // pasted setup token (fallback)
    var loginStatus by remember { mutableStateOf("") }
    var runtimeError by remember { mutableStateOf(false) }
    var brainError by remember { mutableStateOf(false) }
    var lowStorage by remember { mutableStateOf(false) }
    var progressState by remember { mutableStateOf<EmbeddedRuntime.Progress?>(null) }
    var attempt by remember { mutableStateOf(0) }

    // Starting the orchestrator extracts the runtime + launches the brain in-process; then poll real
    // state. Keyed on `attempt` so "Try again" re-runs it. Bounded: fail loud instead of spinning forever.
    LaunchedEffect(attempt) {
        runtimeError = false; brainError = false
        lowStorage = EmbeddedRuntime.lowStorage(ctx) // genuine out-of-space → fail fast with a clear message
        val i = Intent(ctx, AgentOrchestratorService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }
        var ticks = 0
        var readyAtTick = -1
        while (true) {
            runtimeReady = EmbeddedRuntime.isInstalled(ctx)
            progressState = EmbeddedRuntime.progress
            online = BrainClient.healthz()
            if (auth.isSignedIn()) signedIn = true
            // brain is authoritative for the loopback sign-in: detect completion + surface any error
            BrainClient.loginStatus()?.let { li ->
                if (li.signedIn) signedIn = true
                if (li.error.isNotBlank()) loginStatus = "sign-in error: ${li.error.take(90)}"
            }
            if (runtimeReady && readyAtTick < 0) readyAtTick = ticks
            // First-run unpack (~200 MB, thousands of small files) is legitimately slow on a phone — wait
            // up to ~5 min before declaring failure. If we're genuinely out of space it won't unpack at
            // all, so fail fast there. The brain gets ~90s AFTER the unpack actually finishes.
            if (lowStorage && !runtimeReady && ticks >= 3) { runtimeError = true; return@LaunchedEffect }
            if (!runtimeReady && ticks >= 150) { runtimeError = true; return@LaunchedEffect }
            if (runtimeReady && online != true && readyAtTick >= 0 && ticks - readyAtTick >= 45) { brainError = true; return@LaunchedEffect }
            if (runtimeReady && online == true && signedIn) return@LaunchedEffect // fully ready — stop polling
            ticks++
            delay(2000)
        }
    }

    FitToScreen {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        // Clawd reflects the live setup state: working through the runtime, thinking during sign-in,
        // a success hop when all three steps land, an error wobble if unpack/brain fails.
        val scene = when {
            runtimeError || brainError -> "error"
            runtimeReady && online == true && signedIn -> "success"
            else -> "working"
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ClawdSceneView(sceneKey = scene, size = 84.dp)
        }
        Spacer(Modifier.height(10.dp))
        Eyebrow("setup · clyde's brain")
        Spacer(Modifier.height(6.dp))
        Text("Setting up Clyde's brain", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 25.sp, letterSpacing = (-0.025).em, color = ClydeColor.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Clyde's brain runs inside the app — nothing else to install. Sign in once with your Claude plan and you're set.",
            fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp, color = ClydeColor.Muted,
        )
        Spacer(Modifier.height(18.dp))

        StepCard("1", "Runtime ready", done = runtimeReady) {
            if (runtimeError) {
                Text(
                    if (lowStorage) "Low on storage — the runtime needs ~280 MB free to unpack (you have ${EmbeddedRuntime.freeMb(ctx)} MB). Free some space, then tap Try again."
                    else "Couldn't finish unpacking the runtime. Tap Try again and leave Clyde open; if it keeps failing, reinstall Clyde.",
                    fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, color = ClydeColor.TerracottaDeep,
                )
                EmbeddedRuntime.lastError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("details: $it", fontFamily = Mono, fontSize = 10.sp, lineHeight = 14.sp, color = ClydeColor.Muted)
                }
            } else if (runtimeReady) {
                Text("Linux runtime unpacked.", fontFamily = Body, fontSize = 13.sp, color = ClydeColor.Muted)
            } else {
                // live unpack status: a bar + phase + MB + ETA so the first run isn't an opaque wait
                val p = progressState
                val pct = if (p != null && p.totalBytes > 0L) (p.bytes.toFloat() / p.totalBytes).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth(), color = ClydeColor.Blue, trackColor = ClydeColor.Line)
                Spacer(Modifier.height(6.dp))
                val mb = (p?.bytes ?: 0L) / (1024 * 1024)
                val totalMb = (p?.totalBytes ?: (214L * 1024 * 1024)) / (1024 * 1024)
                val eta = p?.etaSeconds ?: -1
                val etaText = if (eta in 0..6000) " · ~${if (eta >= 60) "${eta / 60}m ${eta % 60}s" else "${eta}s"} left" else ""
                Text("${p?.phase ?: "Unpacking"}… $mb / $totalMb MB$etaText", fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
                Text("first run — leave Clyde open", fontFamily = Body, fontSize = 11.sp, color = ClydeColor.Muted)
            }
        }
        Spacer(Modifier.height(12.dp))

        StepCard("2", "Sign in to Claude", done = signedIn) {
            if (signedIn) {
                Text("Signed in on your subscription.", fontFamily = Body, fontSize = 13.sp, color = ClydeColor.Muted)
            } else {
                Text(
                    "Tap below — Clyde opens Claude in your browser, you authorize, and you're in. No password or token to copy; Clyde never sees them.",
                    fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, color = ClydeColor.Muted,
                )
                Spacer(Modifier.height(10.dp))
                val ready = runtimeReady && online == true
                Box(
                    Modifier.background(if (ready) ClydeColor.Blue else ClydeColor.Line2, RoundedCornerShape(10.dp))
                        .pressable(label = "Sign in to Claude") {
                            if (ready) {
                                loginStatus = "opening browser…"
                                scope.launch {
                                    val url = BrainClient.startLogin()
                                    if (url != null) {
                                        dev.kris.clyde.util.Browser.openDefault(ctx, url)
                                        loginStatus = "authorize in your browser, then come back here"
                                    } else {
                                        loginStatus = "couldn't start sign-in — wait for the brain (step 3), then retry"
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Text(if (ready) "Sign in to Claude" else "Waiting for the brain…", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (ready) OnBlue else ClydeColor.Muted) }
                if (loginStatus.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(loginStatus, fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
                }

                // Fallback: paste a token (for when the in-browser sign-in can't reach the device callback).
                Spacer(Modifier.height(14.dp))
                Text(
                    "Trouble? Run  claude setup-token  on a computer with Claude Code and paste the token:",
                    fontFamily = Body, fontSize = 11.5f.sp, lineHeight = 16.sp, color = ClydeColor.Muted,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = code, onValueChange = { code = it }, singleLine = true,
                    label = { Text("paste token (optional)", fontFamily = Body, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.border(1.dp, ClydeColor.Line2, RoundedCornerShape(9.dp))
                        .pressable(label = "Save token") {
                            if (code.isNotBlank()) {
                                Prefs.oauthToken = code.trim(); code = ""; signedIn = true
                                val i = Intent(ctx, AgentOrchestratorService::class.java).setAction(AgentOrchestratorService.ACTION_RESTART_BRAIN)
                                runCatching { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i) }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("Save token", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClydeColor.BlueText) }
            }
        }
        Spacer(Modifier.height(12.dp))

        StepCard("3", "Brain online", done = online == true) {
            if (brainError) {
                Text("The brain didn't start. Tap Try again; if it keeps failing, reopen Clyde.", fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, color = ClydeColor.TerracottaDeep)
            } else {
                Text(if (online == true) "Running on 127.0.0.1:8765." else "Starting the brain…", fontFamily = Body, fontSize = 13.sp, color = ClydeColor.Muted)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (runtimeError || brainError) {
            Box(
                Modifier.fillMaxWidth().border(1.dp, ClydeColor.Line2, RoundedCornerShape(11.dp))
                    .pressable(label = "Try again") { runtimeReady = false; online = null; attempt++ }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Try again", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ClydeColor.BlueText) }
            Spacer(Modifier.height(10.dp))
        }
        PrimaryButton("Continue", onClick = onConnected, enabled = online == true && signedIn)
        SecondaryLink("Finish setup later", onClick = onSkip)
        Spacer(Modifier.height(8.dp))
    }
    }
}

@Composable
private fun StepCard(index: String, title: String, done: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(ClydeColor.Panel2, RoundedCornerShape(14.dp))
            .border(1.dp, if (done) ClydeColor.Verda else ClydeColor.Line, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).background(if (done) ClydeColor.Verda else ClydeColor.BlueTint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (done) "✓" else index, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (done) androidx.compose.ui.graphics.Color.White else ClydeColor.BlueText)
            }
            Spacer(Modifier.size(10.dp))
            Text(title, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ClydeColor.Ink)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

private val Color0E = androidx.compose.ui.graphics.Color(0xFF0E1311)
private val Color1E = androidx.compose.ui.graphics.Color(0xFF1E322E)
private val TermFg = androidx.compose.ui.graphics.Color(0xFF9FB8B0)
private val OnBlue = androidx.compose.ui.graphics.Color(0xFF06303C)
