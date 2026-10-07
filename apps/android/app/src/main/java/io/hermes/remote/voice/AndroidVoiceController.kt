package io.hermes.remote.voice

import android.content.Context
import io.hermes.remote.data.ConfigStore
import io.hermes.remote.ui.VoiceController

/**
 * Concrete [VoiceController] the Activity hands to [io.hermes.remote.ui.MainViewModel].
 *
 * Owns the Android-side voice plumbing: system STT ([SpeechRecognizerManager])
 * and system TTS ([TtsSpeaker]). Kept out of the Activity so the Activity stays
 * a thin Compose host.
 *
 * There is no background listening: the microphone is opened only for a turn
 * the user started, either by tapping the mic or by leaving continuous
 * conversation on.
 */
class AndroidVoiceController(private val context: Context) : VoiceController {

    private var recognizer: SpeechRecognizerManager? = null
    private var tts: TtsSpeaker? = null

    /**
     * Fired when a spoken reply finishes. Set by the Activity so the
     * continuous-conversation loop knows it may reopen the microphone.
     *
     * Held here rather than passed to [speak] because TTS completion is
     * asynchronous and the caller that starts the speech is not the one that
     * needs to hear about its end.
     */
    var speakFinishedListener: (() -> Unit)? = null

    override fun onSpeakFinished() {
        speakFinishedListener?.invoke()
    }

    /**
     * Initialize TTS eagerly (it is cheap) so the first spoken reply is prompt.
     * Safe to call once, from the Activity's onCreate.
     */
    fun warmUp() {
        if (tts == null) {
            tts = TtsSpeaker(context).also { it.init(ttsListener) }
        }
    }

    /**
     * Bridges [TtsSpeaker.Listener] to [onSpeakFinished].
     *
     * Every terminal path (done, both error overloads, and a missing language)
     * reports completion, because the conversation loop must never be left
     * waiting for a callback TTS will not send — that would strand the user
     * with a dead microphone.
     */
    private val ttsListener = object : TtsSpeaker.Listener {
        override fun onStart() = Unit

        override fun onDone() {
            speakFinishedListener?.invoke()
        }

        override fun onLanguageMissing() = Unit
    }

    override fun isMicAvailable(): Boolean = SpeechRecognizerManager.isAvailable(context)

    override fun isSpeakRepliesEnabled(): Boolean = ConfigStore(context).isSpeakRepliesEnabled()

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
        // Pass the listener here too: if TTS was never warmed up this is the
        // construction site, and a speaker without the listener would never
        // report completion — silently breaking the conversation loop.
        if (tts == null) tts = TtsSpeaker(context).also { it.init(ttsListener) }
        tts?.speak(text)
    }

    override fun stopSpeaking() {
        tts?.stop()
    }

    /** Release STT/TTS resources. Called from the Activity's onDestroy. */
    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }
}
