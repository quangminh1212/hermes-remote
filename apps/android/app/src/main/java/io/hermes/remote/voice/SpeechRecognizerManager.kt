package io.hermes.remote.voice

import io.hermes.remote.R

import android.content.ComponentName
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
        // Never let a misbehaving recognizer take the process down. A device
        // whose default recognition service is unexported throws
        // SecurityException right here; the user should see an error message,
        // not a crash that kills the whole app.
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            listening = false
            listener?.onError(context.getString(R.string.voice_err_unknown))
            listener?.onEnded()
        }
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

    /**
     * Build the platform recognizer, preferring a known-good implementation.
     *
     * The bare `createSpeechRecognizer(context)` follows the device's *default*
     * recognition service, which any app can claim. On the test Note 8 a
     * third-party screenshot app had claimed it, and its service is not
     * exported — so `startListening` threw a SecurityException and killed the
     * process. Naming Google's recognizer explicitly sidesteps that, and
     * falling back to the default keeps working on devices that have no Google
     * recognizer at all.
     */
    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer = createPreferredRecognizer().apply {
            setRecognitionListener(platformListener)
        }
    }

    /** Google's recognizer when present, otherwise whatever the device prefers. */
    private fun createPreferredRecognizer(): SpeechRecognizer {
        for (component in PREFERRED_RECOGNIZERS) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .setComponent(component)
            if (context.packageManager.queryIntentServices(intent, 0).isNotEmpty()) {
                return SpeechRecognizer.createSpeechRecognizer(context, component)
            }
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
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
                listener?.onError(context.getString(R.string.voice_err_not_heard))
            } else {
                listener?.onFinal(text)
            }
            listener?.onEnded()
        }

        override fun onError(error: Int) {
            listening = false
            listener?.onError(context.getString(messageResForError(error)))
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
        /**
         * Language to recognize.
         *
         * Follows the device locale rather than being pinned to one language, so
         * a phone set to Chinese or English transcribes in that language. The
         * platform falls back to its own default if the exact locale has no
         * recognizer.
         */
        val DEFAULT_LANGUAGE: String get() = Locale.getDefault().toLanguageTag()

        /**
         * Recognizers to try before falling back to the device default.
         *
         * Google's is listed first because it is the one that actually works on
         * the devices this app targets; a device whose *default* recognizer is
         * a broken third-party service is a real, observed failure mode (see
         * [ensureRecognizer]).
         */
        private val PREFERRED_RECOGNIZERS: List<ComponentName> = listOf(
            ComponentName(
                "com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService",
            ),
        )

        /** Whether the device has any recognition service at all. */
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)

        /**
         * Map a [SpeechRecognizer] error code to a resource id.
         *
         * Returns an id rather than text so the caller decides the language;
         * the platform error codes carry no wording of their own.
         */
        @androidx.annotation.StringRes
        fun messageResForError(error: Int): Int = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> R.string.voice_err_audio
            SpeechRecognizer.ERROR_CLIENT -> R.string.voice_err_client
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_err_permission
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_err_network_timeout
            SpeechRecognizer.ERROR_NO_MATCH -> R.string.voice_err_no_match
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_err_busy
            SpeechRecognizer.ERROR_SERVER -> R.string.voice_err_server
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_err_speech_timeout
            else -> R.string.voice_err_unknown
        }

        /** Convenience for the system locale used as a recognition fallback. */
        val FALLBACK_LOCALE: Locale get() = Locale.getDefault()
    }
}
