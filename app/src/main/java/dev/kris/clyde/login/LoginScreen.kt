package dev.kris.clyde.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import dev.kris.clyde.bridge.TermuxRunCommand
import dev.kris.clyde.ui.Body
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.ClydeLogo
import dev.kris.clyde.ui.Display
import dev.kris.clyde.ui.HeroMark
import dev.kris.clyde.ui.Mono
import dev.kris.clyde.ui.PrimaryButton

/** Panel 01 — Sign in with your Claude plan. Launches `claude login` in Termux. */
@Composable
fun LoginScreen(onStartSignIn: () -> Unit) {
    val ctx = LocalContext.current
    var termuxError by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClydeColor.Paper)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        HeroMark()
        Spacer(Modifier.height(18.dp))
        Text(
            "Sign in with your\nClaude plan",
            fontFamily = Display,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.025).em,
            color = ClydeColor.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Clyde runs on your Claude subscription — the same Pro or Max plan you already pay for. No API key, no per-message billing.",
            fontFamily = Body,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = ClydeColor.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ClydeLogo(tint = ClydeColor.Terracotta, size = 13.dp, contentDescription = null) // ornamental beside the label
            Text("powered by Claude · never an API key", fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Open Termux & start sign-in", onClick = {
            if (TermuxRunCommand.startClaudeLogin(ctx)) {
                termuxError = false
                onStartSignIn()
            } else {
                termuxError = true
            }
        })
        Spacer(Modifier.height(10.dp))
        if (termuxError) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClydeColor.TerracottaTint, RoundedCornerShape(12.dp))
                    .border(1.dp, ClydeColor.Terracotta, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text("Termux didn't accept the command.", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5f.sp, color = ClydeColor.Ink)
                Text(
                    "Install Termux + Termux:API from F-Droid and enable allow-external-apps, then try again.",
                    fontFamily = Body, fontSize = 12.5f.sp, lineHeight = 18.sp, color = ClydeColor.Muted,
                )
            }
        } else {
            Text(
                "Termux opens and runs claude login. Finish in your browser — Clyde never sees your password or token.",
                fontFamily = Body,
                fontSize = 12.5f.sp,
                lineHeight = 18.sp,
                color = ClydeColor.Muted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Signing in on the phone is awkward? You can copy ~/.claude from a desktop where you've already run claude login.",
            fontFamily = Body,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = ClydeColor.Muted,
            textAlign = TextAlign.Center,
        )
    }
}
