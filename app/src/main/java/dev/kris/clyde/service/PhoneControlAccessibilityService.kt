package dev.kris.clyde.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
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

    private val nodeMap = HashMap<String, AccessibilityNodeInfo>()

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
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
                        .put("text", n.text?.toString() ?: "")
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

    fun tapNode(nodeId: String): Boolean =
        nodeMap[nodeId]?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false

    fun setText(nodeId: String?, text: String): Boolean {
        val node = nodeId?.let { nodeMap[it] }
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
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

    /** Blocking screenshot → base64 PNG. */
    fun screenshotBase64(): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        val exec = Executors.newSingleThreadExecutor()
        takeScreenshot(Display.DEFAULT_DISPLAY, exec, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                try {
                    val hb = screenshot.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace)
                    if (bmp != null) {
                        val soft = bmp.copy(Bitmap.Config.ARGB_8888, false)
                        val bos = ByteArrayOutputStream()
                        soft.compress(Bitmap.CompressFormat.PNG, 90, bos)
                        result = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                        soft.recycle()
                    }
                    hb.close()
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }

            override fun onFailure(errorCode: Int) {
                latch.countDown()
            }
        })
        latch.await(8, TimeUnit.SECONDS)
        exec.shutdown()
        return result
    }
}
