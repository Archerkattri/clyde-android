package dev.kris.clyde.router

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/** Toggle Claude⇄Gemini (assistant picker) + delegate to Gemini for image/Nano. */
object GeminiRouter {

    /** Deep-link to the Digital-assistant / voice-input picker so the user can switch. */
    fun openAssistantPicker(ctx: Context) {
        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Best-effort hand-off of a prompt to the Gemini app (image/video/Nano). */
    fun delegate(ctx: Context, prompt: String): Boolean = try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage("com.google.android.apps.bard")
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, prompt)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w("Clyde", "Gemini delegate failed", e)
        false
    }
}
