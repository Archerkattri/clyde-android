package dev.kris.clyde.intents

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tier-0 READ queries via first-party Android content providers / PackageManager. Read-only and
 * safe (no confirm token); returns JSON to the brain. Embedded-runtime friendly — goes through the
 * app's own permissions, not Termux:API.
 */
object DeviceQueries {

    /** The runtime permission a query needs but doesn't have — null if none required or already
     *  granted. Checked before the query so a denied permission surfaces a clear "grant it" message
     *  instead of an empty result. */
    fun missingPermissionFor(ctx: Context, name: String): String? {
        val perm = when (name) {
            "find_contact" -> Manifest.permission.READ_CONTACTS
            "list_calendar_events" -> Manifest.permission.READ_CALENDAR
            else -> return null
        }
        val granted = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        return if (granted) null else perm.substringAfterLast('.')
    }

    /** Run a read query. Returns JSON, or null for an unknown query name. */
    fun query(ctx: Context, name: String, body: JSONObject): JSONObject? = when (name) {
        "find_contact" -> findContact(ctx, body.optString("name"))
        "list_apps" -> listApps(ctx, body.optString("filter"))
        "list_calendar_events" -> listCalendarEvents(ctx, body.optInt("days", 7).coerceIn(1, 60))
        else -> null
    }

    private fun findContact(ctx: Context, name: String): JSONObject {
        val out = JSONArray()
        if (name.isNotBlank()) runCatching {
            val proj = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null,
            )?.use { c ->
                val ni = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val pi = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                var n = 0
                while (c.moveToNext() && n < 15) {
                    out.put(JSONObject().put("name", c.getString(ni) ?: "").put("number", c.getString(pi) ?: ""))
                    n++
                }
            }
        }
        return JSONObject().put("contacts", out)
    }

    private fun listApps(ctx: Context, filter: String): JSONObject {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val f = filter.lowercase()
        val apps = JSONArray()
        pm.queryIntentActivities(main, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .filter { f.isBlank() || it.first.lowercase().contains(f) }
            .sortedBy { it.first.lowercase() }
            .take(80)
            .forEach { apps.put(JSONObject().put("label", it.first).put("package", it.second)) }
        return JSONObject().put("apps", apps)
    }

    private fun listCalendarEvents(ctx: Context, days: Int): JSONObject {
        val out = JSONArray()
        runCatching {
            val now = System.currentTimeMillis()
            val end = now + days.toLong() * 24L * 60L * 60L * 1000L
            val proj = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.EVENT_LOCATION,
            )
            ctx.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, proj,
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(now.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC",
            )?.use { c ->
                val ti = c.getColumnIndex(CalendarContract.Events.TITLE)
                val si = c.getColumnIndex(CalendarContract.Events.DTSTART)
                val li = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                var n = 0
                while (c.moveToNext() && n < 30) {
                    out.put(
                        JSONObject()
                            .put("title", c.getString(ti) ?: "")
                            .put("startMillis", c.getLong(si))
                            .put("location", c.getString(li) ?: ""),
                    )
                    n++
                }
            }
        }
        return JSONObject().put("events", out)
    }
}
