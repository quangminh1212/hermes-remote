package com.hermes.bridge.websocket

import com.hermes.bridge.protocol.CommandRequest
import com.hermes.bridge.protocol.CommandResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide singleton that owns the single [WebSocketClient] instance and
 * exposes its state as an observable flow for the UI layer.
 */
object ConnectionManager {

    enum class State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var client: WebSocketClient? = null

    fun connect(
        serverUrl: String,
        pairingCode: String,
        commandHandler: suspend (CommandRequest) -> CommandResult,
    ) {
        disconnect()
        _state.value = State.CONNECTING
        val ws = WebSocketClient(
            serverUrl = serverUrl,
            pairingCode = pairingCode,
            commandHandler = commandHandler,
            onStateChanged = { wsState -> _state.value = mapState(wsState) },
        )
        client = ws
        ws.connect()
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _state.value = State.DISCONNECTED
    }

    private fun mapState(s: WebSocketClient.ConnectionState): State = when (s) {
        WebSocketClient.ConnectionState.DISCONNECTED -> State.DISCONNECTED
        WebSocketClient.ConnectionState.CONNECTING -> State.CONNECTING
        WebSocketClient.ConnectionState.CONNECTED -> State.CONNECTED
        WebSocketClient.ConnectionState.ERROR -> State.ERROR
    }
}
