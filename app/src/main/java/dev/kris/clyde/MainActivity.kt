package dev.kris.clyde

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.kris.clyde.home.HomeScreen
import dev.kris.clyde.service.AgentOrchestratorService
import dev.kris.clyde.login.LoginScreen
import dev.kris.clyde.login.VerifyScreen
import dev.kris.clyde.setup.SetupScreen
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.ClydeTheme
import dev.kris.clyde.util.Prefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ClydeTheme { ClydeRoot() } }
    }
}

private enum class Screen { Login, Verify, Setup, Home }

@Composable
private fun ClydeRoot() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf(if (Prefs.signedIn) Screen.Home else Screen.Login) }
    Surface(modifier = Modifier.fillMaxSize(), color = ClydeColor.Paper) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(340)) { w -> dir * w / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(340)) { w -> -dir * w / 4 } + fadeOut(tween(220)))
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Login -> LoginScreen(onStartSignIn = { screen = Screen.Verify })
                Screen.Verify -> VerifyScreen(onContinue = {
                    Prefs.signedIn = true
                    screen = Screen.Setup
                })
                Screen.Setup -> SetupScreen(onDone = { screen = Screen.Home })
                Screen.Home -> HomeScreen(onAsk = {
                    val i = Intent(ctx, AgentOrchestratorService::class.java).setAction(AgentOrchestratorService.ACTION_ASSIST)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                })
            }
        }
    }
}
