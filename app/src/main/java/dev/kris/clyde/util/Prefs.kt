package dev.kris.clyde.util

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/** App preferences + the loopback shared secret (X-Clyde-Key). Init in ClydeApp.onCreate. */
object Prefs {
    private const val FILE = "clyde_prefs"
    private const val KEY_CLYDE = "clyde_key"
    private const val KEY_SIGNED_IN = "signed_in"

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (sp.getString(KEY_CLYDE, null).isNullOrEmpty()) {
            sp.edit().putString(KEY_CLYDE, generateKey()).apply()
        }
    }

    /** 32 hex chars, shared with brain/.env CLYDE_KEY. */
    val clydeKey: String get() = sp.getString(KEY_CLYDE, "").orEmpty()

    var signedIn: Boolean
        get() = sp.getBoolean(KEY_SIGNED_IN, false)
        set(value) = sp.edit().putBoolean(KEY_SIGNED_IN, value).apply()

    private fun generateKey(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
