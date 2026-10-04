package com.hermes.bridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.bridge.BuildConfig
import com.hermes.bridge.data.ChatApi
import com.hermes.bridge.data.ConfigStore
import com.hermes.bridge.data.StreamEvent
import com.hermes.bridge.model.ChatMessage
import com.hermes.bridge.model.DeliveryState
import com.hermes.bridge.model.Role
import com.hermes.bridge.model.ServerConfig
import com.hermes.bridge.updater.ApkDownloader
import com.hermes.bridge.updater.UpdateChecker
import com.hermes.bridge.updater.UpdateInfo
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
    val update: UpdateUiState = UpdateUiState(),
)

/** State of the self-update flow shown as a banner/dialog. */
data class UpdateUiState(
    val available: UpdateInfo? = null,
    val downloading: Boolean = false,
    val progress: Int? = null,
    val lastCheckedAt: Long = 0L,
    val error: String? = null,
    val readyToInstall: Boolean = false,
)

/** How often to re-check in the background once the app is open. */
private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6h

/** Drives the chat: holds transcript, config, and streams replies. */
class MainViewModel(
    private val configStore: ConfigStore,
    private val api: ChatApi = ChatApi(),
    private val updateChecker: UpdateChecker = UpdateChecker(BuildConfig.VERSION_CODE),
    private val updatePrefs: android.content.SharedPreferences? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState(config = configStore.load()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** True when an update banner should be shown to the user. */
    val hasUpdate: Boolean get() = _state.value.update.available != null

    /**
     * Check for a newer release, at most once per [UPDATE_CHECK_INTERVAL_MS]
     * unless [force] is set. Safe to call on every app start; network and parse
     * failures are swallowed into [UpdateUiState.error] and never surface UI.
     */
    fun checkForUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val last = updatePrefs?.getLong(KEY_LAST_UPDATE_CHECK, 0L) ?: 0L
        if (!force && now - last < UPDATE_CHECK_INTERVAL_MS) return

        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { updateChecker.check() }.getOrNull()
            }
            updatePrefs?.edit()?.putLong(KEY_LAST_UPDATE_CHECK, now)?.apply()
            _state.value = _state.value.copy(
                update = _state.value.update.copy(
                    available = info,
                    lastCheckedAt = now,
                    error = null,
                ),
            )
        }
    }

    /**
     * Download the pending update, then report whether the installer can be
     * launched. On [onReady] the caller runs [UpdateInstaller].
     */
    fun downloadUpdate(
        downloader: ApkDownloader,
        canInstall: () -> Boolean,
        onReady: (java.io.File) -> Unit,
        onNeedsPermission: () -> Unit,
    ) {
        val info = _state.value.update.available ?: return
        if (_state.value.update.downloading) return

        _state.value = _state.value.copy(
            update = _state.value.update.copy(downloading = true, progress = null, error = null),
        )
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    downloader.download(info) { pct ->
                        _state.value = _state.value.copy(
                            update = _state.value.update.copy(progress = pct),
                        )
                    }
                }
            }
            file.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        update = _state.value.update.copy(
                            downloading = false, progress = 100, readyToInstall = true,
                        ),
                    )
                    if (canInstall()) onReady(it) else onNeedsPermission()
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        update = _state.value.update.copy(
                            downloading = false, progress = null,
                            error = e.message ?: "Tải bản cập nhật thất bại",
                        ),
                    )
                },
            )
        }
    }

    /** Clear the update banner (e.g. user dismissed this version). */
    fun dismissUpdate() {
        _state.value = _state.value.copy(update = UpdateUiState(lastCheckedAt = _state.value.update.lastCheckedAt))
    }


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
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"

        fun factory(
            store: ConfigStore,
            updatePrefs: android.content.SharedPreferences? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(
                        configStore = store,
                        updateChecker = UpdateChecker(BuildConfig.VERSION_CODE),
                        updatePrefs = updatePrefs,
                    ) as T
            }
    }
}
