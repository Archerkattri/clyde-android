package dev.kris.clyde.caps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/** What Clyde can do on THIS phone, detected locally. The brain merges this with rish/su probes. */
data class Caps(
    val accessibility: Boolean,
    val overlay: Boolean,
    val root: Boolean,
    val shizuku: Boolean,
    val customRom: Boolean,
    val romName: String?,
    val perms: Map<String, Boolean>,
)

object CapabilityProbe {

    fun probe(ctx: Context): Caps {
        val rom = detectCustomRom()
        return Caps(
            accessibility = isAccessibilityEnabled(ctx),
            overlay = Settings.canDrawOverlays(ctx),
            root = detectRoot(),
            shizuku = detectShizuku(),
            customRom = rom != null,
            romName = rom,
            perms = mapOf(
                "mic" to granted(ctx, Manifest.permission.RECORD_AUDIO),
                "call" to granted(ctx, Manifest.permission.CALL_PHONE),
                "sms" to granted(ctx, Manifest.permission.SEND_SMS),
                "location" to granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION),
                "calendar" to granted(ctx, Manifest.permission.WRITE_CALENDAR),
            ),
        )
    }

    private fun granted(ctx: Context, perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(ctx.packageName, ignoreCase = true)
    }

    private fun detectRoot(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/su/bin/su", "/system/app/Superuser.apk", "/data/adb/magisk",
        )
        return paths.any { File(it).exists() }
    }

    private fun detectShizuku(): Boolean = try {
        rikka.shizuku.Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /** Returns a friendly ROM name if this looks like a custom ROM, else null. */
    private fun detectCustomRom(): String? {
        if (getProp("ro.lineage.version").isNotEmpty()) return "LineageOS"
        if (getProp("ro.modversion").isNotEmpty()) return getProp("ro.modversion")
        val flavor = (getProp("ro.build.flavor") + " " + getProp("ro.product.name")).lowercase()
        return when {
            flavor.contains("graphene") -> "GrapheneOS"
            flavor.contains("calyx") -> "CalyxOS"
            flavor.contains("lineage") -> "LineageOS"
            else -> null
        }
    }

    private fun getProp(key: String): String = try {
        val c = Class.forName("android.os.SystemProperties")
        val m = c.getMethod("get", String::class.java)
        (m.invoke(null, key) as? String).orEmpty()
    } catch (_: Throwable) {
        ""
    }
}
