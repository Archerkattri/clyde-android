package dev.kris.clyde.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log

/** Fires Termux's RUN_COMMAND intent. Requires com.termux.permission.RUN_COMMAND and
 *  Termux `allow-external-apps=true`. Used by the companion build to sync the loopback key to Termux. */
object TermuxRunCommand {
    private const val TERMUX = "com.termux"
    private const val RUN_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION = "com.termux.RUN_COMMAND"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"

    /** Real check (not a guess): is the Termux app actually installed? Needs <queries> com.termux. */
    fun isTermuxInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(TERMUX, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Open Termux's F-Droid page so the user can install it (Termux isn't on Google Play). */
    fun openTermuxInstall(ctx: Context): Boolean = runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    /** Bring Termux to the foreground via a normal app launch — works even before allow-external-apps
     *  is enabled (unlike RUN_COMMAND), so the "Open Termux" button is reliable on a fresh install. */
    fun openTermuxApp(ctx: Context): Boolean = runCatching {
        val i = ctx.packageManager.getLaunchIntentForPackage(TERMUX) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

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
            Log.w("Clyde", "Termux RUN_COMMAND failed; ensure Termux is installed and allow-external-apps is enabled", e)
            false
        }
    }
}
