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

    /** Stream a query to the brain. Each NDJSON event (status/action/delta/final/error) → onEvent.
     *  [model] is the user's model pick; [backend] is "claude" or "codex"; blank → brain default. */
    suspend fun query(text: String, sessionId: String, model: String? = null, backend: String? = null, onEvent: (JSONObject) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val c = open("/query", "POST")
            c.doOutput = true
            c.readTimeout = 120_000
            c.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject().put("text", text).put("sessionId", sessionId)
            if (!model.isNullOrBlank()) payload.put("model", model)
            if (!backend.isNullOrBlank()) payload.put("backend", backend)
            c.outputStream.use { it.write(payload.toString().toByteArray()) }
            if (c.responseCode in 200..299) {
                c.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) runCatching { onEvent(JSONObject(line)) }
                    }
                }
            } else {
                onEvent(JSONObject().put("type", "error").put("text", "brain ${c.responseCode}"))
            }
            c.disconnect()
        } catch (e: Exception) {
            onEvent(JSONObject().put("type", "error").put("text", "brain unreachable"))
        }
    }

    /** Start the no-paste loopback sign-in; returns the authorize URL to open in the browser, or null. */
    suspend fun startLogin(): String? = withContext(Dispatchers.IO) {
        try {
            val c = open("/auth/login/start", "POST")
            c.doOutput = true
            c.readTimeout = 15_000
            c.outputStream.use { it.write(ByteArray(0)) }
            if (c.responseCode !in 200..299) { c.disconnect(); return@withContext null }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val j = JSONObject(body)
            if (j.optBoolean("ok")) j.optString("url").ifBlank { null } else null
        } catch (_: Exception) {
            null
        }
    }

    data class LoginInfo(val signedIn: Boolean, val pending: Boolean, val error: String)

    /** Poll the loopback sign-in: signedIn once creds are written, or a surfaced exchange error. */
    suspend fun loginStatus(): LoginInfo? = withContext(Dispatchers.IO) {
        try {
            val c = open("/auth/login/status", "GET")
            if (c.responseCode !in 200..299) { c.disconnect(); return@withContext null }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val j = JSONObject(body)
            LoginInfo(j.optBoolean("signedIn"), j.optBoolean("pending"), j.optString("error"))
        } catch (_: Exception) {
            null
        }
    }

    /** Kill switch — invalidate outstanding confirm tokens on the brain. */
    suspend fun kill(): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = open("/kill", "POST")
            c.doOutput = true
            c.outputStream.use { it.write(ByteArray(0)) }
            val ok = c.responseCode in 200..299
            c.disconnect()
            ok
        } catch (_: Exception) {
            false
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
