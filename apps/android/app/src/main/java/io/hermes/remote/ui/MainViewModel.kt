package io.hermes.remote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.hermes.remote.BuildConfig
import io.hermes.remote.data.ChatApi
import io.hermes.remote.data.ConfigStore
import io.hermes.remote.data.StreamEvent
import io.hermes.remote.model.ChatMessage
import io.hermes.remote.model.DeliveryState
import io.hermes.remote.model.Role
import io.hermes.remote.model.ServerConfig
import io.hermes.remote.updater.ApkDownloader
import io.hermes.remote.updater.UpdateChecker
import io.hermes.remote.updater.UpdateInfo
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
    val voice: VoiceUiState = VoiceUiState(),
    /** Non-null when the last turn was handed over by Google Assistant. */
    val assistantPrompt: String? = null,
)

/** Phase of a voice turn, used to drive the mic button + status text. */
enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * State of the voice assistant. [enabled] mirrors whether the always-listening
 * wake-word service is running; [micAvailable] is false when the device has no
 * speech recognizer at all.
 */
data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val enabled: Boolean = false,
    val micAvailable: Boolean = true,
    val wakeWordActive: Boolean = false,
    val partial: String = "",
    val statusMessage: String? = null,
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

/**
 * Bridge the ViewModel uses to drive microphone/speech, so the ViewModel itself
 * stays free of Android framework classes and unit-testable on the JVM.
 *
 * The Activity provides the concrete implementation (STT + TTS + wake service).
 */
interface VoiceController {
    /** Whether the device has a speech recognizer. */
    fun isMicAvailable(): Boolean

    /** Whether the always-listening wake-word service is currently enabled. */
    fun isAssistantEnabled(): Boolean

    /** Whether replies should be read aloud. */
    fun isSpeakRepliesEnabled(): Boolean

    /** Whether the wake-word engine actually started (has a key + keyword). */
    fun isWakeWordActive(): Boolean

    /** Begin one listening turn; results come back through [onResult]/[onPartial]/[onError]. */
    fun startListening(onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit)

    /** Stop the current listening turn. */
    fun stopListening()

    /** Speak an assistant reply aloud. */
    fun speak(text: String)

    /** Stop any speech in progress (user interrupt). */
    fun stopSpeaking()

    /** Enable/disable the wake-word service; returns whether it is now active. */
    fun setAssistantEnabled(enabled: Boolean): Boolean
}

/** No-op controller used by tests and previews. */
object NoopVoiceController : VoiceController {
    override fun isMicAvailable() = false
    override fun isAssistantEnabled() = false
    override fun isSpeakRepliesEnabled() = false
    override fun isWakeWordActive() = false
    override fun startListening(onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit) = Unit
    override fun stopListening() = Unit
    override fun speak(text: String) = Unit
    override fun stopSpeaking() = Unit
    override fun setAssistantEnabled(enabled: Boolean) = false
}

/** Drives the chat: holds transcript, config, and streams replies. */
class MainViewModel(
    private val configStore: ConfigStore,
    private val api: ChatApi = ChatApi(),
    private val updateChecker: UpdateChecker = UpdateChecker(BuildConfig.VERSION_CODE),
    private val updatePrefs: android.content.SharedPreferences? = null,
    private val voice: VoiceController = NoopVoiceController,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(
            config = configStore.load(),
            voice = VoiceUiState(
                enabled = voice.isAssistantEnabled(),
                micAvailable = voice.isMicAvailable(),
                wakeWordActive = voice.isWakeWordActive(),
            ),
        ),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** True when an update banner should be shown to the user. */
    val hasUpdate: Boolean get() = _state.value.update.available != null

    /**
     * True while a turn was started by voice, so the reply should be spoken
     * back once the stream finishes.
     */
    private var spokenSession = false

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
    fun applyConnectPayload(payload: io.hermes.remote.model.ConnectPayload) {
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
        // A spoken session is one whose prompt came in via voice (see startVoiceInput).
        val voicePhase = if (spokenSession) VoicePhase.THINKING else current.voice.phase
        _state.value = current.copy(
            messages = history + placeholder,
            input = "",
            isStreaming = true,
            connectionHint = null,
            voice = current.voice.copy(
                phase = voicePhase,
                partial = "",
                statusMessage = null,
            ),
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
        // A failed voice turn should surface the error aloud too, so a
        // hands-free user knows it broke without looking at the screen.
        val wasSpoken = spokenSession
        spokenSession = false
        _state.value = _state.value.copy(
            isStreaming = false,
            voice = _state.value.voice.copy(
                phase = if (wasSpoken) VoicePhase.SPEAKING else VoicePhase.IDLE,
                partial = "",
            ),
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
        if (wasSpoken) speakAndReturnToIdle("Xin lỗi, có lỗi xảy ra. $message")
    }

    private fun finishStream(id: String, result: String) {
        val alreadyFailed = _state.value.messages
            .firstOrNull { it.id == id }
            ?.state == DeliveryState.ERROR
        if (alreadyFailed) return

        val wasSpoken = spokenSession
        spokenSession = false
        val blank = result.isBlank()

        _state.value = _state.value.copy(
            isStreaming = false,
            voice = _state.value.voice.copy(
                phase = if (wasSpoken && !blank) VoicePhase.SPEAKING else VoicePhase.IDLE,
                partial = "",
            ),
            messages = _state.value.messages.map { msg ->
                if (msg.id == id) {
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

        if (wasSpoken) {
            speakAndReturnToIdle(if (blank) "Xin lỗi, mình không có câu trả lời." else result)
        }
    }

    /**
     * Read [text] aloud (if speak-replies is on) then drop the voice phase back
     * to IDLE. When speaking is off we return to IDLE immediately.
     */
    private fun speakAndReturnToIdle(text: String) {
        if (voice.isSpeakRepliesEnabled()) {
            voice.speak(text)
            _state.value = _state.value.copy(
                voice = _state.value.voice.copy(phase = VoicePhase.SPEAKING),
            )
        } else {
            _state.value = _state.value.copy(
                voice = _state.value.voice.copy(phase = VoicePhase.IDLE),
            )
        }
    }

    /**
     * Send a prompt that arrived from Google Assistant / an App Action.
     *
     * Treated as a spoken turn: the user asked out loud via the Assistant, so
     * the reply is read back through TTS. Marks the state so the UI can show
     * where the prompt came from.
     */
    fun sendFromAssistant(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty()) return
        spokenSession = true
        _state.value = _state.value.copy(assistantPrompt = text)
        send(text)
    }

    fun clearAssistantPrompt() {
        _state.value = _state.value.copy(assistantPrompt = null)
    }

    // --- Voice assistant -----------------------------------------------------

    /**
     * Begin one voice turn: listen, then send the transcript as a normal chat
     * message. The reply is spoken back when the stream finishes.
     */
    fun startVoiceInput() {
        val current = _state.value
        if (current.isStreaming) return
        if (!current.voice.micAvailable) {
            _state.value = current.copy(
                voice = current.voice.copy(statusMessage = "Thiết bị không có trình nhận dạng giọng nói"),
            )
            return
        }
        spokenSession = true
        _state.value = current.copy(
            voice = current.voice.copy(
                phase = VoicePhase.LISTENING,
                partial = "",
                statusMessage = null,
            ),
        )
        voice.startListening(
            onPartial = { p ->
                _state.value = _state.value.copy(
                    voice = _state.value.voice.copy(partial = p),
                )
            },
            onResult = { text ->
                spokenSession = text.isNotBlank()
                if (text.isBlank()) {
                    _state.value = _state.value.copy(
                        voice = _state.value.voice.copy(phase = VoicePhase.IDLE, partial = ""),
                    )
                } else {
                    send(text)
                }
            },
            onError = { message ->
                spokenSession = false
                _state.value = _state.value.copy(
                    voice = _state.value.voice.copy(
                        phase = VoicePhase.IDLE,
                        partial = "",
                        statusMessage = message,
                    ),
                )
            },
        )
    }

    /** Abort the current listening turn (user tapped "stop"). */
    fun stopVoiceInput() {
        spokenSession = false
        voice.stopListening()
        _state.value = _state.value.copy(
            voice = _state.value.voice.copy(phase = VoicePhase.IDLE, partial = ""),
        )
    }

    /** Stop reading the current reply aloud. */
    fun stopSpeaking() {
        voice.stopSpeaking()
        _state.value = _state.value.copy(
            voice = _state.value.voice.copy(phase = VoicePhase.IDLE),
        )
    }

    /**
     * Turn the always-listening wake-word assistant on/off. Returns the new
     * enabled state. Persists the choice so it survives restarts.
     */
    fun setVoiceAssistantEnabled(enabled: Boolean): Boolean {
        val active = voice.setAssistantEnabled(enabled)
        configStore.setVoiceAssistantEnabled(enabled)
        _state.value = _state.value.copy(
            voice = _state.value.voice.copy(
                enabled = enabled,
                wakeWordActive = voice.isWakeWordActive(),
            ),
        )
        return active
    }

    /** Called when the wake word fires: kick off a listening turn. */
    fun onWakeWord() {
        if (_state.value.isStreaming) return
        startVoiceInput()
    }

    /** Surface that the wake-word engine could not start (missing key, etc.). */
    fun onWakeWordUnavailable() {
        _state.value = _state.value.copy(
            voice = _state.value.voice.copy(
                wakeWordActive = false,
                statusMessage = "Chưa cấu hình wake word — bấm micro để nói",
            ),
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
            voice: VoiceController = NoopVoiceController,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(
                        configStore = store,
                        updateChecker = UpdateChecker(BuildConfig.VERSION_CODE),
                        updatePrefs = updatePrefs,
                        voice = voice,
                    ) as T
            }
    }
}
