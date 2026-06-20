package dev.kris.clyde.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.kris.clyde.service.AgentOrchestratorService
import dev.kris.clyde.ui.Body
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.Display
import dev.kris.clyde.ui.Eyebrow
import dev.kris.clyde.ui.Mono
import dev.kris.clyde.ui.PrimaryButton
import dev.kris.clyde.ui.SecondaryLink
import dev.kris.clyde.ui.pressable
import dev.kris.clyde.util.Prefs
import kotlinx.coroutines.delay

/**
 * One-time companion setup: get Clyde's brain running in Termux without leaving the app for long.
 * Everything here is real — Termux detection, the loopback bootstrap the app actually serves, and a
 * live /healthz poll. The only hands-on steps are inherent: install Termux, paste one command,
 * finish `claude login` in the browser.
 */
@Composable
fun BrainSetupScreen(onConnected: () -> Unit, onSkip: () -> Unit) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClydeColor.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
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
                        .pressable(label = "Open Termux") { TermuxRunCommand.runInTermux(ctx, "echo paste the Clyde command and press enter", background = false) }
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
        SecondaryLink("Skip for now — set this up later", onClick = onSkip)
        Spacer(Modifier.height(8.dp))
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
