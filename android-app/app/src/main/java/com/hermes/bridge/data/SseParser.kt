package com.hermes.bridge.data

/**
 * Incremental parser for the Claude-Fable/Claude-compatible SSE stream returned by
 * `POST /v1/chat/completions` with `"stream": true`.
 *
 * The body arrives as lines of the form:
 * ```
 * data: {"choices":[{"delta":{"content":"Hel"}}]}
 * data: {"choices":[{"delta":{"content":"lo"}}]}
 * data: [DONE]
 * ```
 *
 * [feed] can be called with arbitrary chunk boundaries; it buffers partial
 * lines internally and returns every complete text delta decoded so far.
 * This class is platform-independent so it can be tested on the JVM.
 */
class SseParser {

    private val buffer = StringBuilder()

    /** True once a `[DONE]` sentinel has been seen. */
    var isDone: Boolean = false
        private set

    /**
     * Feed a raw chunk of the response body.
     * @return the list of text deltas completed by this chunk (may be empty).
     */
    fun feed(chunk: String): List<String> {
        buffer.append(chunk)
        val out = mutableListOf<String>()
        while (true) {
            val nl = buffer.indexOf("\n")
            if (nl < 0) break
            val rawLine = buffer.substring(0, nl)
            buffer.delete(0, nl + 1)
            out += handleLine(rawLine.trimEnd('\r'))
        }
        return out
    }

    private fun handleLine(line: String): List<String> {
        if (line.isEmpty()) return emptyList()
        // Ignore SSE comments / event: lines / id: lines — we only read data payloads.
        if (!line.startsWith("data:")) return emptyList()
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty()) return emptyList()
        if (payload == "[DONE]") {
            isDone = true
            return emptyList()
        }
        return extractDeltas(payload)
    }

    companion object {
        /**
         * Extract content deltas from a single SSE data payload.
         *
         * Prefers `choices[].delta.content` (streaming). If absent, falls back to
         * `choices[].message.content` (some servers send a whole message at once),
         * then to a bare top-level `text` field. Returns at most one delta per
         * payload so a fallback never duplicates the streaming field.
         *
         * We avoid a JSON dependency here to keep the parser testable and
         * dependency-free; the payloads are small and well-formed.
         */
        fun extractDeltas(json: String): List<String> {
            val deltaContent = readStringField(json, "content")
            val plainText = readStringField(json, "text")
            // `message.content` is nested inside an object; read the inner content
            // that follows the "message" key rather than treating it as a string.
            val messageContent = readContentAfter(json, "message")

            val chosen = deltaContent ?: messageContent ?: plainText
            return if (chosen.isNullOrEmpty()) emptyList() else listOf(chosen)
        }

        /**
         * Read the `"content"` string that appears *after* the given outer key,
         * used for `{"message":{"content":"..."}}` where the outer value is an object.
         */
        private fun readContentAfter(json: String, outerKey: String): String? {
            val at = json.indexOf("\"$outerKey\"")
            if (at < 0) return null
            return readStringField(json.substring(at), "content")
        }

        /** Read the first `"<field>":"..."` string value at any depth. */
        fun readStringField(json: String, field: String): String? {
            val key = "\"$field\""
            val at = json.indexOf(key)
            if (at < 0) return null
            val colon = json.indexOf(':', at + key.length)
            if (colon < 0) return null
            return readJsonString(json, colon + 1)
        }

        /** Read every `"<field>":"..."` occurrence (covers `role` then `content`). */
        fun readAllStringFields(json: String, field: String): List<String> {
            val key = "\"$field\""
            val results = mutableListOf<String>()
            var from = 0
            while (true) {
                val at = json.indexOf(key, from)
                if (at < 0) break
                val colon = json.indexOf(':', at + key.length)
                if (colon < 0) break
                val value = readJsonString(json, colon + 1)
                if (value != null) results += value
                from = colon + 1
            }
            return results
        }

        /** Parse a JSON string literal beginning at or after [start]. */
        private fun readJsonString(json: String, start: Int): String? {
            var i = start
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != '"') return null
            i++
            val sb = StringBuilder()
            while (i < json.length) {
                val c = json[i]
                when {
                    c == '\\' && i + 1 < json.length -> {
                        when (val esc = json[i + 1]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'u' -> {
                                if (i + 5 < json.length) {
                                    val hex = json.substring(i + 2, i + 6)
                                    hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                                    i += 4
                                }
                            }
                            else -> sb.append(esc)
                        }
                        i += 2
                    }
                    c == '"' -> return sb.toString()
                    else -> {
                        sb.append(c)
                        i++
                    }
                }
            }
            return sb.toString()
        }
    }
}
