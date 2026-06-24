package dev.kris.clyde.intents

import android.Manifest
import android.app.NotificationManager
import android.app.SearchManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** Tier-0: fire Android intents using the app's own permissions. Returns success. */
object DeviceIntents {

    fun fire(ctx: Context, name: String, body: JSONObject): Boolean = when (name) {
        "launch_app" -> launchApp(ctx, body.optString("package"), body.optString("query"))
        "play_media" -> playMedia(ctx, body.optString("query"), body.optString("package"))
        "media_control" -> mediaControl(ctx, body.optString("action"))
        "compose_email" -> composeEmail(ctx, body.optString("to"), body.optString("subject"), body.optString("body"))
        "web_search" -> webSearch(ctx, body.optString("query"))
        "open_settings_panel" -> openSettingsPanel(ctx, body.optString("panel"))
        "set_dnd" -> setDnd(ctx, body.optString("mode"))
        "set_ringer_mode" -> setRingerMode(ctx, body.optString("mode"))
        "set_brightness" -> setBrightness(ctx, body.optInt("level"))
        "set_alarm" -> setAlarm(ctx, body.optInt("hour"), body.optInt("minutes"), body.optString("message"))
        "set_timer" -> setTimer(ctx, body.optInt("seconds"), body.optString("message"))
        "navigate_to" -> navigateTo(ctx, body.optString("destination"), body.optString("mode"))
        "open_url" -> openUrl(ctx, body.optString("url"))
        "share_text" -> shareText(ctx, body.optString("content"), body.optString("targetPackage", null))
        "start_call" -> startCall(ctx, body.optString("number"))
        "send_sms" -> sendSms(ctx, body.optString("to"), body.optString("body"))
        "add_calendar_event" -> addCalendarEvent(ctx, body)
        else -> false
    }

    /**
     * The runtime permission an intent needs but doesn't have yet — null if none required or already
     * granted. Checked BEFORE consuming the user's one-time token so a denied permission surfaces a
     * clear error instead of silently failing and burning the approval.
     */
    fun missingPermissionFor(ctx: Context, name: String): String? {
        val perm = when (name) {
            "start_call" -> Manifest.permission.CALL_PHONE
            "send_sms" -> Manifest.permission.SEND_SMS
            "add_calendar_event" -> Manifest.permission.WRITE_CALENDAR
            else -> return null
        }
        val granted = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        return if (granted) null else perm.substringAfterLast('.')
    }

    /** Special access (not a runtime permission) a tool needs but doesn't have — null if granted.
     *  Checked before the confirm token so the user is sent to enable it instead of burning approval. */
    fun missingAccessFor(ctx: Context, name: String): String? = when (name) {
        "set_dnd", "set_ringer_mode" ->
            if (ctx.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true) null
            else "Do Not Disturb access"
        "set_brightness" -> if (Settings.System.canWrite(ctx)) null else "permission to modify system settings"
        else -> null
    }

    /** Open the settings screen where the user grants the special access a tool needs. */
    fun openAccessSettings(ctx: Context, name: String) {
        val intent = when (name) {
            "set_dnd", "set_ringer_mode" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "set_brightness" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName))
            else -> return
        }
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
            val acts = pm.queryIntentActivities(main, 0)
            // prefer exact label, then prefix, then substring — avoids launching an impostor
            val match = acts.firstOrNull { it.loadLabel(pm).toString().equals(query, ignoreCase = true) }
                ?: acts.firstOrNull { it.loadLabel(pm).toString().startsWith(query, ignoreCase = true) }
                ?: acts.firstOrNull { it.loadLabel(pm).toString().contains(query, ignoreCase = true) }
            match?.activityInfo?.packageName?.let { p ->
                pm.getLaunchIntentForPackage(p)?.let { return start(ctx, it) }
            }
        }
        return false
    }

    /** Android's standard "play from search": any media app that registers for it (YouTube Music,
     *  Spotify, …) resolves the query and plays — the same mechanism a system assistant uses. */
    private fun playMedia(ctx: Context, query: String, pkg: String): Boolean {
        if (query.isBlank()) return false
        return start(ctx, Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
            // Target a specific media player when the brain resolved one; otherwise
            // the system routes to the default media app.
            if (pkg.isNotBlank()) setPackage(pkg)
        })
    }

    /** Control current playback by dispatching a media-button event to the active media session. */
    private fun mediaControl(ctx: Context, action: String): Boolean {
        val code = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            "rewind" -> KeyEvent.KEYCODE_MEDIA_REWIND
            "fast_forward" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            else -> return false
        }
        return try {
            val am = ctx.getSystemService(AudioManager::class.java) ?: return false
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun composeEmail(ctx: Context, to: String, subject: String, body: String): Boolean =
        start(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to))).apply {
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
        })

    private fun webSearch(ctx: Context, query: String): Boolean {
        if (query.isBlank()) return false
        return start(ctx, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)) ||
            start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
    }

    /** Open a system settings screen so the user can flip something apps can't change directly. */
    private fun openSettingsPanel(ctx: Context, panel: String): Boolean {
        val intent = when (panel) {
            "wifi" -> Intent(Settings.Panel.ACTION_WIFI)
            "internet" -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            "volume" -> Intent(Settings.Panel.ACTION_VOLUME)
            "nfc" -> Intent(Settings.Panel.ACTION_NFC)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "battery_saver" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "data_usage" -> Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
            "app_details" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        return start(ctx, intent)
    }

    private fun setDnd(ctx: Context, mode: String): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        val filter = when (mode) {
            "off", "all" -> NotificationManager.INTERRUPTION_FILTER_ALL
            "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            "silence", "none", "total" -> NotificationManager.INTERRUPTION_FILTER_NONE
            else -> return false
        }
        return try { nm.setInterruptionFilter(filter); true } catch (_: Exception) { false }
    }

    private fun setRingerMode(ctx: Context, mode: String): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        val m = when (mode) {
            "normal", "ring" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> return false
        }
        return try { am.ringerMode = m; true } catch (_: Exception) { false }
    }

    private fun setBrightness(ctx: Context, level: Int): Boolean = try {
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level.coerceIn(0, 255))
        true
    } catch (_: Exception) {
        false
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
        val trimmed = url.trim()
        val u = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.contains("://") -> return false // a non-web scheme (intent:/file:/content:…) — refuse
            else -> "https://$trimmed"               // bare domain → assume https
        }
        val parsed = runCatching { Uri.parse(u) }.getOrNull() ?: return false
        if (parsed.scheme?.lowercase() !in setOf("http", "https")) return false // open_url is web-only
        return start(ctx, Intent(Intent.ACTION_VIEW, parsed).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    private fun shareText(ctx: Context, text: String, targetPackage: String?): Boolean =
        start(ctx, Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!targetPackage.isNullOrBlank()) setPackage(targetPackage)
        })

    private fun startCall(ctx: Context, number: String): Boolean {
        val clean = number.filter { it.isDigit() || it in "+*#" }
        if (clean.isBlank()) return false // no silent "success" on an empty/contact-only call
        return start(ctx, Intent(Intent.ACTION_CALL, Uri.parse("tel:$clean")))
    }

    private fun sendSms(ctx: Context, to: String, body: String): Boolean {
        if (to.isBlank() || body.isBlank()) return false
        return try {
            ctx.getSystemService(SmsManager::class.java).sendTextMessage(to, null, body, null, null)
            true
        } catch (_: Exception) {
            false
        }
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
