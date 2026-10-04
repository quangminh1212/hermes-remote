package com.hermes.bridge.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Reads assistant replies aloud using the Android system [TextToSpeech] engine.
 *
 * Hermes replies are markdown-heavy and often long, so before speaking we
 * [sanitizeForSpeech] (strip code fences, links, list markers) and cap the
 * spoken text to a prefix — the full reply still shows in the chat. The engine
 * is created lazily and reused; Vietnamese is preferred with an English fallback.
 */
class TtsSpeaker(context: Context) {

    /** Callbacks for speech lifecycle. */
    interface Listener {
        fun onStart()
        fun onDone()
        /** Fired when the requested language is unavailable on this device. */
        fun onLanguageMissing()
    }

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var listener: Listener? = null

    /** True once the engine has initialized successfully. */
    val isReady: Boolean get() = ready

    /** Initialize the engine. Safe to call more than once. */
    fun init(listener: Listener? = null) {
        if (listener != null) this.listener = listener
        if (tts != null) return

        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) configureLanguage()
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    listener?.onStart()
                }

                override fun onDone(utteranceId: String?) {
                    listener?.onDone()
                }

                @Deprecated("Required override for older API levels")
                override fun onError(utteranceId: String?) {
                    listener?.onDone()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    listener?.onDone()
                }
            })
        }
    }

    private fun configureLanguage() {
        val engine = tts ?: return
        val vi = Locale("vi", "VN")
        val result = engine.setLanguage(vi)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fall back to the device default rather than going silent.
            engine.setLanguage(Locale.getDefault())
            listener?.onLanguageMissing()
        }
    }

    /**
     * Speak [text] aloud. Queues after any current utterance.
     * Long replies are truncated to [MAX_SPOKEN_CHARS] for readability.
     */
    fun speak(text: String) {
        val engine = tts
        val clean = sanitizeForSpeech(text)
        if (engine == null || !ready || clean.isBlank()) return
        engine.speak(clean, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
    }

    /** Stop any in-progress speech immediately. */
    fun stop() {
        tts?.stop()
    }

    /** Shut the engine down; the speaker is unusable afterwards. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    companion object {
        const val UTTERANCE_ID = "hermes-reply"

        /** Max characters read aloud; the rest stays on screen for reading. */
        const val MAX_SPOKEN_CHARS = 600

        private const val ELLIPSIS = "…"

        /**
         * Turn a markdown reply into something pleasant to listen to:
         * drop fenced code blocks, unwrap links, remove heading/list markers,
         * collapse whitespace, and cap the length.
         *
         * Pure function — unit tested on the JVM.
         */
        fun sanitizeForSpeech(text: String): String {
            if (text.isBlank()) return ""

            var s = text

            // Remove fenced code blocks entirely (``` ... ```).
            s = CODE_FENCE.replace(s, " ")

            // Inline code `x` -> x
            s = INLINE_CODE.replace(s, "$1")

            // [label](url) -> label ; also bare URLs -> "(liên kết)"
            s = MARKDOWN_LINK.replace(s, "$1")
            s = BARE_URL.replace(s, "liên kết")

            // Headings, blockquotes, list bullets at line starts.
            s = LINE_PREFIX.replace(s, "")

            // Emoji / pictographs read badly aloud — drop them.
            s = EMOJI.replace(s, "")

            // Bold/italic markers.
            s = EMPHASIS.replace(s, "$1")

            // Collapse whitespace.
            s = s.replace(Regex("[ \\t]+"), " ")
            s = s.replace(Regex("\\n{2,}"), "\n")
            s = s.trim()

            if (s.length > MAX_SPOKEN_CHARS) {
                val cut = s.substring(0, MAX_SPOKEN_CHARS)
                val lastStop = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', '。', '！', '？'))
                s = (if (lastStop > MAX_SPOKEN_CHARS / 2) cut.substring(0, lastStop + 1) else cut).trim() + ELLIPSIS
            }
            return s
        }

        private val CODE_FENCE = Regex("```[\\s\\S]*?```")
        private val INLINE_CODE = Regex("`([^`]*)`")
        private val MARKDOWN_LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
        private val BARE_URL = Regex("https?://\\S+")
        private val LINE_PREFIX = Regex("(?m)^\\s*(#{1,6}|>|[-*+]|\\d+[.)])\\s*")
        private val EMOJI = Regex(
            "[\\u2190-\\u21FF\\u2300-\\u27BF\\u2B00-\\u2BFF" +
                "\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\uFE0F\\u200D]",
        )
        private val EMPHASIS = Regex("[*_~]{1,3}([^*_~]+?)[*_~]{1,3}")
    }
}
