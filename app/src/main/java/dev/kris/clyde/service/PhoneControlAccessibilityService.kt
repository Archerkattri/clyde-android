package dev.kris.clyde.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Tier-1 hands: read the screen tree and tap/type/swipe/screenshot. */
class PhoneControlAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PhoneControlAccessibilityService? = null
    }

    // accessed from NanoHTTPD worker threads (dump writes, tap/type read) → concurrent-safe
    private val nodeMap = java.util.concurrent.ConcurrentHashMap<String, AccessibilityNodeInfo>()
    // one reused single-thread executor for screenshots (don't spin a new one per call)
    private val screenshotExec = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        screenshotExec.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** BFS the active window into a compact node list with stable per-dump ids. */
    fun dumpTree(): JSONObject {
        nodeMap.clear()
        val nodes = JSONArray()
        var counter = 0
        val root = rootInActiveWindow ?: return JSONObject().put("nodes", nodes).put("error", "no active window")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && counter < 500) {
            val n = queue.removeFirst()
            val id = "n$counter"
            counter++
            nodeMap[id] = n
            val interesting = !n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank() ||
                n.isClickable || n.isEditable
            if (interesting) {
                val b = Rect().also { n.getBoundsInScreen(it) }
                nodes.put(
                    JSONObject()
                        .put("nodeId", id)
                        .put("role", n.className?.toString() ?: "")
                        // never serialize password-field contents to the model
                        .put("text", if (n.isPassword) "[masked]" else (n.text?.toString() ?: ""))
                        .put("desc", n.contentDescription?.toString() ?: "")
                        .put("bounds", JSONArray(listOf(b.left, b.top, b.right, b.bottom)))
                        .put("clickable", n.isClickable)
                        .put("editable", n.isEditable)
                        .put("scrollable", n.isScrollable)
                        .put("focused", n.isFocused)
                        .put("package", n.packageName?.toString() ?: "")
                )
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return JSONObject().put("nodes", nodes)
    }

    fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun tapNode(nodeId: String): Boolean {
        val node = nodeMap[nodeId] ?: return false
        node.refresh() // the window may have changed since the dump
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        // fall back to a coordinate tap on the node's last-known bounds
        val b = Rect().also { node.getBoundsInScreen(it) }
        return !b.isEmpty && tap(b.centerX(), b.centerY())
    }

    /** Screen center of a dumped node's bounds, for the visual "point before tap" cue. */
    fun nodeCenter(nodeId: String): IntArray? {
        val node = nodeMap[nodeId] ?: return null
        node.refresh()
        val b = Rect().also { node.getBoundsInScreen(it) }
        return if (b.isEmpty) null else intArrayOf(b.centerX(), b.centerY())
    }

    fun setText(nodeId: String?, text: String): Boolean {
        val node = nodeId?.let { nodeMap[it] }
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        node.refresh()
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms.coerceIn(50, 3000)))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun global(action: String): Boolean {
        val a = when (action.uppercase()) {
            "BACK" -> GLOBAL_ACTION_BACK
            "HOME" -> GLOBAL_ACTION_HOME
            "RECENTS" -> GLOBAL_ACTION_RECENTS
            "NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
            "QUICK_SETTINGS" -> GLOBAL_ACTION_QUICK_SETTINGS
            "LOCK" -> GLOBAL_ACTION_LOCK_SCREEN
            "POWER_DIALOG" -> GLOBAL_ACTION_POWER_DIALOG
            "SCREENSHOT" -> GLOBAL_ACTION_TAKE_SCREENSHOT
            else -> return false
        }
        return performGlobalAction(a)
    }

    /** The package owning the active window — used to gate screen-driving on sensitive apps. "" if unknown. */
    fun foregroundPackage(): String =
        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: ""

    /** Visible text/description of a dumped node — used to gate taps on pay/confirm targets. "" if unknown. */
    fun nodeText(nodeId: String?): String {
        val n = nodeId?.let { nodeMap[it] } ?: return ""
        runCatching { n.refresh() }
        return (n.text ?: n.contentDescription)?.toString() ?: ""
    }

    /** Blocking screenshot → base64 PNG. */
    fun screenshotBase64(): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExec, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                var hb: HardwareBuffer? = null
                try {
                    hb = screenshot.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace)
                    if (bmp != null) {
                        val soft = bmp.copy(Bitmap.Config.ARGB_8888, false)
                        val bos = ByteArrayOutputStream()
                        soft.compress(Bitmap.CompressFormat.PNG, 90, bos)
                        result = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                        soft.recycle()
                    }
                } catch (_: Exception) {
                } finally {
                    hb?.close() // release the native buffer even if encode throws
                    latch.countDown()
                }
            }

            override fun onFailure(errorCode: Int) {
                latch.countDown()
            }
        })
        latch.await(8, TimeUnit.SECONDS)
        return result
    }
}
