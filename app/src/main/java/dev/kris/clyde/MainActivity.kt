package dev.kris.clyde

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.kris.clyde.login.LoginScreen
import dev.kris.clyde.login.VerifyScreen
import dev.kris.clyde.ui.ClydeColor
import dev.kris.clyde.ui.ClydeTheme
import dev.kris.clyde.util.Prefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ClydeTheme { ClydeRoot() } }
    }
}

private enum class Screen { Login, Verify, Home }

@Composable
private fun ClydeRoot() {
    var screen by remember { mutableStateOf(if (Prefs.signedIn) Screen.Home else Screen.Login) }
    Surface(modifier = Modifier.fillMaxSize(), color = ClydeColor.Paper) {
        when (screen) {
            Screen.Login -> LoginScreen(onStartSignIn = { screen = Screen.Verify })
            Screen.Verify -> VerifyScreen(onContinue = {
                Prefs.signedIn = true
                screen = Screen.Home
            })
            Screen.Home -> HomePlaceholder()
        }
    }
}

@Composable
private fun HomePlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Home — building next", color = ClydeColor.Muted)
    }
}
