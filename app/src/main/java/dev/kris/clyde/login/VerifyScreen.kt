package dev.kris.clyde.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import dev.kris.clyde.bridge.AuthStatus
import dev.kris.clyde.bridge.BrainClient
import dev.kris.clyde.overlay.ClawdSceneView
import dev.kris.clyde.runtime.EmbeddedRuntime
import dev.kris.clyde.ui.Body
import dev.kris.clyde.ui.CheckRow
import dev.kris.clyde.ui.CheckState
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.Display
import dev.kris.clyde.ui.Eyebrow
import dev.kris.clyde.ui.Mono
import dev.kris.clyde.ui.PrimaryButton
import dev.kris.clyde.ui.pressable
import kotlinx.coroutines.delay

/** Panel 02 — Confirming your plan. Polls the brain; verifies, never handles tokens. */
@Composable
fun VerifyScreen(onContinue: () -> Unit) {
    val embedded = EmbeddedRuntime.isBundled(LocalContext.current) // brain runs in-app, not Termux
    var reachable by remember { mutableStateOf<Boolean?>(null) }
    var auth by remember { mutableStateOf<AuthStatus?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var timedOut by remember { mutableStateOf(false) }

    // Keyed on `attempt` so "Check again" re-runs it; exits only when actually verified.
    LaunchedEffect(attempt) {
        timedOut = false
        reachable = null
        repeat(40) {
            reachable = BrainClient.healthz()
            if (reachable == true) auth = BrainClient.authStatus()
            if (reachable == true && auth?.subscription == true && auth?.apiKeyPresent == false) return@LaunchedEffect
            delay(1800)
        }
        timedOut = true
    }

    val brainState = when (reachable) {
        true -> CheckState.Ok
        else -> CheckState.Pending
    }
    val subState = when {
        auth?.subscription == true -> CheckState.Ok
        auth?.subscription == false -> CheckState.Fail
        else -> CheckState.Pending
    }
    val keyState = when {
        auth == null -> CheckState.Pending
        auth?.apiKeyPresent == false -> CheckState.Ok
        else -> CheckState.Fail
    }
    val verified = brainState == CheckState.Ok && subState == CheckState.Ok && keyState == CheckState.Ok

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClydeColor.Paper)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        val scene = when {
            verified -> "success"
            subState == CheckState.Fail || keyState == CheckState.Fail -> "error"
            timedOut -> "warning"
            reachable == true -> "thinking"
            else -> "searching"
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ClawdSceneView(sceneKey = scene, size = 84.dp)
        }
        Spacer(Modifier.height(10.dp))
        Eyebrow("sign-in · verifying")
        Spacer(Modifier.height(6.dp))
        Text(
            "Confirming your plan",
            fontFamily = Display,
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp,
            letterSpacing = (-0.025).em,
            color = ClydeColor.Ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Clyde asks the brain on your phone whether you're really signed in — it reads the result, it never touches your credentials.",
            fontFamily = Body,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = ClydeColor.Muted,
        )
        Spacer(Modifier.height(16.dp))

        // Terminal-styled LIVE status — reflects the real brain/auth handshake, never a canned login.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E1311), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1E322E), RoundedCornerShape(12.dp))
                .padding(13.dp),
        ) {
            TermuxLine("~ $ ", "claude login", Color(0xFF56E6C0))
            val (glyph, msg, col) = when {
                verified -> Triple("✓ ", "signed in · ${auth?.plan ?: "subscription"}", Color(0xFF56E6C0))
                reachable == true -> Triple("… ", "waiting for claude login to finish", Color(0xFF5C7068))
                else -> Triple("… ", "connecting to the brain on 127.0.0.1:8765", Color(0xFF5C7068))
            }
            TermuxLine(glyph, msg, col)
        }
        Spacer(Modifier.height(16.dp))

        CheckRow(brainState, "Brain reachable", when { brainState == CheckState.Ok -> "127.0.0.1:8765"; timedOut -> "offline"; else -> "checking…" })
        CheckRow(subState, "Signed in on a subscription", when { subState == CheckState.Ok -> auth?.plan ?: "subscription"; subState == CheckState.Fail -> if (embedded) "sign in again" else "sign in in Termux"; timedOut -> "not yet"; else -> "checking…" })
        CheckRow(keyState, "No API key set", when { keyState == CheckState.Ok -> "clean"; keyState == CheckState.Fail -> "remove API key"; else -> "checking…" })

        Spacer(Modifier.weight(1f))
        if (timedOut && !verified) {
            Text(
                if (embedded) "Couldn't confirm yet. Make sure sign-in finished and the brain is running, then check again."
                else "Couldn't confirm yet. Finish claude login in Termux and make sure the brain is running, then check again.",
                fontFamily = Body, fontSize = 12.5f.sp, color = ClydeColor.Muted, modifier = Modifier.padding(bottom = 10.dp),
            )
            Box(
                Modifier.fillMaxWidth().border(1.dp, ClydeColor.Line2, RoundedCornerShape(11.dp)).pressable(label = "Check again") { attempt++ }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Check again", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ClydeColor.BlueText) }
            Spacer(Modifier.height(10.dp))
        } else {
            Text(
                "Clyde refuses to run if an ANTHROPIC_API_KEY is present — subscription only, by design.",
                fontFamily = Body, fontSize = 12.sp, color = ClydeColor.Muted, modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        PrimaryButton("Continue", onClick = onContinue, enabled = verified)
    }
}

@Composable
private fun TermuxLine(prompt: String, text: String, promptColor: Color) {
    Row {
        Text(prompt, fontFamily = Mono, fontSize = 11.sp, color = promptColor)
        Text(text, fontFamily = Mono, fontSize = 11.sp, color = Color(0xFF9FB8B0))
    }
}
