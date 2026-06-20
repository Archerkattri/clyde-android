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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kris.clyde.bridge.BrainClient
import dev.kris.clyde.caps.CapabilityProbe
import dev.kris.clyde.router.GeminiRouter
import dev.kris.clyde.ui.Body
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.ClydeLogo
import dev.kris.clyde.ui.Display
import dev.kris.clyde.ui.Eyebrow
import dev.kris.clyde.ui.Mono
import dev.kris.clyde.ui.PrimaryButton
import kotlinx.coroutines.launch

/** Panel 08 — Home / control center. */
@Composable
fun HomeScreen(onAsk: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val caps = remember { CapabilityProbe.probe(ctx) }
    var online by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) { online = BrainClient.healthz() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClydeColor.Paper)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClydeLogo(size = 28.dp)
            Spacer(Modifier.size(10.dp))
            Row {
                Text("Clyde", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 27.sp, letterSpacing = (-0.03).em, color = ClydeColor.Ink)
                Text(".", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 27.sp, color = ClydeColor.Blue)
            }
            Spacer(Modifier.weight(1f))
            val statusText = when (online) { true -> "online"; false -> "brain offline"; null -> "…" }
            val statusColor = if (online == true) ClydeColor.BlueDeep else ClydeColor.Muted
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

        Spacer(Modifier.height(20.dp))
        Eyebrow("what clyde can do")
        Spacer(Modifier.height(6.dp))
        CapabilityRow("Apps, alarms, texts, navigation", "always on", live = true, on = true)
        CapabilityRow("See & tap the screen", if (caps.accessibility) "on" else "tap to enable", live = caps.accessibility, on = caps.accessibility)
        CapabilityRow("Type, install, change settings", if (caps.shizuku) "on" else "off", live = caps.shizuku, on = caps.shizuku)
        CapabilityRow("Unrestricted (root)", if (caps.root) "on" else "off", live = caps.root, on = caps.root)

        Spacer(Modifier.weight(1f))
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
                Modifier.weight(1f).clickable { GeminiRouter.openAssistantPicker(ctx) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Gemini", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClydeColor.Muted) }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, ClydeColor.Line2, RoundedCornerShape(11.dp))
                .clickable { scope.launch { BrainClient.kill() } }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Stop Clyde · revoke tokens", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ClydeColor.Danger) }
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
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (live) ClydeColor.Blue else if (on) ClydeColor.BlueDeep else ClydeColor.Line2,
                    CircleShape,
                ),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            name,
            fontFamily = Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.5f.sp,
            color = if (on) ClydeColor.Ink else ClydeColor.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(status, fontFamily = Body, fontSize = 12.5f.sp, color = if (live) ClydeColor.BlueDeep else ClydeColor.Muted)
    }
}
