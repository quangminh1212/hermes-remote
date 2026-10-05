package com.hermes.bridge.voice

import android.content.Intent

/**
 * Parses the query out of an intent handed over by Google Assistant / an
 * App Action.
 *
 * Kept as an object of pure functions over plain maps so the parsing rules are
 * unit-testable on the JVM; [fromIntent] is the only Android-aware entry point.
 *
 * ## Why so many shapes
 * Google's tooling is not consistent about where the user's words land:
 *
 * - **Custom capability** (what `res/xml/shortcuts.xml` declares here): the
 *   text arrives in the `query` extra.
 * - **Built-in intent** (`actions.intent.*`): parameters are nested in a
 *   `Bundle` under `com.google.assistant.extra.parameters`.
 * - **Assistant / other callers**: `android.intent.extra.TEXT` is used.
 * - **Deep link**: `hermes://ask?query=...`.
 *
 * All of them are accepted rather than betting on one.
 */
object AssistantIntent {

    /** Extras that may carry the prompt text, in priority order. */
    val QUERY_EXTRA_KEYS = listOf(
        "query",
        Intent.EXTRA_TEXT,
        "queryText",
        "text",
        "prompt",
    )

    /** Keys under which a built-in-intent delivery nests its parameters. */
    val PARAM_BUNDLE_KEYS = listOf(
        "com.google.assistant.extra.parameters",
        "parameters",
        "extras",
    )

    /**
     * Leading app invocation the Assistant may leave prefixed on the text,
     * e.g. "Hermes, cho tôi biết …", "ask Hermes to …", "hỏi Hermes …".
     *
     * The product name is alternated longest-first ("Hermes Remote" before
     * "Hermes"), so a two-word name is not truncated to one word — which would
     * leave the stray remainder "Remote, chạy test" as the prompt.
     */
    private val INVOCATION_PREFIX = Regex(
        "^(?:(?:hey|ok|okay|ask|tell|open|launch|speak\\s+to|mở|hỏi|nhắn|nói\\s+với)\\s+)*" +
            "(?:hermes\\s+remote|hermes)\\b[\\s,:;.!\\-]*",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Normalise raw Assistant text into a prompt.
     *
     * Strips the trailing app invocation so the PC receives the user's actual
     * request. Returns null when nothing usable is left — a bare "Hermes" is not
     * a prompt, and sending it would waste a round trip and speak a nonsense
     * reply.
     */
    fun clean(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val stripped = INVOCATION_PREFIX.replace(text, "").trim()
        if (stripped.isEmpty()) return null
        if (stripped.equals("hermes", ignoreCase = true)) return null
        return stripped
    }

    /**
     * Resolve a prompt from already-extracted extras.
     *
     * [strings] holds the flat string extras; [bundles] maps a bundle key to
     * that bundle's string extras. Split this way so tests need no Android.
     */
    fun parse(
        strings: Map<String, String?>,
        bundles: Map<String, Map<String, String?>> = emptyMap(),
        deepLinkQuery: String? = null,
    ): String? {
        for (key in QUERY_EXTRA_KEYS) {
            clean(strings[key])?.let { return it }
        }

        for (bundleKey in PARAM_BUNDLE_KEYS) {
            val bundle = bundles[bundleKey] ?: continue
            if (bundle.isEmpty()) continue
            for (key in QUERY_EXTRA_KEYS) {
                clean(bundle[key])?.let { return it }
            }
            // Some deliveries use the literal key "feature" for the text.
            clean(bundle["feature"])?.let { return it }
        }

        return clean(deepLinkQuery)
    }

    /** Android-aware entry point: resolve a prompt from a delivered [Intent]. */
    fun fromIntent(intent: Intent?): String? {
        intent ?: return null

        val strings = buildMap {
            for (key in QUERY_EXTRA_KEYS) put(key, intent.getStringExtra(key))
        }

        val bundles = buildMap {
            for (key in PARAM_BUNDLE_KEYS) {
                @Suppress("DEPRECATION")
                val bundle = intent.getBundleExtra(key) ?: continue
                val inner = buildMap {
                    for (innerKey in QUERY_EXTRA_KEYS + "feature") {
                        put(innerKey, bundle.getString(innerKey))
                    }
                }
                put(key, inner)
            }
        }

        return parse(strings, bundles, intent.data?.getQueryParameter("query"))
    }
}
