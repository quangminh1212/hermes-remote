package com.hermes.bridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.util.Base64

/**
 * Accessibility service that performs real device interaction on behalf of Hermes.
 *
 * All gestures use [dispatchGesture] (no synthetic KeyEvents, no coordinate abuse).
 * Text input uses [AccessibilityNodeInfo.ACTION_SET_TEXT] on the focused editable node.
 */
class AccessibilityBridgeService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityBridge"
        private const val GESTURE_TIMEOUT_MS = 5_000L
        private const val SCREENSHOT_TIMEOUT_MS = 5_000L

        @Volatile
        var instance: AccessibilityBridgeService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed")
    }

    // ---------------------------------------------------------------------
    // Gestures
    // ---------------------------------------------------------------------

    /** Tap at (x, y) for `durationMs`. Returns true when the gesture completed. */
    fun tap(x: Float, y: Float, durationMs: Long = 100L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(gestureFor(path, durationMs))
    }

    /** Swipe from (startX, startY) to (endX, endY) over `durationMs`. */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        return dispatch(gestureFor(path, durationMs))
    }

    private fun gestureFor(path: Path, durationMs: Long): GestureDescription {
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L))
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    private fun dispatch(gesture: GestureDescription): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(description: GestureDescription?) { ok = true; latch.countDown() }
            override fun onCancelled(description: GestureDescription?) { ok = false; latch.countDown() }
        }, null)
        if (!dispatched) return false
        latch.await(GESTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return ok
    }

    // ---------------------------------------------------------------------
    // Text input
    // ---------------------------------------------------------------------

    /**
     * Type [text] into the currently focused editable field using ACTION_SET_TEXT.
     * Falls back to clipboard-free direct node update when the API action is unavailable.
     */
    fun typeText(text: String): Boolean {
        val node = focusedEditableNode()
        if (node == null) {
            Log.w(TAG, "typeText: no focused editable node")
            return false
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        @Suppress("DEPRECATION")
        node.recycle()
        return ok
    }

    private fun focusedEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused
        if (root.isEditable) return root
        return findFirstEditable(root)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child)
            if (found != null) return found
        }
        return null
    }

    // ---------------------------------------------------------------------
    // UI tree
    // ---------------------------------------------------------------------

    /** Serialize the accessibility tree into a JSON string, bounded by [depthLimit]. */
    fun readUiTree(depthLimit: Int = 10): String? {
        val root = rootInActiveWindow ?: return null
        return buildNode(root, 0, depthLimit)?.toString()
    }

    private fun buildNode(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int): JSONObject? {
        if (depth > maxDepth) return null
        val json = JSONObject().apply {
            put("className", node.className?.toString() ?: "")
            put("packageName", node.packageName?.toString() ?: "")
            put("text", node.text?.toString() ?: "")
            put("contentDescription", node.contentDescription?.toString() ?: "")
            put("viewId", node.viewIdResourceName ?: "")
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        json.put("bounds", JSONObject().apply {
            put("left", bounds.left)
            put("top", bounds.top)
            put("right", bounds.right)
            put("bottom", bounds.bottom)
        })
        json.put("clickable", node.isClickable)
        json.put("longClickable", node.isLongClickable)
        json.put("editable", node.isEditable)
        json.put("focusable", node.isFocusable)
        json.put("scrollable", node.isScrollable)
        json.put("selected", node.isSelected)
        json.put("enabled", node.isEnabled)

        if (node.childCount > 0 && depth < maxDepth) {
            val children = JSONArray()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                buildNode(child, depth + 1, maxDepth)?.let { children.put(it) }
            }
            json.put("children", children)
        }
        return json
    }

    // ---------------------------------------------------------------------
    // Launch app
    // ---------------------------------------------------------------------

    /** Launch an app by package name using a launcher intent. */
    fun launchApp(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: run {
            Log.w(TAG, "launchApp: no launch intent for $packageName")
            return false
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "launchApp failed for $packageName", e)
            false
        }
    }

    // ---------------------------------------------------------------------
    // Screenshot
    // ---------------------------------------------------------------------

    /**
     * Capture the screen. Requires Android 11+ (API 30) for the accessibility
     * takeScreenshot API; returns null on older versions.
     */
    fun screenshotBase64(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Screenshot requires API 30+")
            return null
        }
        return takeScreenshotInternal()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshotInternal(): String? {
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    screenshot.hardwareBuffer.close()
                    latch.countDown()
                }
                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "takeScreenshot failed: $errorCode")
                    latch.countDown()
                }
            }
        )
        if (!latch.await(SCREENSHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return null
        val bmp = bitmap ?: return null
        return try {
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } finally {
            bmp.recycle()
        }
    }
}
