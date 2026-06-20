package dev.kris.clyde.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kris.clyde.caps.CapabilityProbe
import dev.kris.clyde.ui.Body
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.Display
import dev.kris.clyde.ui.Eyebrow
import dev.kris.clyde.ui.Mono
import dev.kris.clyde.ui.PrimaryButton
import dev.kris.clyde.ui.SecondaryLink

private val RUNTIME_PERMS = arrayOf(
    android.Manifest.permission.RECORD_AUDIO,
    android.Manifest.permission.POST_NOTIFICATIONS,
    android.Manifest.permission.CALL_PHONE,
    android.Manifest.permission.SEND_SMS,
    android.Manifest.permission.ACCESS_FINE_LOCATION,
)

private enum class SetupStep { Branch, Chooser, Pairing }

/** Panels 03–05 — auto-detecting setup. Forks on root/custom-ROM vs stock. */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val caps = remember { CapabilityProbe.probe(ctx) }
    val auto = caps.root || caps.customRom
    var step by remember { mutableStateOf(if (auto) SetupStep.Branch else SetupStep.Chooser) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result handled by re-probe on next screen */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClydeColor.Paper)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        when (step) {
            SetupStep.Branch -> AutoConfigView(
                romName = caps.romName ?: if (caps.root) "rooted device" else "this phone",
                rooted = caps.root,
                onConfigure = {
                    permLauncher.launch(RUNTIME_PERMS)
                    requestOverlay(ctx)
                    openAccessibility(ctx)
                    onDone()
                },
                onManual = { step = SetupStep.Chooser },
            )
            SetupStep.Chooser -> ChooserView(
                onBasic = {
                    permLauncher.launch(RUNTIME_PERMS)
                    requestOverlay(ctx)
                    openAccessibility(ctx)
                    onDone()
                },
                onFull = { step = SetupStep.Pairing },
            )
            SetupStep.Pairing -> PairingView(
                onOpenWirelessDebugging = { openDeveloperSettings(ctx) },
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun ColumnScope.AutoConfigView(romName: String, rooted: Boolean, onConfigure: () -> Unit, onManual: () -> Unit) {
    Eyebrow("setup · 1 of 1 on this phone")
    Spacer(Modifier.height(6.dp))
    H1("Clyde can set itself up")
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClydeColor.BlueTint, RoundedCornerShape(14.dp))
            .border(1.dp, ClydeColor.Blue.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).background(Color.White, RoundedCornerShape(9.dp))
                .border(1.dp, ClydeColor.Blue.copy(alpha = 0.4f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(if (rooted) "su" else "OS", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ClydeColor.BlueDeep) }
        Spacer(Modifier.size(11.dp))
        Column {
            Text(if (rooted) "Root access found · $romName" else "Custom ROM · $romName", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5f.sp, color = ClydeColor.Ink)
            Text(if (rooted) "probed: su -c id → uid=0" else "probed: getprop ro.* fingerprints", fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Because this phone is unlocked, Clyde grants the permissions itself — no taps, no pairing codes.",
        fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp, color = ClydeColor.Muted,
    )
    Spacer(Modifier.weight(1f))
    PrimaryButton("Configure automatically", onClick = onConfigure)
    SecondaryLink("Set it up manually instead", onClick = onManual)
}

@Composable
private fun ColumnScope.ChooserView(onBasic: () -> Unit, onFull: () -> Unit) {
    Eyebrow("setup · choose how much it can do")
    Spacer(Modifier.height(6.dp))
    H1("How much should Clyde do?")
    Spacer(Modifier.height(8.dp))
    Text(
        "Stock phone, no root — so you pick. Start basic and add more later; you can change this any time.",
        fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp, color = ClydeColor.Muted,
    )
    Spacer(Modifier.height(14.dp))
    ChoiceCard(
        title = "Basic",
        tag = "always works",
        desc = "Clyde reads your screen and taps for you — texts, reminders, opening apps.",
        why = "One thing to do: flip the Accessibility toggle.",
        featured = false,
        onClick = onBasic,
    )
    Spacer(Modifier.height(12.dp))
    ChoiceCard(
        title = "Full control",
        tag = "recommended",
        desc = "Adds real typing, installing apps, and changing settings.",
        why = "Clyde does the ADB setup; you read it one 6-digit code.",
        featured = true,
        onClick = onFull,
    )
}

@Composable
private fun ColumnScope.PairingView(onOpenWirelessDebugging: () -> Unit, onDone: () -> Unit) {
    Eyebrow("full control · 1 human step, then automatic")
    Spacer(Modifier.height(6.dp))
    H1("Read Clyde the pairing code")
    Spacer(Modifier.height(8.dp))
    Text(
        "Android shows this code only in its own secure dialog, so it's the single thing Clyde can't do for you. Open wireless debugging, tap \"Pair device with code\", and read it in.",
        fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp, color = ClydeColor.Muted,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(6) { i ->
            Box(
                Modifier.weight(1f).height(48.dp)
                    .background(ClydeColor.Paper, RoundedCornerShape(9.dp))
                    .border(if (i == 0) 2.dp else 1.dp, if (i == 0) ClydeColor.Blue else ClydeColor.Line2, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("_", fontFamily = Mono, fontSize = 20.sp, color = ClydeColor.Faint) }
        }
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton("Open wireless debugging", onClick = onOpenWirelessDebugging)
    SecondaryLink("Paired — finish setup", onClick = onDone)
}

@Composable
private fun ChoiceCard(title: String, tag: String, desc: String, why: String, featured: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (featured) ClydeColor.Paper else ClydeColor.Panel2, RoundedCornerShape(16.dp))
            .border(if (featured) 2.dp else 1.dp, if (featured) ClydeColor.Blue else ClydeColor.Line, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ClydeColor.Ink)
            Spacer(Modifier.weight(1f))
            Text(tag.uppercase(), fontFamily = Mono, fontSize = 10.sp, color = if (featured) ClydeColor.BlueDeep else ClydeColor.Muted)
        }
        Spacer(Modifier.height(6.dp))
        Text(desc, fontFamily = Body, fontSize = 13.sp, lineHeight = 19.sp, color = ClydeColor.Muted)
        Spacer(Modifier.height(8.dp))
        Text(why, fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
    }
}

@Composable
private fun H1(text: String) {
    Text(text, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 25.sp, letterSpacing = (-0.025).em, color = ClydeColor.Ink)
}

private fun requestOverlay(ctx: Context) {
    if (!Settings.canDrawOverlays(ctx)) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun openAccessibility(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openDeveloperSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
