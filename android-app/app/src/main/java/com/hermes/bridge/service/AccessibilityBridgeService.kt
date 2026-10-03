package com.hermes.bridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.hermes.bridge.websocket.WebSocketClient
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.*

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
     * Execute tap at coordinates
     */
    fun executeTap(x: Float, y: Float, durationMs: Int = 100) {
        scope.launch {
            try {
                performGlobalAction(GLOBAL_ACTION_NONE)
                
                val gesture = android.view.MotionEvent.obtain(
                    0, 0, 0,
                    x, y,
                    0
                )
                
                // Send touch event
                sendGestureEvent(gesture)
                
                gesture.recycle()
                
                delay(durationMs.toLong())
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Type text into active input field
     */
    fun typeText(text: String) {
        scope.launch {
            try {
                // Insert text using clipboard method
                Runtime.getRuntime().exec("input keyevent CODE_INSERT")
                
                // Alternative: Use UiAutomation if available
                uiAutomation?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
