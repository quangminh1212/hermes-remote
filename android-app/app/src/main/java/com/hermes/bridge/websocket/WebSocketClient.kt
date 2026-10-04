package com.hermes.bridge.websocket

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hermes.bridge.protocol.CommandRequest
import com.hermes.bridge.protocol.CommandResult
import com.hermes.bridge.protocol.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Maintains a resilient WebSocket connection to the Hermes relay server.
 *
 * - Authenticates with a pairing code.
 * - Dispatches inbound `command.execute` frames to [commandHandler] off the reader thread.
 * - Reconnects with exponential backoff.
 */
class WebSocketClient(
    private val serverUrl: String,
    private val pairingCode: String,
    private val commandHandler: suspend (CommandRequest) -> CommandResult,
    private val onStateChanged: (ConnectionState) -> Unit,
) {
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private companion object {
        const val TAG = "WebSocketClient"
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val PING_INTERVAL_S = 20L
    }

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val outbound = Channel<String>(Channel.UNLIMITED)

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_S, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming
        .build()

    @Volatile private var webSocket: WebSocket? = null

    fun connect() {
        if (!running.compareAndSet(false, true)) return
        scope.launch { runLoop() }
    }

    fun disconnect() {
        running.set(false)
        webSocket?.close(1000, "client closing")
        webSocket = null
        scope.cancel()
        onStateChanged(ConnectionState.DISCONNECTED)
    }

    fun send(message: String): Boolean = outbound.trySend(message).isSuccess

    private suspend fun runLoop() {
        var attempt = 0
        while (running.get()) {
            onStateChanged(ConnectionState.CONNECTING)
            val connected = openSocket()
            if (!connected) {
                attempt++
                onStateChanged(ConnectionState.ERROR)
                delay(backoffFor(attempt))
                continue
            }
            attempt = 0
            drainOutbound()
            if (running.get()) delay(1_000) // brief pause before reconnect
        }
        onStateChanged(ConnectionState.DISCONNECTED)
    }

    private suspend fun openSocket(): Boolean {
        val terminated = Channel<Boolean>(1)
        return try {
            val request = Request.Builder().url(serverUrl).build()
            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket opened")
                    onStateChanged(ConnectionState.CONNECTED)
                    sendAuth()
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleIncoming(text)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}")
                    terminated.trySend(false)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closed: $code $reason")
                    terminated.trySend(true)
                }
            })
            terminated.receive()
            true
        } catch (e: Exception) {
            Log.e(TAG, "openSocket failed", e)
            false
        }
    }

    private fun sendAuth() {
        val payload = JsonObject().apply {
            addProperty("type", MessageType.AUTH_REQUEST)
            addProperty("pairing_code", pairingCode)
        }
        send(gson.toJson(payload))
    }

    private suspend fun drainOutbound() {
        while (running.get()) {
            val message = outbound.tryReceive().getOrNull() ?: break
            webSocket?.send(message)
        }
    }

    private fun handleIncoming(text: String) {
        val root = try {
            JsonParser.parseString(text).asJsonObject
        } catch (e: Exception) {
            Log.w(TAG, "Malformed frame: $text")
            return
        }
        when (root.get("type")?.asString) {
            MessageType.AUTH_GRANT -> Log.i(TAG, "Authenticated with server")
            MessageType.AUTH_REJECT -> {
                Log.e(TAG, "Auth rejected: ${root.get("reason")?.asString}")
                onStateChanged(ConnectionState.ERROR)
            }
            MessageType.COMMAND_EXECUTE -> {
                val request = parseCommand(root) ?: return
                scope.launch {
                    val result = withContext(Dispatchers.Default) { commandHandler(request) }
                    sendResult(request.requestId, result)
                }
            }
            else -> Log.d(TAG, "Ignoring frame type ${root.get("type")}")
        }
    }

    private fun parseCommand(root: JsonObject): CommandRequest? {
        return try {
            val action = root.get("action")?.asString ?: return null
            CommandRequest(
                requestId = root.get("request_id")?.asString ?: "",
                action = action,
                params = root.getAsJsonObject("params") ?: JsonObject(),
                timeoutMs = root.get("timeout_ms")?.asLong ?: 30_000L,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Bad command frame", e)
            null
        }
    }

    private fun sendResult(requestId: String, result: CommandResult) {
        val payload = JsonObject().apply {
            addProperty("type", if (result.success) MessageType.COMMAND_COMPLETE else MessageType.COMMAND_ERROR)
            addProperty("request_id", requestId)
            addProperty("success", result.success)
            result.error?.let { addProperty("error", it) }
            add("result", gson.toJsonTree(result.payload))
        }
        send(gson.toJson(payload))
    }

    private fun backoffFor(attempt: Int): Long {
        val exp = BASE_BACKOFF_MS shl (attempt - 1).coerceIn(0, 5)
        return exp.coerceAtMost(MAX_BACKOFF_MS)
    }
}
