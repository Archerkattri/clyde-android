package dev.kris.clyde.bridge

import android.content.Context
import dev.kris.clyde.caps.CapabilityProbe
import dev.kris.clyde.intents.DeviceIntents
import dev.kris.clyde.router.GeminiRouter
import dev.kris.clyde.service.PhoneControlAccessibilityService
import dev.kris.clyde.voice.VoiceIO
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import org.json.JSONObject
import java.security.MessageDigest

/** The app's action API on 127.0.0.1:8766 — the brain calls these. Loopback + X-Clyde-Key. */
class LocalControlServer(
    private val ctx: Context,
    private val voice: VoiceIO,
    private val key: String,
    private val confirmHandler: (summary: String, details: String?, action: String, params: JSONObject) -> Pair<Boolean, String?>,
    private val overlayStatus: (text: String, state: String?) -> Unit,
    private val consumeIntentToken: (token: String, action: String, body: JSONObject) -> Boolean,
) : NanoHTTPD("127.0.0.1", PORT) {

    companion object {
        const val PORT = 8766
        // Intents the app independently gates with a user-approved, one-time token —
        // never trusting the brain to self-gate (defense-in-depth).
        private val CONSEQUENTIAL_INTENTS = setOf("send_sms", "start_call", "add_calendar_event")
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (!authorized(session)) {
                return json(err("bad or missing X-Clyde-Key"), Response.Status.UNAUTHORIZED)
            }
            val body = parseJson(session)
            val uri = session.uri
            val result: JSONObject = when {
                uri == "/caps" -> ok(caps())
                uri == "/a11y/dump" -> a11y { it.dumpTree() }
                uri == "/a11y/tap" -> a11yBool {
                    if (body.has("nodeId")) it.tapNode(body.getString("nodeId"))
                    else it.tap(body.optInt("x"), body.optInt("y"))
                }
                uri == "/a11y/type" -> a11yBool { it.setText(body.optString("nodeId").ifBlank { null }, body.optString("text")) }
                uri == "/a11y/swipe" -> a11yBool {
                    it.swipe(body.optInt("x1"), body.optInt("y1"), body.optInt("x2"), body.optInt("y2"), body.optLong("ms", 300))
                }
                uri == "/a11y/global" -> a11yBool { it.global(body.optString("action")) }
                uri == "/a11y/screenshot" -> {
                    val svc = PhoneControlAccessibilityService.instance
                    if (svc == null) err("accessibility off")
                    else svc.screenshotBase64()?.let { ok(JSONObject().put("pngBase64", it)) } ?: err("screenshot failed")
                }
                uri.startsWith("/intent/") -> {
                    val name = uri.removePrefix("/intent/")
                    if (name in CONSEQUENTIAL_INTENTS) {
                        val token = body.optString("token")
                        when {
                            token.isBlank() || !consumeIntentToken(token, name, body) -> err("confirmation required: no valid token for $name")
                            DeviceIntents.fire(ctx, name, body) -> ok(JSONObject().put("fired", name))
                            else -> err("$name failed")
                        }
                    } else if (DeviceIntents.fire(ctx, name, body)) {
                        ok(JSONObject().put("fired", name))
                    } else {
                        err("$name failed")
                    }
                }
                uri == "/speak" -> { voice.speak(body.optString("text")); ok(JSONObject()) }
                uri == "/overlay/status" -> {
                    overlayStatus(body.optString("text"), body.optString("state").ifBlank { null }); ok(JSONObject())
                }
                uri == "/confirm" -> {
                    val (approved, token) = confirmHandler(
                        body.optString("summary"),
                        body.optString("details").ifBlank { null },
                        body.optString("action"),
                        body.optJSONObject("params") ?: JSONObject(),
                    )
                    ok(JSONObject().put("approved", approved).also { if (token != null) it.put("token", token) })
                }
                uri == "/gemini/delegate" -> {
                    val token = body.optString("token")
                    when {
                        token.isBlank() || !consumeIntentToken(token, "delegate_to_gemini", body) -> err("confirmation required: no valid token for gemini")
                        GeminiRouter.delegate(ctx, body.optString("prompt")) -> ok(JSONObject().put("delegated", true))
                        else -> err("gemini failed")
                    }
                }
                uri == "/ping" -> ok(JSONObject().put("pong", true))
                else -> err("not found: $uri")
            }
            json(result)
        } catch (_: Exception) {
            json(err("internal error")) // don't echo internals to the caller
        }
    }

    private fun authorized(session: IHTTPSession): Boolean {
        if (key.isEmpty()) return false // the app always generates a key; empty means misconfig → reject
        val provided = session.headers["x-clyde-key"] ?: return false
        return MessageDigest.isEqual(provided.toByteArray(), key.toByteArray())
    }

    private fun caps(): JSONObject {
        val c = CapabilityProbe.probe(ctx)
        val perms = JSONObject()
        c.perms.forEach { (k, v) -> perms.put(k, v) }
        return JSONObject()
            .put("accessibility", c.accessibility)
            .put("shizuku", c.shizuku)
            .put("root", c.root)
            .put("overlay", c.overlay)
            .put("perms", perms)
    }

    private fun a11y(block: (PhoneControlAccessibilityService) -> JSONObject): JSONObject {
        val svc = PhoneControlAccessibilityService.instance ?: return err("accessibility off")
        return ok(block(svc))
    }

    private fun a11yBool(block: (PhoneControlAccessibilityService) -> Boolean): JSONObject {
        val svc = PhoneControlAccessibilityService.instance ?: return err("accessibility off")
        return if (block(svc)) ok(JSONObject()) else err("action failed")
    }

    private fun ok(result: JSONObject) = JSONObject().put("ok", true).put("result", result)
    private fun err(msg: String) = JSONObject().put("ok", false).put("error", msg)

    private fun parseJson(session: IHTTPSession): JSONObject = try {
        if (session.method == Method.POST || session.method == Method.PUT) {
            val map = HashMap<String, String>()
            session.parseBody(map)
            map["postData"]?.let { JSONObject(it) } ?: JSONObject()
        } else {
            JSONObject()
        }
    } catch (_: Exception) {
        JSONObject()
    }

    private fun json(obj: JSONObject, status: Response.Status = Response.Status.OK): Response =
        newFixedLengthResponse(status, "application/json", obj.toString())
}
