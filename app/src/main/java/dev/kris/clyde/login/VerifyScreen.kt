package dev.kris.clyde.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kris.clyde.bridge.AuthStatus
import dev.kris.clyde.bridge.BrainClient
import dev.kris.clyde.ui.Body
import dev.kris.clyde.ui.CheckRow
import dev.kris.clyde.ui.CheckState
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.Display
import dev.kris.clyde.ui.Eyebrow
import dev.kris.clyde.ui.Mono
import dev.kris.clyde.ui.PrimaryButton
import kotlinx.coroutines.delay

/** Panel 02 — Confirming your plan. Polls the brain; verifies, never handles tokens. */
@Composable
fun VerifyScreen(onContinue: () -> Unit) {
    var reachable by remember { mutableStateOf<Boolean?>(null) }
    var auth by remember { mutableStateOf<AuthStatus?>(null) }

    LaunchedEffect(Unit) {
        repeat(40) {
            reachable = BrainClient.healthz()
            if (reachable == true) auth = BrainClient.authStatus()
            if (reachable == true && auth != null) return@LaunchedEffect
            delay(1800)
        }
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

        // Termux preview strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E1311), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1E322E), RoundedCornerShape(12.dp))
                .padding(13.dp),
        ) {
            TermuxLine("~ $ ", "claude login", Color(0xFF56E6C0))
            Text("→ opening browser to claude.ai…", fontFamily = Mono, fontSize = 11.sp, color = Color(0xFF5C7068))
            TermuxLine("✓ ", "Logged in", Color(0xFF56E6C0))
            TermuxLine("~ $ ", "claude /status", Color(0xFF56E6C0))
        }
        Spacer(Modifier.height(16.dp))

        CheckRow(brainState, "Brain reachable", if (brainState == CheckState.Ok) "127.0.0.1:8765" else "checking…")
        CheckRow(subState, "Signed in on a subscription", auth?.plan ?: if (subState == CheckState.Ok) "subscription" else "checking…")
        CheckRow(keyState, "No API key set", if (keyState == CheckState.Fail) "remove it" else if (keyState == CheckState.Ok) "clean" else "checking…")

        Spacer(Modifier.weight(1f))
        Text(
            "Clyde refuses to run if an ANTHROPIC_API_KEY is present — subscription only, by design.",
            fontFamily = Body,
            fontSize = 12.sp,
            color = ClydeColor.Faint,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        PrimaryButton("Continue", onClick = onContinue, enabled = verified)
    }
}

@Composable
private fun TermuxLine(prompt: String, text: String, promptColor: Color) {
    Text(
        buildString { append(prompt); append(text) },
        fontFamily = Mono,
        fontSize = 11.sp,
        color = Color(0xFF9FB8B0),
    )
}
