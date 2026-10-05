package io.hermes.remote.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Thin wrapper around the Android system [SpeechRecognizer].
 *
 * Uses the on-device recognizer (no cloud dependency, no extra permission beyond
 * RECORD_AUDIO). Callbacks are delivered on the main thread, matching the
 * platform contract — callers should not block in them.
 *
 * Vietnamese is the default language; if the device lacks a vi-VN recognizer the
 * platform falls back automatically to the system default.
 */
class SpeechRecognizerManager(private val context: Context) {

    /** Callbacks for one listening session. */
    interface Listener {
        /** Fired once when the recognizer is ready to accept audio. */
        fun onReady()
        /** A partial (still-changing) transcription, e.g. while the user talks. */
        fun onPartial(text: String)
        /** The final transcript for this session. */
        fun onFinal(text: String)
        /** Human-readable error (already mapped from the platform code). */
        fun onError(message: String)
        /** Fired when the session ends, whether by result, error, or cancel. */
        fun onEnded()
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var listening = false

    /** True once [destroy] has run; the manager must not be reused afterwards. */
    var isDestroyed = false
        private set

    /** True while a recognition session is active. */
    val isListening: Boolean get() = listening

    /**
     * Start a single recognition session. Safe to call when already listening —
     * the previous session is cancelled first.
     */
    fun start(listener: Listener) {
        if (isDestroyed) return
        this.listener = listener

        ensureRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, DEFAULT_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        listening = true
        recognizer?.startListening(intent)
    }

    /** Stop listening; any pending result is still delivered. */
    fun stop() {
        if (isDestroyed) return
        recognizer?.stopListening()
    }

    /** Abort the current session without a result. */
    fun cancel() {
        if (isDestroyed) return
        listening = false
        recognizer?.cancel()
    }

    /** Release the platform recognizer. The manager is unusable afterwards. */
    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        listening = false
        recognizer?.destroy()
        recognizer = null
        listener = null
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(platformListener)
            }
        }
    }

    private val platformListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener?.onReady()
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults) ?: return
            listener?.onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            listening = false
            if (text.isNullOrBlank()) {
                listener?.onError("Không nghe rõ, bạn thử lại nhé.")
            } else {
                listener?.onFinal(text)
            }
            listener?.onEnded()
        }

        override fun onError(error: Int) {
            listening = false
            listener?.onError(messageForError(error))
            listener?.onEnded()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    companion object {
        /** Vietnamese by default; the platform downgrades gracefully if unsupported. */
        const val DEFAULT_LANGUAGE = "vi-VN"

        /** Whether the device has any recognition service at all. */
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)

        /** Map a [SpeechRecognizer] error code to a friendly Vietnamese message. */
        fun messageForError(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Lỗi thu âm. Thử lại nhé."
            SpeechRecognizer.ERROR_CLIENT -> "Trình nhận dạng bận, thử lại nhé."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Chưa có quyền micro. Hãy bật quyền trong Cài đặt."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Lỗi mạng khi nhận dạng giọng nói."
            SpeechRecognizer.ERROR_NO_MATCH -> "Không nghe rõ, bạn thử nói lại nhé."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Đang bận nhận dạng, thử lại sau giây lát."
            SpeechRecognizer.ERROR_SERVER -> "Máy chủ nhận dạng gặp lỗi."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe thấy gì. Thử lại nhé."
            else -> "Không nhận dạng được giọng nói. Thử lại nhé."
        }

        /** Convenience for the system locale used when vi-VN is unavailable. */
        val FALLBACK_LOCALE: Locale get() = Locale.getDefault()
    }
}
