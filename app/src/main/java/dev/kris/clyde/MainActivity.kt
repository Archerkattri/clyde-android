package dev.kris.clyde

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.kris.clyde.home.HomeScreen
import dev.kris.clyde.service.AgentOrchestratorService
import dev.kris.clyde.login.LoginScreen
import dev.kris.clyde.login.VerifyScreen
import dev.kris.clyde.setup.BrainSetupScreen
import dev.kris.clyde.setup.SetupScreen
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.ClydeTheme
import dev.kris.clyde.ui.reduceMotion
import dev.kris.clyde.util.Prefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Clyde is a pop-up assistant, not an app you sit in. Once it's set up (signed in + overlay
        // permission granted), launching the icon SUMMONS THE FLOATING POPUP and gets out of the way —
        // it does not open the full control center. The control center stays reachable via the launcher
        // long-press "Clyde settings" shortcut (action HOME), and during onboarding we always show the UI.
        val wantsHome = intent?.action == ACTION_HOME
        if (!wantsHome && Prefs.signedIn && Settings.canDrawOverlays(this)) {
            val i = Intent(this, AgentOrchestratorService::class.java)
                .setAction(AgentOrchestratorService.ACTION_ASSIST)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
            finish()
            return
        }
        setContent { ClydeTheme { ClydeRoot() } }
    }

    companion object {
        /** Launcher shortcut / in-app "settings" entry — forces the control center instead of the popup. */
        const val ACTION_HOME = "dev.kris.clyde.action.HOME"
    }
}

private enum class Screen { Login, BrainSetup, Verify, Setup, Home }

@Composable
private fun ClydeRoot() {
    val ctx = LocalContext.current
    val reduce = reduceMotion()
    // rememberSaveable so a process-death restart resumes the right screen; configChanges (manifest)
    // keeps rotation/dark-mode/font-scale from recreating the Activity and restarting onboarding.
    var screen by rememberSaveable { mutableStateOf(if (Prefs.signedIn) Screen.Home else Screen.Login) }
    Surface(modifier = Modifier.fillMaxSize(), color = ClydeColor.Paper) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val dir = if (forward) 1 else -1
                val slide = if (reduce) 0 else 340
                (slideInHorizontally(tween(slide)) { w -> dir * w / 4 } + fadeIn(tween(if (reduce) 0 else 300))) togetherWith
                    (slideOutHorizontally(tween(slide)) { w -> -dir * w / 4 } + fadeOut(tween(if (reduce) 0 else 220)))
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Login -> LoginScreen(onStartSignIn = { screen = Screen.BrainSetup })
                Screen.BrainSetup -> BrainSetupScreen(
                    onConnected = { screen = Screen.Verify },
                    // "Set up later" must still land the user in the app (with a Home breadcrumb to
                    // return) — not park them on Verify, which can never pass while the brain is down.
                    onSkip = { Prefs.signedIn = true; screen = Screen.Setup },
                )
                Screen.Verify -> VerifyScreen(onContinue = {
                    Prefs.signedIn = true
                    screen = Screen.Setup
                })
                Screen.Setup -> SetupScreen(onDone = { screen = Screen.Home })
                Screen.Home -> HomeScreen(
                    onAsk = {
                        val i = Intent(ctx, AgentOrchestratorService::class.java).setAction(AgentOrchestratorService.ACTION_ASSIST)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                    },
                    onConnectBrain = { screen = Screen.BrainSetup },
                )
            }
        }
    }
}
