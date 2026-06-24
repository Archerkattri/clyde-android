package dev.kris.clyde.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.kris.clyde.MainActivity
import dev.kris.clyde.R
import dev.kris.clyde.util.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Time-based reminders: persisted in [Prefs] and scheduled with AlarmManager so they fire even if the
 * app/brain isn't running. On fire, [ReminderReceiver] posts a notification (the reliable part) and —
 * best-effort — surfaces + speaks it and runs any attached action through the brain.
 *
 * Location/other triggers are a follow-up; this is the time/date core.
 */
object Reminders {
    const val EXTRA_ID = "reminder_id"
    private const val FIRE_ACTION = "dev.kris.clyde.REMINDER_FIRE"
    private const val CHANNEL = "clyde_reminders"

    // ── public API (called from LocalControlServer) ──

    /** Create + schedule a reminder. [fireAt] is epoch millis. [action] is an optional command for
     *  Clyde to run when it fires. Returns the stored reminder. */
    fun set(ctx: Context, text: String, fireAt: Long, action: String?, repeat: String? = null): JSONObject {
        val id = "r${System.currentTimeMillis()}${(100..999).random()}"
        val obj = JSONObject()
            .put("id", id)
            .put("text", text)
            .put("fireAt", fireAt)
            .put("action", action ?: "")
            .put("repeat", (repeat ?: "").lowercase().trim()) // "" = one-shot; hourly|daily|weekdays|weekly|monthly
            .put("createdAt", System.currentTimeMillis())
        val arr = load()
        arr.put(obj)
        save(arr)
        schedule(ctx, id, fireAt)
        return obj
    }

    /** All pending reminders, soonest first. */
    fun list(ctx: Context): JSONObject {
        val arr = load()
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }.sortedBy { it.optLong("fireAt") }
        val out = JSONArray()
        items.forEach { out.put(it) }
        return JSONObject().put("reminders", out)
    }

    /** Cancel + unschedule by id. Returns true if it existed. */
    fun cancel(ctx: Context, id: String): Boolean {
        val arr = load()
        val out = JSONArray()
        var found = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) found = true else out.put(o)
        }
        save(out)
        runCatching { ctx.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(ctx, id)) }
        return found
    }

    // ── called from the receiver on fire ──

    /** Re-arm every still-future reminder — call after a reboot, since AlarmManager alarms don't survive
     *  it. Any that came due while powered off fire a catch-up notification so they aren't lost. */
    fun rescheduleAll(ctx: Context) {
        val arr = load()
        val now = System.currentTimeMillis()
        val keep = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val at = o.optLong("fireAt")
            if (at > now) { schedule(ctx, o.optString("id"), at); keep.put(o) }
            else {
                notify(ctx, o.optString("text")) // missed while off → fire now
                val next = nextFireAt(at, o.optString("repeat")) // recurring → re-arm next; one-shot → drop
                if (next != null) { o.put("fireAt", next); schedule(ctx, o.optString("id"), next); keep.put(o) }
            }
        }
        save(keep)
    }

    /** Handle a fired reminder: return it, and either drop it (one-shot) or advance it to its next
     *  occurrence and re-arm the alarm (recurring). Returns null if it was already gone. */
    fun fired(ctx: Context, id: String): JSONObject? {
        val arr = load()
        var found: JSONObject? = null
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id && found == null) {
                found = o
                val next = nextFireAt(o.optLong("fireAt"), o.optString("repeat"))
                if (next != null) { o.put("fireAt", next); out.put(o); schedule(ctx, id, next) } // recurring → keep + re-arm
                // one-shot → not re-added (dropped)
            } else out.put(o)
        }
        if (found != null) save(out)
        return found
    }

    /** Next occurrence STRICTLY in the future for a recurring cadence, or null for one-shot/unknown.
     *  Advances past `now` (bounded) so a phone that was powered off can't immediately re-fire in a loop. */
    private fun nextFireAt(from: Long, repeat: String): Long? {
        val r = repeat.lowercase().trim()
        if (r.isBlank() || r == "none" || r == "once") return null
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = from }
        val now = System.currentTimeMillis()
        var guard = 0
        do {
            when (r) {
                "hourly" -> cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
                "daily" -> cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                "weekly" -> cal.add(java.util.Calendar.DAY_OF_MONTH, 7)
                "monthly" -> cal.add(java.util.Calendar.MONTH, 1)
                "weekdays" -> {
                    cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                    while (cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SATURDAY ||
                        cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
                    ) cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                else -> return null // unknown cadence → treat as one-shot
            }
        } while (cal.timeInMillis <= now && ++guard < 2000)
        return cal.timeInMillis
    }

    /** The reliable part: a high-priority notification that fires even with the app/brain dead. */
    fun notify(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_clyde_logo)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(("rem:$text:${System.currentTimeMillis()}").hashCode(), n)
    }

    // ── internals ──

    private fun schedule(ctx: Context, id: String, fireAt: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(ctx, id)
        // Exact when allowed (USE_EXACT_ALARM on 33+, SCHEDULE_EXACT_ALARM on 31-32); otherwise the OS
        // may batch it a little, which is fine for reminders. AllowWhileIdle wakes through Doze.
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        runCatching {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
    }

    private fun pendingIntent(ctx: Context, id: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(FIRE_ACTION).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, id.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun load(): JSONArray = runCatching { JSONArray(Prefs.reminders) }.getOrDefault(JSONArray())
    private fun save(arr: JSONArray) { Prefs.reminders = arr.toString() }
}
