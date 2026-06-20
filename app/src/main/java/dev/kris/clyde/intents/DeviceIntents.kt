package dev.kris.clyde.intents

import android.app.SearchManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.telephony.SmsManager
import org.json.JSONObject

/** Tier-0: fire Android intents using the app's own permissions. Returns success. */
object DeviceIntents {

    fun fire(ctx: Context, name: String, body: JSONObject): Boolean = when (name) {
        "launch_app" -> launchApp(ctx, body.optString("package"), body.optString("query"))
        "set_alarm" -> setAlarm(ctx, body.optInt("hour"), body.optInt("minutes"), body.optString("message"))
        "set_timer" -> setTimer(ctx, body.optInt("seconds"), body.optString("message"))
        "navigate_to" -> navigateTo(ctx, body.optString("destination"), body.optString("mode"))
        "open_url" -> openUrl(ctx, body.optString("url"))
        "share_text" -> shareText(ctx, body.optString("text"), body.optString("targetPackage", null))
        "start_call" -> startCall(ctx, body.optString("number"))
        "send_sms" -> sendSms(ctx, body.optString("to"), body.optString("body"))
        "add_calendar_event" -> addCalendarEvent(ctx, body)
        else -> false
    }

    private fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    private fun launchApp(ctx: Context, pkg: String?, query: String?): Boolean {
        val pm = ctx.packageManager
        if (!pkg.isNullOrBlank()) {
            pm.getLaunchIntentForPackage(pkg)?.let { return start(ctx, it) }
        }
        if (!query.isNullOrBlank()) {
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val match = pm.queryIntentActivities(main, 0).firstOrNull {
                it.loadLabel(pm).toString().contains(query, ignoreCase = true)
            }
            match?.activityInfo?.packageName?.let { p ->
                pm.getLaunchIntentForPackage(p)?.let { return start(ctx, it) }
            }
        }
        return false
    }

    private fun setAlarm(ctx: Context, hour: Int, minutes: Int, message: String?): Boolean =
        start(ctx, Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            if (!message.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        })

    private fun setTimer(ctx: Context, seconds: Int, message: String?): Boolean =
        start(ctx, Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            if (!message.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        })

    private fun navigateTo(ctx: Context, destination: String, mode: String?): Boolean {
        val m = when (mode) { "walk" -> "w"; "transit" -> "r"; "bike" -> "b"; else -> "d" }
        val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=$m")
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        if (start(ctx, intent)) return true
        return start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}")))
    }

    private fun openUrl(ctx: Context, url: String): Boolean {
        val u = if (url.startsWith("http")) url else "https://$url"
        return start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    }

    private fun shareText(ctx: Context, text: String, targetPackage: String?): Boolean =
        start(ctx, Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!targetPackage.isNullOrBlank()) setPackage(targetPackage)
        })

    private fun startCall(ctx: Context, number: String): Boolean =
        start(ctx, Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))

    private fun sendSms(ctx: Context, to: String, body: String): Boolean = try {
        val sms = ctx.getSystemService(SmsManager::class.java)
        sms.sendTextMessage(to, null, body, null, null)
        true
    } catch (_: Exception) {
        false
    }

    private fun addCalendarEvent(ctx: Context, body: JSONObject): Boolean = try {
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, body.optString("title"))
            put(CalendarContract.Events.EVENT_LOCATION, body.optString("location"))
            put(CalendarContract.Events.DTSTART, parseIso(body.optString("startIso")))
            put(CalendarContract.Events.DTEND, parseIso(body.optString("endIso").ifBlank { body.optString("startIso") }))
            put(CalendarContract.Events.CALENDAR_ID, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
    } catch (_: Exception) {
        false
    }

    private fun parseIso(iso: String): Long = try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}
