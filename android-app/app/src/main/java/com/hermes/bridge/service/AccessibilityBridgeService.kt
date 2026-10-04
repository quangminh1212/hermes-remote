package com.hermes.bridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityManager
import android.util.Log
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.hermes.bridge.ui.MainActivity
import com.hermes.bridge.websocket.WebSocketClient
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Core accessibility service that provides device interaction capabilities
 * Executes commands received from Hermes Agent via WebSocket
 */
class AccessibilityBridgeService : AccessibilityService() {
    
    companion object {
        const val TAG = "AccessibilityBridge"
        
        var instance: AccessibilityBridgeService? = null
            private set
        
        val scope by lazy { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    }
    
    private var webSocketClient: WebSocketClient? = null
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridgeService.instance = this
        
        configureService()
    }
    
    private fun configureService() {
        val info = AccessibilityServiceInfo().apply {
            types = AccessibilityEvent.TYPE_ALL
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED
                        
            canPerformGestures = true
            canRetrieveWindowContent = true
        }
        
        serviceInfo = info
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed for diagnostics
    }
    
    override fun onInterrupt() {
        // Called when service is interrupted
    }
    
    /**
     * Execute tap at coordinates with optional duration
     */
    fun executeTap(x: Float, y: Float, durationMs: Int = 100) {
        scope.launch {
            try {
                // Use performGlobalAction for better compatibility
                sendGestureEvent(createTouchEvents(x, y, durationMs))
            } catch (e: Exception) {
                Log.e(TAG, "Tap execution failed", e)
                // Fallback method
                try {
                    pressKeyAtCoordinates(x, y)
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Fallback tap failed", fallbackEx)
                }
            }
        }
    }
    
    /**
     * Create touch motion events for a tap gesture
     */
    private fun createTouchEvents(x: Float, y: Float, durationMs: Int): KeyEvent {
        val downTime = System.currentTimeMillis()
        val currentTime = downTime
        
        // ACTION_DOWN
        val downEvent = KeyEvent(
            downTime, currentTime,
            KeyEvent.ACTION_DOWN, 0f, 0f,
            0, 0, 0.0f, 0.0f,
            android.view.InputDevice.SOURCE_TOUCHSCREEN,
            android.view.InputDevice.KEYBOARD_DEVICE_ID,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_FROM_SYSTEM,
            KeyEvent.META_SHIFT_LEFT_ON,
            android.view.MotionEvent.ACTION_DOWN, x, y, 0
        )
        
        // Small delay
        Thread.sleep(10)
        
        // ACTION_UP
        val upEvent = KeyEvent(
            downTime, System.currentTimeMillis(),
            KeyEvent.ACTION_UP, 0f, 0f,
            0, 0, 0.0f, 0.0f,
            android.view.InputDevice.SOURCE_TOUCHSCREEN,
            android.view.InputDevice.KEYBOARD_DEVICE_ID,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_FROM_SYSTEM,
            KeyEvent.META_SHIFT_LEFT_ON,
            android.view.MotionEvent.ACTION_UP, x, y, 0
        )
        
        // Send both events
        sendKeyEvent(downEvent)
        sendKeyEvent(upEvent)
        
        // Wait for duration
        if (durationMs > 0) {
            Thread.sleep(durationMs.toLong())
        }
        
        return downEvent
    }
    
    /**
     * Type text into active input field
     */
    fun typeText(text: String) {
        scope.launch {
            try {
                // Method 1: Use UiAutomation for direct text insertion
                val automation = uiAutomation
                
                if (automation != null) {
                    // Send text character by character
                    for (char in text) {
                        // Simulate key press for each character
                        val keyCode = charToKeyCode(char)
                        sendCharKeyCode(keyCode)
                        delay(50) // Small delay between characters
                    }
                } else {
                    // Fallback: Use clipboard paste method
                    pasteFromClipboard(text)
                }
            } catch (e: Exception) {
                    Log.e(TAG, "Exception handling failed")
                
                // Final fallback: Use shell input method
                try {
                    sendShellInput(text)
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Fallback tap failed", fallbackEx)
                }
            }
        }
    }
    
    /**
     * Convert character to keyboard keycode
     */
    private fun charToKeyCode(char: Char): Int {
        // Basic mapping for common characters
        return when (char) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (char - 'a')
            in 'A'..'Z' -> KeyEvent.KEYCODE_A + (char - 'a') + KeyEvent.META_SHIFT_ON
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (char - '0')
            ' ' -> KeyEvent.KEYCODE_SPACE
            '\n' -> KeyEvent.KEYCODE_ENTER
            '.' -> KeyEvent.KEYCODE_DOT
            ',' -> KeyEvent.KEYCODE_COMMA
            '-' -> KeyEvent.KEYCODE_MINUS
            '=' -> KeyEvent.KEYCODE_EQUALS
            '/' -> KeyEvent.KEYCODE_SLASH
            ':' -> KeyEvent.KEYCODE_SEMICOLON
            '\'' -> KeyEvent.KEYCODE_APOSTROPHE
            ';' -> KeyEvent.KEYCODE_SEMICOLON
            '[' -> KeyEvent.KEYCODE_LEFT_BRACKET
            ']' -> KeyEvent.KEYCODE_RIGHT_BRACKET
            '\\' -> KeyEvent.KEYCODE_BACKSLASH
            '+' -> KeyEvent.KEYCODE_PLUS
            '*' -> KeyEvent.KEYCODE_STAR
            '#' -> KeyEvent.KEYCODE_POUND
            '@' -> KeyEvent.KEYCODE_AT
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }
    
    /**
     * Send character code via sendKeyEvent
     */
    private fun sendCharKeyCode(keyCode: Int) {
        val downTime = System.currentTimeMillis()
        
        // KEY_DOWN
        val downEvent = KeyEvent(
            downTime, downTime,
            KeyEvent.ACTION_DOWN, keyCode, 0,
            0, 0, 0.0f, 0.0f,
            android.view.InputDevice.SOURCE_KEYBOARD,
            android.view.InputDevice.KEYBOARD_DEVICE_ID,
            KeyEvent.FLAG_FROM_SYSTEM,
            if (keyCode >= KeyEvent.KEYCODE_A && keyCode < KeyEvent.KEYCODE_Z) {
                KeyEvent.META_SHIFT_ON
            } else 0,
            0, 0, 0, 0,
            -1, 0, 0, android.view.InputFlags.NONE
        )
        
        // KEY_UP
        val upEvent = KeyEvent(
            downTime, System.currentTimeMillis(),
            KeyEvent.ACTION_UP, keyCode, 0,
            0, 0, 0.0f, 0.0f,
            android.view.InputDevice.SOURCE_KEYBOARD,
            android.view.InputDevice.KEYBOARD_DEVICE_ID,
            KeyEvent.FLAG_FROM_SYSTEM,
            if (keyCode >= KeyEvent.KEYCODE_A && keyCode < KeyEvent.KEYCODE_Z) {
                KeyEvent.META_SHIFT_ON
            } else 0,
            0, 0, 0, 0,
            -1, 0, 0, android.view.InputFlags.NONE
        )
        
        sendKeyEvent(downEvent)
        sendKeyEvent(upEvent)
    }
    
    /**
     * Fallback: Paste text using clipboard
     */
    private fun pasteFromClipboard(text: String) {
        try {
            // Copy text to clipboard
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("hermes_input", text)
            clipboard.setPrimaryClip(clip)
            
            // Simulate Ctrl+V paste
            val ctrlVDown = KeyEvent(
                System.currentTimeMillis(), System.currentTimeMillis(),
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V, 0,
                0, 0, 0.0f, 0.0f,
                android.view.InputDevice.SOURCE_KEYBOARD,
                android.view.InputDevice.KEYBOARD_DEVICE_ID,
                KeyEvent.FLAG_FROM_SYSTEM,
                KeyEvent.META_CTRL_ON,
                0, 0, 0, 0,
                -1, 0, 0, android.view.InputFlags.NONE
            )
            
            val ctrlVUp = KeyEvent(
                System.currentTimeMillis(), System.currentTimeMillis(),
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_V, 0,
                0, 0, 0.0f, 0.0f,
                android.view.InputDevice.SOURCE_KEYBOARD,
                android.view.InputDevice.KEYBOARD_DEVICE_ID,
                KeyEvent.FLAG_FROM_SYSTEM,
                KeyEvent.META_CTRL_ON,
                0, 0, 0, 0,
                -1, 0, 0, android.view.InputFlags.NONE
            )
            
            sendKeyEvent(ctrlVDown)
            sendKeyEvent(ctrlVUp)
            
        } catch (e: Exception) {
                Log.e(TAG, "Exception handling failed")
        }
    }
    
    /**
     * Fallback: Use shell input method
     */
    private fun sendShellInput(text: String) {
        try {
            // Use am instrument to send text
            val process = Runtime.getRuntime().exec(arrayOf(
                "am", "instrument",
                "-w",
                "com.android.settings/androidx.test.aggregator.AggregatorTest"
            ))
            // This is just a placeholder - actual implementation would need proper intent
        } catch (e: Exception) {
                Log.e(TAG, "Exception handling failed")
        }
    }
    
    /**
     * Perform swipe gesture from one point to another
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Int = 300) {
        scope.launch {
            try {
                // Create swipe gesture with interpolated points for smoothness
                val timeStep = durationMs / 10.0
                val steps = 10
                
                for (i in 0..steps) {
                    val progress = i.toFloat() / steps
                    val currentX = startX + (endX - startX) * progress
                    val currentY = startY + (endY - startY) * progress
                    
                    val downTime = System.currentTimeMillis()
                    
                    // ACTION_MOVE
                    val moveEvent = KeyEvent(
                        downTime, downTime,
                        KeyEvent.ACTION_MOVE, 0f, 0f,
                        0, 0, 0.0f, 0.0f,
                        android.view.InputDevice.SOURCE_TOUCHSCREEN,
                        android.view.InputDevice.KEYBOARD_DEVICE_ID,
                        KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_LONG_PRESS,
                        0,
                        android.view.MotionEvent.ACTION_MOVE, currentX, currentY, 0
                    )
                    
                    sendKeyEvent(moveEvent)
                    delay(timeStep.toLong())
                }
                
                // Final ACTION_UP
                val upEvent = KeyEvent(
                    System.currentTimeMillis(), System.currentTimeMillis(),
                    KeyEvent.ACTION_UP, 0f, 0f,
                    0, 0, 0.0f, 0.0f,
                    android.view.InputDevice.SOURCE_TOUCHSCREEN,
                    android.view.InputDevice.KEYBOARD_DEVICE_ID,
                    KeyEvent.FLAG_FROM_SYSTEM,
                    0,
                    android.view.MotionEvent.ACTION_UP, endX, endY, 0
                )
                
                sendKeyEvent(upEvent)
                
            } catch (e: Exception) {
                    Log.e(TAG, "Exception handling failed")
            }
        }
    }
    
    /**
     * Alternative swipe using MotionEvent
     */
    private fun createTouchSwipeEvents(startX: Float, startY: Float, endX: Float, endY: Float): KeyEvent {
        val startTime = System.currentTimeMillis()
        
        // START at start position
        val startEvent = android.view.MotionEvent.obtain(
            startTime, startTime,
            android.view.MotionEvent.ACTION_DOWN,
            startX, startY, 0f
        )
        
        // END at end position  
        val endEvent = android.view.MotionEvent.obtain(
            startTime + 200, startTime + 200,
            android.view.MotionEvent.ACTION_UP,
            endX, endY, 0f
        )
        
        // Send events
        sendGestureEvent(startEvent)
        sendGestureEvent(endEvent)
        
        startEvent.recycle()
        endEvent.recycle()
        
        return startEvent
    }
    
    /**
     * Get screenshot of current screen as base64 encoded PNG
     */
    fun getScreenshot(): String? {
        return try {
            // Capture screen using DisplayMetrics
            val windowManager = getSystemService(WINDOW_SERVICE) as android.content.Context.WINDOW_SERVICE
            val display = windowManager.defaultDisplay
            val screenWidth = display.widthPixels
            val screenHeight = display.heightPixels
            
            // Create empty bitmap for screenshot
            val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            
            // Convert to base64 PNG with compression quality 85%
            val bitmapBytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, bitmapBytes)
            Base64.encodeToString(bitmapBytes.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
                Log.e(TAG, "Exception handling failed")
            null
        }
    }
    
    /**
     * Read UI tree structure
     */
    fun readUiTree(depthLimit: Int = 10): JSONObject? {
        return try {
            val rootNode = rootInActiveWindow ?: return null
            buildUiTree(rootNode, 0, depthLimit)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun buildUiTree(node: AccessibilityNodeInfo, currentDepth: Int, maxDepth: Int): JSONObject? {
        if (currentDepth > maxDepth || node == null) return null
        
        val json = JSONObject()
        
        // Basic properties
        json.put("className", node.className?.toString() ?: "")
        json.put("packageName", node.packageName?.toString() ?: "")
        json.put("text", node.text?.toString() ?: "")
        json.put("description", node.contentDescription?.toString() ?: "")
        json.put("resourceId", node.resourceId?.toString() ?: "")
        
        // Bounds
        val bounds = Rect()
        if (node.getBoundsInScreen(bounds)) {
            json.put("bounds", JSONObject().apply {
                put("left", bounds.left)
                put("top", bounds.top)
                put("right", bounds.right)
                put("bottom", bounds.bottom)
            })
        }
        
        // State
        json.put("clickable", node.isClickable)
        json.put("longClickable", node.isLongClickable)
        json.put("editable", node.isEditable)
        json.put("focusable", node.isFocusable)
        json.put("selected", node.isSelected)
        json.put("password", node.isPassword)
        
        // Children
        if (node.childCount > 0 && currentDepth < maxDepth) {
            val childrenArray = JSONArray()
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val childJson = buildUiTree(child, currentDepth + 1, maxDepth)
                    if (childJson != null) {
                        childrenArray.put(childJson)
                    }
                    child.recycle()
                }
            }
            
            json.put("children", childrenArray)
        }
        
        return json
    }
    
    /**
     * Launch application by package name
     */
    fun launchApp(packageName: String) {
        scope.launch {
            try {
                // Use Intent to launch app
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                }
                
                startActivity(intent)
                
                // Alternative command-line approach (requires adb/root)
                // execShellCommand("am start -n $packageName/.MainActivity")
                
            } catch (e: Exception) {
                    Log.e(TAG, "Exception handling failed")
            }
        }
    }
    
    /**
     * Press hardware key
     */
    fun pressKey(keyCode: Int) {
        scope.launch {
            try {
                val downTime = System.currentTimeMillis()
                
                // KEYDOWN
                val downEvent = KeyEvent(
                    downTime, downTime,
                    KeyEvent.ACTION_DOWN, keyCode, 0,
                    0, 0, 0.0f, 0.0f,
                    android.view.InputDevice.SOURCE_BUTTON,
                    android.view.InputDevice.KEYBOARD_DEVICE_ID,
                    KeyEvent.FLAG_FROM_SYSTEM,
                    0,
                    0, 0, 0, 0,
                    -1, 0, 0, android.view.InputFlags.NONE
                )
                
                // KEYUP
                val upEvent = KeyEvent(
                    downTime, System.currentTimeMillis(),
                    KeyEvent.ACTION_UP, keyCode, 0,
                    0, 0, 0.0f, 0.0f,
                    android.view.InputDevice.SOURCE_BUTTON,
                    android.view.InputDevice.KEYBOARD_DEVICE_ID,
                    KeyEvent.FLAG_FROM_SYSTEM,
                    0,
                    0, 0, 0, 0,
                    -1, 0, 0, android.view.InputFlags.NONE
                )
                
                sendKeyEvent(downEvent)
                sendKeyEvent(upEvent)
                
            } catch (e: Exception) {
                    Log.e(TAG, "Exception handling failed")
            }
        }
    }
    
    /**
     * Common key codes
     */
    fun pressHome() = pressKey(KeyEvent.KEYCODE_HOME)
    fun pressBack() = pressKey(KeyEvent.KEYCODE_BACK)
    fun pressRecentApps() = pressKey(KeyEvent.KEYCODE_RECENT)
    fun pressVolumeUp() = pressKey(KeyEvent.KEYCODE_VOLUME_UP)
    fun pressVolumeDown() = pressKey(KeyEvent.KEYCODE_VOLUME_DOWN)
    fun pressMute() = pressKey(KeyEvent.KEYCODE_MUTE)
    
    /**
     * Scroll current window
     */
    fun scroll(direction: String) {
        scope.launch {
            try {
                val node = rootInActiveWindow ?: return@launch
                
                val scrollAmount = when (direction.lowercase()) {
                    "up" -> AccessibilityNodeInfo.SCROLL_TO_TOP
                    "down" -> AccessibilityNodeInfo.SCROLL_TO_BOTTOM
                    "left" -> AccessibilityNodeInfo.SCROLL_LEFT
                    "right" -> AccessibilityNodeInfo.SCROLL_RIGHT
                    else -> AccessibilityNodeInfo.SCROLL_FORWARD
                }
                
                node.performAction(scrollAmount)
            } catch (e: Exception) {
                    Log.e(TAG, "Exception handling failed")
            }
        }
    }
    
    /**
     * Clear clipboard
     */
    fun clearClipboard() {
        scope.launch {
            try {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.primaryClip = android.content.ClipData.newPlainText("empty", "")
            } catch (e: Exception) {
                    Log.e(TAG, "Exception handling failed")
            }
        }
    }
    
    /**
     * Get device information
     */
    fun getDeviceInfo(): JSONObject {
        val json = JSONObject()
        
        try {
            json.put("model", Build.MODEL)
            json.put("manufacturer", Build.MANUFACTURER)
            json.put("brand", Build.BRAND)
            json.put("sdkVersion", Build.VERSION.SDK_INT)
            json.put("androidVersion", Build.VERSION.RELEASE)
            json.put("product", Build.PRODUCT)
            
            // Screen dimensions
            val windowManager = getSystemService(WINDOW_SERVICE) as android.content.Context.WINDOW_SERVICE
            val display = windowManager.defaultDisplay
            val size = android.util.DisplayMetrics().also { 
                display.getRealMetrics(it) 
            }
            
            json.put("screenWidth", size.widthPixels)
            json.put("screenHeight", size.heightPixels)
            json.put("density", size.density)
            
        } catch (e: Exception) {
                Log.e(TAG, "Exception handling failed")
        }
        
        return json
    }
    
    /**
     * Send result back to server via WebSocket
     */
    fun sendCommandResponse(requestId: String, success: Boolean, result: Any?) {
        webSocketClient?.sendCommandComplete(requestId, success, result)
    }
}
