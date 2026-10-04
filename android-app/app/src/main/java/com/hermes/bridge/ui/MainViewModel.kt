package com.hermes.bridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.bridge.data.ChatApi
import com.hermes.bridge.data.ConfigStore
import com.hermes.bridge.data.StreamEvent
import com.hermes.bridge.model.ChatMessage
import com.hermes.bridge.model.DeliveryState
import com.hermes.bridge.model.Role
import com.hermes.bridge.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** UI state for the chat screen. */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val config: ServerConfig = ServerConfig.DEFAULT,
    val connectionHint: String? = null,
)

/** Drives the chat: holds transcript, config, and streams replies. */
class MainViewModel(
    private val configStore: ConfigStore,
    private val api: ChatApi = ChatApi(),
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState(config = configStore.load()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** Quick-action prompts shown as chips: label to prompt. */
    val quickActions: List<Pair<String, String>> = listOf(
        "Tình trạng máy" to
            "Kiểm tra nhanh tình trạng máy tính: CPU, RAM và dung lượng đĩa còn trống.",
        "Danh sách file" to "Liệt kê các file trong thư mục hiện tại của bạn.",
        "Chạy test" to "Chạy test suite của dự án hiện tại và tóm tắt kết quả.",
        "Git status" to "Cho tôi biết trạng thái git hiện tại của dự án đang làm việc.",
        "Tắt máy" to "Hỏi tôi xác nhận rồi tắt máy tính sau 60 giây.",
    )

    fun onInputChange(value: String) {
        _state.value = _state.value.copy(input = value)
    }

    fun saveConfig(config: ServerConfig) {
        configStore.save(config)
        _state.value = _state.value.copy(config = config, connectionHint = null)
    }

    /**
     * Apply a payload scanned from the `hermes-remote` QR: persist it as the
     * server config and immediately probe the connection so the user sees a
     * result without touching the settings form.
     */
    fun applyConnectPayload(payload: com.hermes.bridge.model.ConnectPayload) {
        val config = payload.toServerConfig()
        configStore.save(config)
        _state.value = _state.value.copy(config = config, connectionHint = null)
        testConnection(config)
    }

    /** Probe the server by listing models and set a human-readable hint. */
    fun testConnection(config: ServerConfig) {
        viewModelScope.launch {
            if (!config.isValid()) {
                _state.value = _state.value.copy(connectionHint = "Thiếu URL hoặc API key")
                return@launch
            }
            val models = withContext(Dispatchers.IO) { api.listModels(config) }
            val hint = when {
                models.isNotEmpty() ->
                    "Kết nối OK — ${models.size} model: ${models.take(3).joinToString()}"
                else -> "Không kết nối được (kiểm tra URL, key, server đang chạy)"
            }
            _state.value = _state.value.copy(connectionHint = hint)
        }
    }

    fun send(promptOverride: String? = null) {
        val current = _state.value
        if (current.isStreaming) return
        val text = (promptOverride ?: current.input).trim()
        if (text.isEmpty()) return

        val userMsg = ChatMessage(id = newId(), role = Role.USER, content = text)
        val placeholder = ChatMessage(
            id = newId(),
            role = Role.ASSISTANT,
            content = "",
            state = DeliveryState.STREAMING,
        )
        val history = current.messages + userMsg
        _state.value = current.copy(
            messages = history + placeholder,
            input = "",
            isStreaming = true,
            connectionHint = null,
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                api.streamChat(current.config, history) { event ->
                    when (event) {
                        is StreamEvent.Delta -> appendDelta(placeholder.id, event.text)
                        is StreamEvent.Completed -> Unit
                        is StreamEvent.Failed -> failStream(placeholder.id, event.message)
                    }
                }
            }
            finishStream(placeholder.id, result)
        }
    }

    private fun appendDelta(id: String, delta: String) {
        _state.value = _state.value.copy(
            messages = _state.value.messages.map { msg ->
                if (msg.id == id) msg.copy(content = msg.content + delta) else msg
            },
        )
    }

    private fun failStream(id: String, message: String) {
        _state.value = _state.value.copy(
            isStreaming = false,
            messages = _state.value.messages.map { msg ->
                if (msg.id == id) {
                    msg.copy(
                        state = DeliveryState.ERROR,
                        error = message,
                        content = msg.content.ifBlank { message },
                    )
                } else {
                    msg
                }
            },
        )
    }

    private fun finishStream(id: String, result: String) {
        val alreadyFailed = _state.value.messages
            .firstOrNull { it.id == id }
            ?.state == DeliveryState.ERROR
        if (alreadyFailed) return
        _state.value = _state.value.copy(
            isStreaming = false,
            messages = _state.value.messages.map { msg ->
                if (msg.id == id) {
                    val blank = result.isBlank()
                    msg.copy(
                        state = if (blank) DeliveryState.ERROR else DeliveryState.DONE,
                        content = result.ifBlank { "Không có phản hồi." },
                        error = if (blank) "Không có phản hồi từ server" else null,
                    )
                } else {
                    msg
                }
            },
        )
    }

    fun clearChat() {
        _state.value = _state.value.copy(messages = emptyList())
    }

    private fun newId(): String = UUID.randomUUID().toString()

    companion object {
        fun factory(store: ConfigStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(store) as T
            }
    }
}
