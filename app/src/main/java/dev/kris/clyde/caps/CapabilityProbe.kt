package dev.kris.clyde.caps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                // POST_NOTIFICATIONS is only a runtime grant on API 33+; treat older as granted.
                "notifications" to (Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)),
                "call" to granted(ctx, Manifest.permission.CALL_PHONE),
                "sms" to granted(ctx, Manifest.permission.SEND_SMS),
                "location" to granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION),
                "calendar" to granted(ctx, Manifest.permission.WRITE_CALENDAR),
                "contacts" to granted(ctx, Manifest.permission.READ_CONTACTS),
            ),
        )
    }

    private fun granted(ctx: Context, perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    /**
     * Composable that probes capabilities OFF the main thread and re-probes on every ON_RESUME,
     * so flipping Accessibility/overlay in system Settings and returning reflects immediately.
     * Returns null until the first probe completes.
     */
    @Composable
    fun rememberCaps(): Caps? {
        val ctx = LocalContext.current
        val owner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        var caps by remember { mutableStateOf<Caps?>(null) }
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    scope.launch { caps = withContext(Dispatchers.IO) { probe(ctx) } }
                }
            }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
        return caps
    }

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
