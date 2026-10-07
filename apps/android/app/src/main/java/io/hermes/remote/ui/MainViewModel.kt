package io.hermes.remote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.hermes.remote.BuildConfig
import io.hermes.remote.data.ChatApi
import io.hermes.remote.data.ConfigStore
import io.hermes.remote.data.GatewayApi
import io.hermes.remote.data.StreamEvent
import io.hermes.remote.model.ChatMessage
import io.hermes.remote.model.DeliveryState
import io.hermes.remote.model.GatewayInfo
import io.hermes.remote.model.Role
import io.hermes.remote.ui.theme.ThemeMode
import io.hermes.remote.model.ServerConfig
import io.hermes.remote.updater.ApkDownloader
import io.hermes.remote.updater.UpdateChecker
import io.hermes.remote.updater.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    /**
     * Whether the configured backend is known to be reachable.
     *
     * Set by a successful probe or a completed turn, cleared when a turn fails.
     * It is a real field rather than something inferred from [connectionHint]
     * because the hint is user-facing prose: parsing it to decide a colour
     * breaks the moment the wording or translation changes.
     */
    val connected: Boolean = false,

    /**
     * What the PC says about itself: health, capabilities, past sessions.
     *
     * Null until the first successful fetch, which is different from "fetched
     * and empty" — the UI uses that distinction to avoid claiming the PC has
     * no sessions when it simply has not been asked yet.
     */
    val gateway: GatewayInfo? = null,

    /** True while a gateway info fetch is in flight. */
    val gatewayLoading: Boolean = false,
)

/** Phase of a voice turn, used to drive the mic button + status text. */
enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * State of the voice assistant. [conversationMode] mirrors whether the app will
 * reopen the microphone after a spoken reply; [micAvailable] is false when the
 * device has no speech recognizer at all.
 */
data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val micAvailable: Boolean = true,
    val partial: String = "",
    val statusMessage: String? = null,
    /**
     * Continuous-conversation ("hands-free dialogue") is on.
     *
     * This is the app's only hands-free mode: it does not listen in the
     * background, it simply reopens the microphone after each spoken reply so
     * the user can keep talking without tapping.
     */
    val conversationMode: Boolean = false,
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
 * How long continuous conversation waits for TTS to report completion before
 * reopening the microphone anyway, in milliseconds.
 *
 * Sized to cover a short utterance's tail: long enough that a normal reply
 * finishes on its own, short enough that a failed callback does not leave the
 * user pressing the switch again.
 */
private const val SPEAK_COMPLETION_GRACE_MS = 15_000L

/**
 * Pause before re-arming the microphone after a recognition error, in
 * milliseconds.
 *
 * A recognizer that is failing fast (no network, no permission) would otherwise
 * be restarted in a tight loop; this keeps a broken session from spinning the
 * CPU while still recovering on its own once the cause clears.
 */
private const val CONVERSATION_RETRY_DELAY_MS = 2_000L

/**
 * Bridge the ViewModel uses to drive microphone/speech, so the ViewModel itself
 * stays free of Android framework classes and unit-testable on the JVM.
 *
 * The Activity provides the concrete implementation (STT + TTS).
 */
interface VoiceController {
    /** Whether the device has a speech recognizer. */
    fun isMicAvailable(): Boolean

    /** Whether replies should be read aloud. */
    fun isSpeakRepliesEnabled(): Boolean

    /** Begin one listening turn; results come back through [onResult]/[onPartial]/[onError]. */
    fun startListening(onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit)

    /** Stop the current listening turn. */
    fun stopListening()

    /** Speak an assistant reply aloud. */
    fun speak(text: String)

    /** Stop any speech in progress (user interrupt). */
    fun stopSpeaking()

    /**
     * Called by the voice layer once a spoken reply has finished playing.
     *
     * Continuous conversation needs this edge: only when the assistant has
     * stopped talking is it safe to reopen the microphone, otherwise the mic
     * would record the reply itself.
     */
    fun onSpeakFinished()
}

/** No-op controller used by tests and previews. */
object NoopVoiceController : VoiceController {
    override fun isMicAvailable() = false
    override fun isSpeakRepliesEnabled() = false
    override fun startListening(onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit) = Unit
    override fun stopListening() = Unit
    override fun speak(text: String) = Unit
    override fun stopSpeaking() = Unit
    override fun onSpeakFinished() = Unit
}

/**
 * Localised text the ViewModel needs.
 *
 * Injected rather than read from a Context so the ViewModel stays free of
 * Android framework classes and unit-testable on the JVM — the same reason
 * [VoiceController] exists. The Activity supplies the resource-backed
 * implementation; tests use [Default].
 */
interface StringProvider {
    /** "Machine status" and its prompt, "List files" and its prompt, … */
    fun quickActions(): List<Pair<String, String>>

    fun missingConfig(): String
    fun connectionOk(modelCount: Int, sample: String): String
    fun connectionFailed(): String

    fun updateFailed(): String
    fun emptyReply(): String
    fun noReplyFromServer(): String
    fun errorSpoken(detail: String): String
    fun noAnswer(): String

    fun noRecognizer(): String

    /** ChatApi's own messages, in the current language. */
    fun chatApiMessages(): ChatApi.Messages
}

/**
 * English defaults, for tests and previews that do not assert on wording.
 *
 * Kept in sync with values/strings.xml; a test asserts the quick-action count
 * so a missing entry here cannot silently shrink the UI.
 */
object DefaultStringProvider : StringProvider {
    override fun quickActions() = listOf(
        "Machine status" to "Quickly check my computer's status: CPU, RAM and free disk space.",
        "List files" to "List the files in my current folder.",
        "Run tests" to "Run the current project's test suite and summarize the result.",
        "Git status" to "Tell me the current git status of the project I'm working on.",
        "Shut down" to "Ask me to confirm, then shut down the computer in 60 seconds.",
    )

    override fun missingConfig() = "Missing URL or API key"
    override fun connectionOk(modelCount: Int, sample: String) =
        "Connected — $modelCount models: $sample"
    override fun connectionFailed() =
        "Could not connect (check the URL, key, and that the server is running)"

    override fun updateFailed() = "Could not download the update"
    override fun emptyReply() = "No reply."
    override fun noReplyFromServer() = "No reply from the server"
    override fun errorSpoken(detail: String) = "Sorry, something went wrong. $detail"
    override fun noAnswer() = "Sorry, I don't have an answer."

    override fun noRecognizer() = "This device has no speech recognizer"

    override fun chatApiMessages() = ChatApi.Messages.DEFAULT
}

/** Drives the chat: holds transcript, config, and streams replies. */
class MainViewModel(
    private val configStore: ConfigStore,
    private val api: ChatApi = ChatApi(),
    private val gatewayApi: GatewayApi = GatewayApi(),
    private val updateChecker: UpdateChecker = UpdateChecker(BuildConfig.VERSION_CODE),
    private val updatePrefs: android.content.SharedPreferences? = null,
    private val voice: VoiceController = NoopVoiceController,
    private val strings: StringProvider = DefaultStringProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(
            config = configStore.load(),
            voice = VoiceUiState(
                micAvailable = voice.isMicAvailable(),
            ),
        ),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /**
     * Probe the saved server once at startup.
     *
     * Without this the app only ever tested the connection right after a QR
     * scan, so a user who paired once and reopened the app got a permanent
     * "not connected" strip even though nothing was wrong — the probe simply
     * had never run. Firing it here means the strip reflects reality on open.
     */
    init {
        testConnection(_state.value.config)
        refreshGatewayInfo()
    }

    /**
     * Ask the PC to describe itself and publish the answer.
     *
     * Best-effort by design: a failure leaves the last known info in place
     * rather than blanking the screen. Losing a health read must never look
     * like losing the connection, which [connected] already tracks.
     */
    fun refreshGatewayInfo() {
        val config = _state.value.config
        if (!config.isValid()) return
        _state.value = _state.value.copy(gatewayLoading = true)
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { gatewayApi.fetchInfo(config) }
            _state.value = _state.value.copy(
                gateway = info ?: _state.value.gateway,
                gatewayLoading = false,
            )
        }
    }

    /**
     * The colour mode, kept in its own flow rather than [ChatUiState].
     *
     * Theme is not chat state: putting it in the chat flow would rebuild the
     * transcript's UI on every toggle and entangle a presentation preference
     * with the turn lifecycle.
     */
    private val _themeMode = MutableStateFlow(configStore.loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Persist and apply a new colour mode. */
    fun setThemeMode(mode: ThemeMode) {
        configStore.saveThemeMode(mode)
        _themeMode.value = mode
    }

    /** True when an update banner should be shown to the user. */
    val hasUpdate: Boolean get() = _state.value.update.available != null

    /**
     * True while a turn was started by voice, so the reply should be spoken
     * back once the stream finishes.
     */
    private var spokenSession = false

    /**
     * Continuous-conversation flag: reopen the microphone after each spoken
     * reply. Deliberately NOT persisted — an always-on mic that survives a
     * restart would be a surprise, and the user can flip the switch again.
     */
    private var conversationMode = false

    /**
     * Monotonic token identifying the current speech turn.
     *
     * Guards [armConversationFallback]: a stale timer compares against this and
     * bails, so it cannot reopen the mic during a later turn's reply.
     */
    private var speechSession = 0

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
                            error = e.message ?: strings.updateFailed(),
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
    val quickActions: List<Pair<String, String>> get() = strings.quickActions()

    /** The agent ids the UI should offer, empty when the PC reported none. */
    val agentChoices: List<String> get() = _state.value.config.agentChoices

    /**
     * Switch which CLI answers.
     *
     * The agent id travels in the request's `model` field, so this is the whole
     * mechanism for choosing Codex over Claude. Persisted immediately so the
     * choice survives a restart, matching how the rest of the config behaves.
     */
    fun selectAgent(agentId: String) {
        val id = agentId.trim()
        if (id.isEmpty()) return
        val current = _state.value.config
        val existing = current.agents.firstOrNull { it.equals(id, ignoreCase = true) }
            ?: id
        if (existing == current.model) return
        saveConfig(current.copy(model = existing))
    }

    /**
     * Refresh the agent list from the server's /v1/models, so the picker works
     * even when the QR payload predates the agent fields — or when a CLI was
     * installed after pairing.
     *
     * Silent on failure: this is a convenience, not a precondition, and a
     * spurious error banner would be worse than a stale list.
     */
    fun refreshAgents() {
        val config = _state.value.config
        if (!config.isValid()) return
        viewModelScope.launch {
            val models = withContext(Dispatchers.IO) { api.listModels(config) }
            // Drop the "hermes" passthrough: it is served by the gateway, not
            // this bridge, and selecting it would only return an error.
            val agents = models.filter { it.isNotBlank() && it.lowercase() != "hermes" }
            if (agents.isEmpty()) return@launch
            configStore.saveAgents(agents, config.defaultAgent)
            _state.value = _state.value.copy(
                config = _state.value.config.copy(agents = agents),
            )
        }
    }

    fun onInputChange(value: String) {
        _state.value = _state.value.copy(input = value)
    }

    fun saveConfig(config: ServerConfig) {
        configStore.save(config)
        // Drop the previous PC's info: it belongs to the old config, and
        // showing it against the new one would be actively misleading.
        _state.value = _state.value.copy(config = config, connectionHint = null, gateway = null)
        refreshGatewayInfo()
    }

    /**
     * Apply a payload scanned from the `hermes-remote` QR: persist it as the
     * server config and immediately probe the connection so the user sees a
     * result without touching the settings form.
     */
    fun applyConnectPayload(payload: io.hermes.remote.model.ConnectPayload) {
        val config = payload.toServerConfig()
        configStore.save(config)
        _state.value = _state.value.copy(config = config, connectionHint = null, gateway = null)
        testConnection(config)
        refreshGatewayInfo()
    }

    /** Probe the server by listing models and set a human-readable hint. */
    fun testConnection(config: ServerConfig) {
        viewModelScope.launch {
            if (!config.isValid()) {
                _state.value = _state.value.copy(
                    connectionHint = strings.missingConfig(),
                    connected = false,
                )
                return@launch
            }
            val models = withContext(Dispatchers.IO) { api.listModels(config) }
            val hint = when {
                models.isNotEmpty() ->
                    strings.connectionOk(models.size, models.take(3).joinToString())
                else -> strings.connectionFailed()
            }
            _state.value = _state.value.copy(
                connectionHint = hint,
                connected = models.isNotEmpty(),
            )
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
                api.streamChat(
                    current.config,
                    history,
                    strings.chatApiMessages(),
                ) { event ->
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
            connected = false,
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
        if (wasSpoken) speakAndReturnToIdle(strings.errorSpoken(message))
    }

    private fun finishStream(id: String, result: String) {
        val alreadyFailed = _state.value.messages
            .firstOrNull { it.id == id }
            ?.state == DeliveryState.ERROR
        if (alreadyFailed) return

        val wasSpoken = spokenSession
        spokenSession = false
        val blank = result.isBlank()

        // A turn that came back with content is proof the backend is reachable,
        // so the status pill stops saying "not connected" even when the user
        // never ran an explicit probe.
        if (!blank && !_state.value.connected) {
            _state.value = _state.value.copy(connected = true)
        }

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
                        content = result.ifBlank { strings.emptyReply() },
                        error = if (blank) strings.noReplyFromServer() else null,
                    )
                } else {
                    msg
                }
            },
        )

        if (wasSpoken) {
            speakAndReturnToIdle(if (blank) strings.noAnswer() else result)
        }
    }

    /**
     * Read [text] aloud (if speak-replies is on). When speaking is off — or the
     * engine is unavailable — the voice phase drops straight back to IDLE.
     *
     * In continuous-conversation mode the microphone is NOT reopened here: that
     * waits for [onSpeakFinished], because reopening while TTS is still playing
     * would make the recognizer transcribe the assistant's own voice.
     */
    private fun speakAndReturnToIdle(text: String) {
        if (voice.isSpeakRepliesEnabled()) {
            voice.speak(text)
            _state.value = _state.value.copy(
                voice = _state.value.voice.copy(phase = VoicePhase.SPEAKING),
            )
            // With no audio the completion callback may never arrive (silent
            // TTS, empty sanitized text), so arm a fallback that keeps the
            // dialogue alive instead of stranding the user.
            armConversationFallback()
        } else {
            _state.value = _state.value.copy(
                voice = _state.value.voice.copy(phase = VoicePhase.IDLE),
            )
            if (conversationMode) startVoiceInput()
        }
    }

    /**
     * Safety net for continuous mode: if TTS never reports completion, resume
     * listening anyway after a grace period.
     *
     * [speakAgain] makes the stale timer harmless — it is only honoured while
     * the session is still the current one, so a timer left over from an
     * earlier turn cannot reopen the mic in the middle of a later reply.
     */
    private fun armConversationFallback() {
        if (!conversationMode) return
        val session = ++speechSession
        viewModelScope.launch {
            delay(SPEAK_COMPLETION_GRACE_MS)
            if (session == speechSession && conversationMode && !_state.value.isStreaming) {
                resumeConversationIfIdle()
            }
        }
    }

    /**
     * TTS finished (or the fallback fired): reopen the microphone when the user
     * asked for a continuous conversation.
     *
     * Not an `override`: the ViewModel *consumes* [VoiceController], it does not
     * implement it. The Activity wires the real controller's callback here.
     */
    fun onSpeakFinished() {
        if (!conversationMode) return
        // Invalidate any fallback timer for this turn so it cannot double-fire.
        speechSession++
        resumeConversationIfIdle()
    }

    private fun resumeConversationIfIdle() {
        val current = _state.value
        if (!conversationMode) return
        if (current.isStreaming) return
        // Already listening (e.g. the fallback raced the real callback): leave it.
        if (current.voice.phase == VoicePhase.LISTENING) return
        startVoiceInput()
    }

    /**
     * Turn continuous conversation on/off.
     *
     * Turning it on starts listening immediately, so flipping the switch is all
     * the user has to do. Turning it off only stops re-arming: the in-flight
     * turn is allowed to finish rather than being cut off mid-sentence.
     */
    fun setConversationMode(on: Boolean) {
        conversationMode = on
        _state.value = _state.value.copy(
            voice = _state.value.voice.copy(conversationMode = on),
        )
        if (on) {
            if (_state.value.isStreaming) return
            if (_state.value.voice.phase == VoicePhase.LISTENING) return
            startVoiceInput()
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
                voice = current.voice.copy(statusMessage = strings.noRecognizer()),
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
                    // Silence is not a reason to end a conversation: without
                    // this the mode would switch itself off the first time the
                    // user paused to think.
                    resumeConversationIfIdle()
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
                // A recognition hiccup must not silently end the conversation,
                // but reopening immediately on a hard failure would spin. Wait
                // out a short pause, then re-arm if the user still wants it.
                if (conversationMode) {
                    val session = ++speechSession
                    viewModelScope.launch {
                        delay(CONVERSATION_RETRY_DELAY_MS)
                        if (session == speechSession && conversationMode) {
                            resumeConversationIfIdle()
                        }
                    }
                }
            },
        )
    }

    /**
     * Abort the current listening turn (user tapped "stop").
     *
     * Also leaves continuous-conversation mode: "stop" must be an exit, never
     * just a pause that the next callback silently undoes.
     */
    fun stopVoiceInput() {
        spokenSession = false
        val leaving = conversationMode
        conversationMode = false
        speechSession++
        voice.stopListening()
        _state.value = _state.value.copy(
            voice = _state.value.voice.copy(
                phase = VoicePhase.IDLE,
                partial = "",
                conversationMode = if (leaving) false else _state.value.voice.conversationMode,
            ),
        )
    }

    /** Stop reading the current reply aloud, leaving continuous mode. */
    fun stopSpeaking() {
        val leaving = conversationMode
        conversationMode = false
        speechSession++
        voice.stopSpeaking()
        _state.value = _state.value.copy(
            voice = _state.value.voice.copy(
                phase = VoicePhase.IDLE,
                conversationMode = if (leaving) false else _state.value.voice.conversationMode,
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
            strings: StringProvider = DefaultStringProvider,
            gatewayApi: GatewayApi = GatewayApi(),
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(
                        configStore = store,
                        gatewayApi = gatewayApi,
                        updateChecker = UpdateChecker(BuildConfig.VERSION_CODE),
                        updatePrefs = updatePrefs,
                        voice = voice,
                        strings = strings,
                    ) as T
            }
    }
}
