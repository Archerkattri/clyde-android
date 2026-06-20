package dev.kris.clyde.bridge

import dev.kris.clyde.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthStatus(val subscription: Boolean, val apiKeyPresent: Boolean, val plan: String?)

/** Talks to the brain (Claude Agent SDK server) on 127.0.0.1:8765 over the loopback. */
object BrainClient {
    private const val BASE = "http://127.0.0.1:8765"

    suspend fun healthz(): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = open("/healthz", "GET")
            val ok = c.responseCode in 200..299
            c.disconnect()
            ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun authStatus(): AuthStatus? = withContext(Dispatchers.IO) {
        try {
            val c = open("/auth/status", "GET")
            if (c.responseCode !in 200..299) {
                c.disconnect()
                return@withContext null
            }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val j = JSONObject(body)
            AuthStatus(
                subscription = j.optBoolean("subscription", false),
                apiKeyPresent = j.optBoolean("apiKeyPresent", true),
                plan = if (j.isNull("plan")) null else j.optString("plan"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun open(path: String, method: String): HttpURLConnection {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 4000
        c.readTimeout = 6000
        c.setRequestProperty("X-Clyde-Key", Prefs.clydeKey)
        return c
    }
}
