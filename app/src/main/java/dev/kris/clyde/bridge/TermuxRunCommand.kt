package dev.kris.clyde.bridge

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** Fires Termux's RUN_COMMAND intent. Requires com.termux.permission.RUN_COMMAND and
 *  Termux `allow-external-apps=true`. Used to launch `claude login` and (Model B) oneshot. */
object TermuxRunCommand {
    private const val TERMUX = "com.termux"
    private const val RUN_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION = "com.termux.RUN_COMMAND"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"

    /** Opens a visible Termux session running `claude login` so the user can finish OAuth. */
    fun startClaudeLogin(ctx: Context): Boolean = runInTermux(ctx, "claude login", background = false)

    fun runInTermux(ctx: Context, command: String, background: Boolean): Boolean {
        val intent = Intent().apply {
            setClassName(TERMUX, RUN_SERVICE)
            action = ACTION
            putExtra("com.termux.RUN_COMMAND_PATH", BASH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
            true
        } catch (e: Exception) {
            Log.w("Clyde", "Termux RUN_COMMAND failed (is Termux installed + allow-external-apps?)", e)
            false
        }
    }
}
