package dev.kris.clyde.setup

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.kris.clyde.caps.CapabilityProbe
import dev.kris.clyde.caps.Caps
import dev.kris.clyde.overlay.ClawdSceneView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.kris.clyde.ui.Body
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.Display
import dev.kris.clyde.ui.Eyebrow
import dev.kris.clyde.ui.Mono
import dev.kris.clyde.ui.PrimaryButton
import dev.kris.clyde.ui.SecondaryLink
import dev.kris.clyde.ui.pressable

// Keep this in sync with CapabilityProbe.perms so the Grants "App permissions" row can read granted.
private val RUNTIME_PERMS = arrayOf(
    android.Manifest.permission.RECORD_AUDIO,
    android.Manifest.permission.POST_NOTIFICATIONS,
    android.Manifest.permission.CALL_PHONE,
    android.Manifest.permission.SEND_SMS,
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.WRITE_CALENDAR,
)

private enum class SetupStep { Branch, Chooser, Pairing, Grants }

/** Panels 03–05 — auto-detecting setup. Forks on root/custom-ROM vs stock. */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    // Probe off the main thread; the branch decision needs the result, so show a beat of detection.
    val caps by produceState<Caps?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { CapabilityProbe.probe(ctx) }
    }
    val c = caps
    if (c == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(ClydeColor.Paper).padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClawdSceneView(sceneKey = "searching", size = 88.dp)
            Spacer(Modifier.height(12.dp))
            Eyebrow("setup")
            Spacer(Modifier.height(8.dp))
            Text("Detecting your phone…", fontFamily = Body, fontSize = 14.sp, color = ClydeColor.Muted)
        }
        return
    }
    SetupContent(c, onDone)
}

@Composable
private fun SetupContent(caps: Caps, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val auto = caps.root || caps.customRom
    var step by remember { mutableStateOf(if (auto) SetupStep.Branch else SetupStep.Chooser) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClydeColor.Paper)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ClawdSceneView(
                sceneKey = when (step) { SetupStep.Branch, SetupStep.Chooser -> "thinking"; else -> "working" },
                size = 78.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        // Every completion path routes through Grants so permissions/overlay/accessibility are
        // granted one-at-a-time (not all fired at once while the screen navigates away).
        when (step) {
            SetupStep.Branch -> AutoConfigView(
                romName = caps.romName ?: if (caps.root) "rooted device" else "this phone",
                rooted = caps.root,
                onConfigure = { step = SetupStep.Grants },
                onManual = { step = SetupStep.Chooser },
            )
            SetupStep.Chooser -> ChooserView(
                onBasic = { step = SetupStep.Grants },
                onFull = { step = SetupStep.Pairing },
            )
            SetupStep.Pairing -> PairingView(
                onOpenWirelessDebugging = { openDeveloperSettings(ctx) },
                onDone = { step = SetupStep.Grants },
            )
            SetupStep.Grants -> GrantsView(onFinish = onDone)
        }
    }
}

@Composable
private fun ColumnScope.GrantsView(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val caps = CapabilityProbe.rememberCaps() // re-probes on ON_RESUME → status updates when you return
    val permsGranted = caps?.perms?.values?.all { it } == true
    val overlayOn = caps?.overlay == true
    val accessibilityOn = caps?.accessibility == true
    // Re-read on every ON_RESUME. Returning from the assistant picker changes ONLY the "assistant"
    // secure setting, which is NOT part of Caps — so keying off the caps re-probe would stay stale.
    val owner = LocalLifecycleOwner.current
    var assistantOn by remember { mutableStateOf(isDefaultAssistant(ctx)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) assistantOn = isDefaultAssistant(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result reflected by the rememberCaps re-probe */ }
    // Ask for the runtime permissions once on entry; the user can re-tap the row to ask again.
    LaunchedEffect(Unit) { permLauncher.launch(RUNTIME_PERMS) }

    Eyebrow("setup · grant access")
    Spacer(Modifier.height(6.dp))
    H1("Finish granting access")
    Spacer(Modifier.height(8.dp))
    Text(
        "Grant each one in turn — Clyde re-checks when you come back. You can finish now and add the rest later from Home.",
        fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp, color = ClydeColor.Muted,
    )
    Spacer(Modifier.height(16.dp))
    GrantRow("Set Clyde as your assistant", "so the assist gesture opens Clyde, not Gemini", done = assistantOn) { openAssistantSettings(ctx) }
    GrantRow("App permissions", "mic, calls, texts, calendar, location", done = permsGranted) { permLauncher.launch(RUNTIME_PERMS) }
    GrantRow("Draw over apps", "for the summon overlay", done = overlayOn) { requestOverlay(ctx) }
    GrantRow("Accessibility", "to see & tap the screen", done = accessibilityOn) { openAccessibility(ctx) }
    Spacer(Modifier.weight(1f))
    val allDone = assistantOn && permsGranted && overlayOn && accessibilityOn
    PrimaryButton(if (allDone) "All set — open Clyde" else "Finish setup", onClick = onFinish)
}

@Composable
private fun GrantRow(title: String, desc: String, done: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClydeColor.Panel2, RoundedCornerShape(12.dp))
            .border(1.dp, if (done) ClydeColor.Verda else ClydeColor.Line, RoundedCornerShape(12.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ClydeColor.Ink)
            Text(desc, fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
        }
        if (done) {
            Text("granted", fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.VerdaDeep)
        } else {
            Box(
                Modifier
                    .border(1.dp, ClydeColor.Line2, RoundedCornerShape(9.dp))
                    .pressable(label = "Grant $title") { onGrant() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Grant", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClydeColor.BlueText) }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ColumnScope.AutoConfigView(romName: String, rooted: Boolean, onConfigure: () -> Unit, onManual: () -> Unit) {
    Eyebrow("setup · this phone is unlocked")
    Spacer(Modifier.height(6.dp))
    H1("Quick setup on this phone")
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
        ) { Text(if (rooted) "su" else "OS", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ClydeColor.BlueText) }
        Spacer(Modifier.size(11.dp))
        Column {
            Text(if (rooted) "Root access found · $romName" else "Custom ROM · $romName", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5f.sp, color = ClydeColor.Ink)
            Text(if (rooted) "found an su binary on this device" else "matched custom-ROM build props", fontFamily = Mono, fontSize = 11.sp, color = ClydeColor.Muted)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "No ADB pairing needed on this phone. You'll grant a few permissions on the next screen — Android asks for each one as you tap Grant.",
        fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp, color = ClydeColor.Muted,
    )
    Spacer(Modifier.weight(1f))
    PrimaryButton("Continue to permissions", onClick = onConfigure)
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
        why = "You pair once in Android's wireless-debugging dialog.",
        featured = true,
        onClick = onFull,
    )
}

@Composable
private fun ColumnScope.PairingView(onOpenWirelessDebugging: () -> Unit, onDone: () -> Unit) {
    Eyebrow("full control · pair in Android's dialog")
    Spacer(Modifier.height(6.dp))
    H1("Pair with wireless debugging")
    Spacer(Modifier.height(8.dp))
    Text(
        "You enter the code in Android's own secure dialog — Clyde can't see it. Open wireless debugging, tap \"Pair device with code\", type the code there, then come back.",
        fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp, color = ClydeColor.Muted,
    )
    Spacer(Modifier.height(16.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClydeColor.Panel2, RoundedCornerShape(12.dp))
            .border(1.dp, ClydeColor.Line, RoundedCornerShape(12.dp))
            .padding(13.dp),
    ) {
        Text("where to find it", fontFamily = Mono, fontSize = 10.sp, color = ClydeColor.Muted)
        Spacer(Modifier.height(4.dp))
        Text("Settings → Developer options → Wireless debugging → Pair device with code", fontFamily = Mono, fontSize = 11.5f.sp, lineHeight = 17.sp, color = ClydeColor.Ink)
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton("Open wireless debugging", onClick = onOpenWirelessDebugging)
    // Honest: this only advances; the next screen re-probes the REAL Shizuku/ADB capability.
    SecondaryLink("I've paired — continue", onClick = onDone)
}

@Composable
private fun ChoiceCard(title: String, tag: String, desc: String, why: String, featured: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (featured) ClydeColor.Paper else ClydeColor.Panel2, RoundedCornerShape(16.dp))
            .border(if (featured) 2.dp else 1.dp, if (featured) ClydeColor.Blue else ClydeColor.Line, RoundedCornerShape(16.dp))
            .pressable(label = "$title plan") { onClick() }
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ClydeColor.Ink)
            Spacer(Modifier.weight(1f))
            Text(tag.uppercase(), fontFamily = Mono, fontSize = 10.sp, color = if (featured) ClydeColor.BlueText else ClydeColor.Muted)
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

/** Is Clyde the system's chosen assist app? Checks the assist secure settings AND the assistant role,
 *  since which one is populated is OEM-dependent for an ACTION_ASSIST app without a VoiceInteractionService. */
private fun isDefaultAssistant(ctx: Context): Boolean = runCatching {
    val pkg = ctx.packageName
    val cr = ctx.contentResolver
    val assistant = Settings.Secure.getString(cr, "assistant") ?: ""
    val voiceInteraction = Settings.Secure.getString(cr, "voice_interaction_service") ?: ""
    if (assistant.contains(pkg) || voiceInteraction.contains(pkg)) return@runCatching true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = ctx.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) return@runCatching true
    }
    false
}.getOrDefault(false)

/** Send the user to "Assist & voice input" (or Default apps) to pick Clyde — it can't be set silently. */
private fun openAssistantSettings(ctx: Context) {
    for (action in listOf(Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)) {
        if (runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)) return
    }
}

private fun openDeveloperSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
