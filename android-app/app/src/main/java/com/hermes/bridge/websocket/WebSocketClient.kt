package com.hermes.bridge.websocket

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * WebSocket client for maintaining persistent connection to Hermes Agent relay server
 */
class WebSocketClient {
    
    companion object {
        private const val TAG = "WebSocketClient"
    }
    
    private var webSocket: WebSocket? = null
    private var isConnecting = false
    private var isConnected = false
    
    private val requestIds = ConcurrentHashMap<String, CompletableJob>()
    private val responseCallbacks = mutableMapOf<String, (JsonObject) -> Unit>()
    
    private val gson = Gson()
    private lateinit var baseUrl: String
    private lateinit var pairingCode: String
    
    private val dispatcher = Dispatchers.IO
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    fun connect(serverUrl: String, pairingCode: String) {
        this.baseUrl = serverUrl
        this.pairingCode = pairingCode
        
        scope.launch {
            if (isConnecting || isConnected) {
                Log.w(TAG, "Already connecting or connected, ignoring duplicate call")
                return@launch
            }
            
            try {
                isConnecting = true
                
                val request = Request.Builder()
                    .url(serverUrl)
                    .build()
                
                val client = OkHttpClient.Builder()
                    .pingInterval(java.time.Duration.ofSeconds(30))
                    .build()
                
                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i(TAG, "WebSocket connected successfully")
                        
                        // Authenticate immediately
                        authenticate(pairingCode)
                        
                        isConnected = true
                        isConnecting = false
                    }
                    
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d(TAG, "Received message: $text")
                        
                        try {
                            handleTextMessage(text)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling message", e)
                        }
                    }
                    
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        Log.d(TAG, "Received binary message")
                    }
                    
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "WebSocket closing: $code / $reason")
                        webSocket.close(1000, null)
                        isConnected = false
                    }
                    
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket error", t)
                        
                        isConnected = false
                        isConnecting = false
                        
                        // Attempt reconnection
                        scheduleReconnection()
                    }
                    
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "WebSocket closed: $code / $reason")
                        isConnected = false
                        isConnecting = false
                    }
                })
                
                Log.i(TAG, "WebSocket connection initiated")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create WebSocket", e)
                isConnecting = false
            }
        }
    }
    
    private fun authenticate(pairingCode: String) {
        val authRequest = JsonObject().apply {
            addProperty("type", "auth.request")
            addProperty("pairing_code", pairingCode)
        }
        
        send(authRequest.toString())
    }
    
    private fun handleTextMessage(jsonString: String) {
        val json = gson.fromJson(jsonString, JsonObject::class.java)
        val messageType = json.get("type")?.asString ?: "unknown"
        
        when (messageType) {
            "auth.grant" -> {
                handleAuthGrant(json)
            }
            
            "auth.reject" -> {
                Log.e(TAG, "Authentication rejected: ${json.get("reason")}")
            }
            
            "command.execute" -> {
                handleCommandExecute(json)
            }
            
            else -> {
                Log.w(TAG, "Unknown message type: $messageType")
            }
        }
    }
    
    private fun handleAuthGrant(json: JsonObject) {
        Log.i(TAG, "Authentication successful!")
        
        val sessionToken = json.get("session_token")?.asString
        val permissions = json.get("permissions")?.asJsonArray?.map { it.asString } ?: emptyList()
        
        Log.d(TAG, "Session token: $sessionToken")
        Log.d(TAG, "Permissions granted: $permissions")
    }
    
    private fun handleCommandExecute(json: JsonObject) {
        val requestId = json.get("request_id")?.asString ?: run {
            Log.e(TAG, "Missing request_id in command")
            return
        }
        
        val action = json.get("action")?.asString ?: run {
            Log.e(TAG, "Missing action in command")
            return
        }
        
        val params = json.get("params")?.asJsonObject ?: JsonObject()
        val timeoutMs = json.get("timeout_ms")?.asInt ?: 5000
        
        Log.i(TAG, "Command received: $action (id: $requestId)")
        
        // Schedule command execution with timeout
        scope.launch {
            try {
                val result = executeCommand(action, params)
                
                completeCommand(requestId, true, result)
            } catch (e: Exception) {
                Log.e(TAG, "Command execution failed: $action", e)
                failCommand(requestId, e.message ?: "Unknown error")
            }
        }
        
        // Set timeout
        scope.launch {
            delay(timeoutMs.toLong())
            failCommand(requestId, "Command timeout exceeded")
        }
    }
    
    private suspend fun executeCommand(action: String, params: JsonObject): Any {
        return when (action) {
            "tap" -> {
                val x = params.get("x")?.asDouble ?: 0.0
                val y = params.get("y")?.asDouble ?: 0.0
                val durationMs = params.get("duration_ms")?.asInt ?: 100
                
                AccessibilityBridgeService.instance?.executeTap(x.toFloat(), y.toFloat(), durationMs)
                mapOf("success" to true, "coordinates" to "$x,$y")
            }
            
            "type_text" -> {
                val text = params.get("text")?.asString ?: ""
                
                AccessibilityBridgeService.instance?.typeText(text)
                mapOf("success" to true, "text_length" to text.length)
            }
            
            "swipe" -> {
                val startX = params.get("start_x")?.asDouble ?: 0.0
                val startY = params.get("start_y")?.asDouble ?: 0.0
                val endX = params.get("end_x")?.asDouble ?: 0.0
                val endY = params.get("end_y")?.asDouble ?: 0.0
                val durationMs = params.get("duration_ms")?.asInt ?: 300
                
                AccessibilityBridgeService.instance?.performSwipe(
                    startX.toFloat(), startY.toFloat(),
                    endX.toFloat(), endY.toFloat(),
                    durationMs
                )
                mapOf("success" to true, "gesture" to "swipe")
            }
            
            "get_screenshot" -> {
                val screenshot = AccessibilityBridgeService.instance?.getScreenshot()
                mapOf("success" to screenshot != null, "screenshot_base64" to screenshot)
            }
            
            "read_ui_tree" -> {
                val depthLimit = params.get("depth_limit")?.asInt ?: 10
                
                val uiTree = AccessibilityBridgeService.instance?.readUiTree(depthLimit)
                mapOf("success" to uiTree != null, "ui_tree_json" to uiTree?.toString())
            }
            
            "launch_app" -> {
                val packageName = params.get("package_name")?.asString ?: ""
                
                AccessibilityBridgeService.instance?.launchApp(packageName)
                mapOf("success" to true, "package" to packageName)
            }
            
            else -> {
                throw IllegalArgumentException("Unknown action: $action")
            }
        }
    }
    
    private fun completeCommand(requestId: String, success: Boolean, result: Any) {
        scope.launch {
            val response = JsonObject().apply {
                addProperty("type", "command.complete")
                addProperty("request_id", requestId)
                addProperty("success", success)
                add("result", gson.toJsonTree(result))
            }
            
            send(response.toString())
        }
    }
    
    private fun failCommand(requestId: String, error: String) {
        scope.launch {
            val response = JsonObject().apply {
                addProperty("type", "command.error")
                addProperty("request_id", requestId)
                addProperty("error", error)
            }
            
            send(response.toString())
        }
    }
    
    fun sendCommandComplete(requestId: String, success: Boolean, result: Any?) {
        completeCommand(requestId, success, result)
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        isConnected = false
        scope.cancel()
    }
    
    private fun send(message: String) {
        scope.launch {
            webSocket?.send(message)
        }
    }
    
    private fun scheduleReconnection() {
        scope.launch {
            Log.i(TAG, "Scheduling reconnection in 5 seconds...")
            delay(5000)
            
            if (!isConnected) {
                connect(baseUrl, pairingCode)
            }
        }
    }
}
