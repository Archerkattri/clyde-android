package dev.kris.clyde

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.kris.clyde.home.HomeScreen
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
    var screen by remember { mutableStateOf(if (Prefs.signedIn) Screen.Home else Screen.Login) }
    Surface(modifier = Modifier.fillMaxSize(), color = ClydeColor.Paper) {
        when (screen) {
            Screen.Login -> LoginScreen(onStartSignIn = { screen = Screen.Verify })
            Screen.Verify -> VerifyScreen(onContinue = {
                Prefs.signedIn = true
                screen = Screen.Setup
            })
            Screen.Setup -> SetupScreen(onDone = { screen = Screen.Home })
            Screen.Home -> HomeScreen(onAsk = { /* assist overlay wired in a later milestone */ })
        }
    }
}
