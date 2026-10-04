package com.hermes.bridge.voice

import android.content.Context
import com.hermes.bridge.data.ConfigStore
import com.hermes.bridge.ui.VoiceController

/**
 * Concrete [VoiceController] the Activity hands to [com.hermes.bridge.ui.MainViewModel].
 *
 * Owns the Android-side voice plumbing: system STT ([SpeechRecognizerManager]),
 * system TTS ([TtsSpeaker]) and the foreground wake-word service. Kept out of
 * the Activity so the Activity stays a thin Compose host.
 */
class AndroidVoiceController(private val context: Context) : VoiceController {

    private val configStore = ConfigStore(context)
    private var recognizer: SpeechRecognizerManager? = null
    private var tts: TtsSpeaker? = null

    /** Latched result of the last wake-word service start. */
    private var wakeWordActive = false

    /**
     * Initialize TTS eagerly (it is cheap) so the first spoken reply is prompt.
     * Safe to call once, from the Activity's onCreate.
     */
    fun warmUp() {
        if (tts == null) {
            tts = TtsSpeaker(context).also { it.init() }
        }
    }

    override fun isMicAvailable(): Boolean = SpeechRecognizerManager.isAvailable(context)

    override fun isAssistantEnabled(): Boolean = configStore.isVoiceAssistantEnabled()

    override fun isSpeakRepliesEnabled(): Boolean = configStore.isSpeakRepliesEnabled()

    override fun isWakeWordActive(): Boolean = wakeWordActive

    override fun startListening(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        // Stop any speech so the mic does not hear the assistant itself.
        tts?.stop()

        // Recreate the recognizer if it was released or never created.
        if (recognizer == null || recognizer?.isDestroyed == true) {
            recognizer = SpeechRecognizerManager(context)
        }
        recognizer?.start(object : SpeechRecognizerManager.Listener {
            override fun onReady() = Unit
            override fun onPartial(text: String) = onPartial(text)
            override fun onFinal(text: String) = onResult(text)
            override fun onError(message: String) = onError(message)
            override fun onEnded() = Unit
        })
    }

    override fun stopListening() {
        recognizer?.stop()
    }

    override fun speak(text: String) {
        if (tts == null) tts = TtsSpeaker(context).also { it.init() }
        tts?.speak(text)
    }

    override fun stopSpeaking() {
        tts?.stop()
    }

    override fun setAssistantEnabled(enabled: Boolean): Boolean {
        return if (enabled) {
            VoiceAssistantService.start(context)
            // The service reports whether the wake engine truly started via
            // VoiceBus; assume enabled for UI immediacy and let the event correct it.
            wakeWordActive = true
            true
        } else {
            VoiceAssistantService.stop(context)
            wakeWordActive = false
            false
        }
    }

    /** Release STT/TTS resources. Called from the Activity's onDestroy. */
    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }
}
