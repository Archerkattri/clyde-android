package dev.kris.clyde

import android.app.Application
import dev.kris.clyde.util.Prefs

class ClydeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Generate the loopback shared secret on first launch; it must match brain/.env.
        Prefs.init(this)
    }
}
