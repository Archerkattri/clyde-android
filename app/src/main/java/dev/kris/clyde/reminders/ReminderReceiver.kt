package dev.kris.clyde.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.kris.clyde.service.AgentOrchestratorService

/** Fires when a reminder's AlarmManager alarm goes off. Always posts a notification; then, best-effort,
 *  asks the orchestrator to surface + speak it and run any attached action. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.rescheduleAll(ctx) // alarms don't survive reboot → re-arm them
            return
        }
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
        val r = Reminders.fired(ctx, id) ?: return // already cancelled/handled (recurring re-arms its next fire inside)
        val text = r.optString("text")
        val action = r.optString("action")

        // 1) The reliable reminder — a notification, works even if the app/brain is dead.
        Reminders.notify(ctx, text)

        // 2) Best-effort: surface + speak, and run the attached action through the brain (consequential
        //    steps still confirm). If the service/brain can't come up, the notification already stands.
        runCatching {
            val svc = Intent(ctx, AgentOrchestratorService::class.java)
                .setAction(AgentOrchestratorService.ACTION_REMINDER_FIRED)
                .putExtra("text", text)
                .putExtra("action", action)
            ctx.startForegroundService(svc)
        }.onFailure { Log.w("Clyde", "reminder: couldn't start orchestrator (notification still shown)", it) }
    }
}
