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
                e.printStackTrace()
                // Fallback method
                try {
                    pressKeyAtCoordinates(x, y)
                } catch (fallbackEx: Exception) {
                    fallbackEx.printStackTrace()
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
                e.printStackTrace()
                
                // Final fallback: Use shell input method
                try {
                    sendShellInput(text)
                } catch (fallbackEx: Exception) {
                    fallbackEx.printStackTrace()
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
            e.printStackTrace()
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
            e.printStackTrace()
        }
    }
    
    /**
     * Perform swipe gesture
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Int = 300) {
        scope.launch {
            try {
                val gesture = android.view.MotionEvent.obtain(
                    0, 0, 0,
                    startX, startY,
                    0
                )
                
                gesture.setAction(android.view.MotionEvent.ACTION_DOWN)
                gesture.setLocation(startX, startY)
                
                sendGestureEvent(gesture)
                
                gesture.recycle()
                
                delay(durationMs.toLong())
                
                val gesture2 = android.view.MotionEvent.obtain(
                    0, 0, 0,
                    endX, endY,
                    0
                )
                
                gesture2.setAction(android.view.MotionEvent.ACTION_UP)
                gesture2.setLocation(endX, endY)
                
                sendGestureEvent(gesture2)
                
                gesture2.recycle()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Get screenshot of current screen
     */
    fun getScreenshot(): String? {
        return try {
            // Use MediaProjection API to capture screen
            // This requires runtime permission
            null // TODO: Implement proper screenshot capture
        } catch (e: Exception) {
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
                // Use am command via shell
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am start -n $packageName/.MainActivity"))
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Press hardware key
     */
    fun pressKey(keyCode: Int) {
        scope.launch {
            try {
                val KeyEventWrapper(KeyEvent.KEYCODE_UNKNOWN)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Send result back to server via WebSocket
     */
    fun sendCommandResponse(requestId: String, success: Boolean, result: Any?) {
        webSocketClient?.sendCommandComplete(requestId, success, result)
    }
}
