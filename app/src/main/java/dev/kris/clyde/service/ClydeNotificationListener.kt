package dev.kris.clyde.service

import android.service.notification.NotificationListenerService
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tier-0 read: the user's active notifications. Singleton like the accessibility service — the system
 * binds this once the user grants Notification access (a special access, not a runtime permission).
 * When access is off, `instance` is null and the read query surfaces a "grant it" message.
 */
class ClydeNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: ClydeNotificationListener? = null

        /** Active notifications as JSON (most recent first), or null if the listener isn't connected. */
        fun snapshot(): JSONArray? {
            val svc = instance ?: return null
            val arr = JSONArray()
            runCatching {
                svc.activeNotifications
                    ?.sortedByDescending { it.postTime }
                    ?.take(40)
                    ?.forEach { sbn ->
                        val ex = sbn.notification?.extras ?: return@forEach
                        val title = ex.getCharSequence("android.title")?.toString().orEmpty()
                        val text = ex.getCharSequence("android.text")?.toString().orEmpty()
                        if (title.isBlank() && text.isBlank()) return@forEach
                        arr.put(
                            JSONObject()
                                .put("app", sbn.packageName)
                                .put("title", title)
                                .put("text", text)
                                .put("postedMillis", sbn.postTime),
                        )
                    }
            }
            return arr
        }
    }

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { instance = null }
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
