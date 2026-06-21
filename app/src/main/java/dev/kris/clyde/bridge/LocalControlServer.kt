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
    // Non-destructive token check; the token is only burned (invalidate) after the action SUCCEEDS,
    // so a denied permission or failed fire never costs the user a fresh approval.
    private val validateIntentToken: (token: String, action: String, body: JSONObject) -> Boolean,
    private val invalidateIntentToken: (token: String) -> Unit,
) : NanoHTTPD("127.0.0.1", PORT) {

    companion object {
        const val PORT = 8766
        const val MAX_BODY = 256 * 1024 // reject oversized request bodies before NanoHTTPD buffers them
        // Fail-closed allowlist: ONLY these intents fire without a user-approved token. Everything
        // else (open_url, share_text, send_sms, start_call, add_calendar_event, and any future
        // intent) is gated app-side, never trusting the brain to self-gate (defense-in-depth).
        private val SAFE_INTENTS = setOf("launch_app", "set_alarm", "set_timer", "navigate_to")
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (!authorized(session)) {
                return json(err("bad or missing X-Clyde-Key"), Response.Status.UNAUTHORIZED)
            }
            // Loopback brain delivery for the Termux companion bootstrap (key-gated, non-secret).
            if (session.uri == "/bootstrap.sh") return serveAsset("clyde-bootstrap.sh", "text/x-shellscript; charset=utf-8", stripCr = true)
            if (session.uri == "/brain.tgz") return serveAsset("brain.tgz", "application/gzip", stripCr = false)
            session.headers["content-length"]?.toLongOrNull()?.let {
                if (it > MAX_BODY) return json(err("body too large"), Response.Status.BAD_REQUEST)
            }
            val body = parseJson(session)
            val uri = session.uri
            val result: JSONObject = when {
                uri == "/caps" -> ok(caps())
                uri == "/a11y/dump" -> a11y { it.dumpTree() }
                uri == "/a11y/tap" -> a11yGuard("tap", { if (body.has("nodeId")) it.nodeText(body.getString("nodeId")) else "" }) {
                    if (body.has("nodeId")) it.tapNode(body.getString("nodeId"))
                    else it.tap(body.optInt("x"), body.optInt("y"))
                }
                uri == "/a11y/type" -> a11yGuard("type", { body.optString("text") }) {
                    it.setText(body.optString("nodeId").ifBlank { null }, body.optString("text"))
                }
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
                    val needPerm = DeviceIntents.missingPermissionFor(ctx, name)
                    when {
                        // distinct, recoverable error BEFORE touching the token, so it isn't burned
                        needPerm != null -> err("$name needs the $needPerm permission — grant it in Clyde, then retry")
                        name in SAFE_INTENTS ->
                            if (DeviceIntents.fire(ctx, name, body)) ok(JSONObject().put("fired", name)) else err("$name failed")
                        else -> {
                            val token = body.optString("token")
                            when {
                                token.isBlank() || !validateIntentToken(token, name, body) ->
                                    err("confirmation required: no valid token for $name")
                                DeviceIntents.fire(ctx, name, body) -> {
                                    invalidateIntentToken(token) // single-use: burn only on success
                                    ok(JSONObject().put("fired", name))
                                }
                                else -> err("$name failed") // token preserved — user can retry without re-approving
                            }
                        }
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
                        token.isBlank() || !validateIntentToken(token, "delegate_to_gemini", body) -> err("confirmation required: no valid token for gemini")
                        GeminiRouter.delegate(ctx, body.optString("prompt")) -> {
                            invalidateIntentToken(token)
                            ok(JSONObject().put("delegated", true))
                        }
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
        // Compare fixed-length SHA-256 digests so the length short-circuit can't leak the key's length.
        val a = MessageDigest.getInstance("SHA-256").digest(provided.toByteArray())
        val b = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return MessageDigest.isEqual(a, b)
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

    /** Gate a screen-driving action behind a user confirm when the foreground app or tap/typed target
     *  looks payment/auth-sensitive. Closes the prompt-injection path where on-screen content steers
     *  Clyde to tap "Pay"/"Confirm" — screen-driving is otherwise exempt from the confirm-token model. */
    private fun a11yGuard(
        action: String,
        targetText: (PhoneControlAccessibilityService) -> String,
        block: (PhoneControlAccessibilityService) -> Boolean,
    ): JSONObject {
        val svc = PhoneControlAccessibilityService.instance ?: return err("accessibility off")
        val pkg = svc.foregroundPackage()
        val text = runCatching { targetText(svc) }.getOrDefault("")
        if (SensitiveContext.isSensitive(pkg, text)) {
            val details = if (text.isNotBlank()) "“${text.take(60)}” in $pkg" else "in $pkg"
            val (approved, _) = confirmHandler("Let Clyde $action here?", details, "ui_$action", JSONObject().put("pkg", pkg).put("text", text))
            if (!approved) return err("blocked: “$action” in a sensitive app needs your ok")
        }
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

    /** Serve a bundled asset (the bootstrap script / brain tarball) to the Termux bootstrap. */
    private fun serveAsset(name: String, mime: String, stripCr: Boolean): Response = try {
        var bytes = ctx.assets.open(name).use { it.readBytes() }
        if (stripCr) bytes = String(bytes, Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)
        newFixedLengthResponse(Response.Status.OK, mime, java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
    } catch (_: Exception) {
        json(err("asset unavailable: $name"), Response.Status.INTERNAL_ERROR)
    }
}

/** Heuristic: is a screen-driving action targeting a payment/auth-sensitive surface? Defense-in-depth
 *  so tap/type can't silently push "Pay"/"Confirm" in a wallet, bank, or store via prompt injection. */
private object SensitiveContext {
    private val PKGS = setOf(
        "com.android.vending",                          // Play Store (purchases)
        "com.google.android.apps.walletnfcrel",         // Google Wallet
        "com.google.android.apps.nbu.paisa.user",       // Google Pay
    )
    private val HINTS = listOf("bank", "wallet", "upi", "paypal", "venmo", "cash", "finance", "money", "credit", "invest", "coinbase", "binance", "crypto", "trading", "pay")
    private val MONEY = Regex("\\b(pay|send money|transfer|withdraw|deposit|checkout|purchase|place order|authori[sz]e|confirm payment|buy now|send \\$)", RegexOption.IGNORE_CASE)
    fun isSensitive(pkg: String, text: String): Boolean {
        val p = pkg.lowercase()
        if (pkg in PKGS) return true
        if (HINTS.any { p.contains(it) }) return true
        return text.isNotBlank() && MONEY.containsMatchIn(text)
    }
}
