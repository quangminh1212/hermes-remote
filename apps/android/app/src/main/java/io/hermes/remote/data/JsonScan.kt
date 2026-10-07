package io.hermes.remote.data

/**
 * Minimal, hand-written JSON scanning helpers.
 *
 * The app deliberately avoids a reflective JSON binder for these payloads: the
 * shapes are small and stable, and a scanner has no build-time cost and no
 * surprise when the gateway adds a field. [ChatApi] already parses model ids
 * this way; this collects the pieces so [GatewayApi] does not grow its own
 * copy of each one.
 *
 * Every function is total: a malformed document yields null or an empty list
 * rather than throwing, because a gateway that changes shape should degrade the
 * information shown, not crash the app.
 */
internal object JsonScan {

    /**
     * The string value of [field] anywhere in [json], or null.
     *
     * Finds the first `"field"` followed by a string value. Sufficient because
     * the fields read here are unique within their payload.
     */
    fun stringField(json: String, field: String): String? {
        val key = "\"$field\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            var i = at + key.length
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != ':') {
                from = at + key.length
                continue
            }
            i++
            while (i < json.length && json[i].isWhitespace()) i++
            // A non-string value (object, array, number) is not what we want:
            // keep looking in case the same key name appears as a real string.
            if (i < json.length && json[i] == '"') return readString(json, i)
            from = at + key.length
        }
    }

    /** The numeric value of [field], or null when absent/not a number. */
    fun numberField(json: String, field: String): Double? {
        val key = "\"$field\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            var i = at + key.length
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != ':') {
                from = at + key.length
                continue
            }
            i++
            while (i < json.length && json[i].isWhitespace()) i++
            // `null` is a valid value for fields like ended_at.
            if (json.startsWith("null", i)) return null
            val start = i
            while (i < json.length && (json[i].isDigit() || json[i] in "-+.eE")) i++
            return json.substring(start, i).toDoubleOrNull()
        }
    }

    /** The boolean value of [field], or null when absent. */
    fun boolField(json: String, field: String): Boolean? {
        val key = "\"$field\""
        val at = json.indexOf(key)
        if (at < 0) return null
        var i = at + key.length
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != ':') return null
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        return when {
            json.startsWith("true", i) -> true
            json.startsWith("false", i) -> false
            else -> null
        }
    }

    /** Every string in the array value of [field], or an empty list. */
    fun stringArrayField(json: String, field: String): List<String> {
        val key = "\"$field\""
        val at = json.indexOf(key)
        if (at < 0) return emptyList()
        val open = json.indexOf('[', at)
        if (open < 0) return emptyList()
        val close = matchingBracket(json, open) ?: return emptyList()
        val body = json.substring(open + 1, close)

        val out = mutableListOf<String>()
        var i = 0
        while (i < body.length) {
            if (body[i] == '"') {
                out += readString(body, i)
                i = skipString(body, i)
            } else {
                i++
            }
        }
        return out
    }

    /**
     * Every top-level object inside the array named [field].
     *
     * Used to walk list payloads (`{"data":[{...},{...}]}`) without building a
     * full parser: each returned string is one element's JSON source.
     */
    fun objectsInArray(json: String, field: String): List<String> {
        val key = "\"$field\""
        val at = json.indexOf(key)
        if (at < 0) return emptyList()
        val open = json.indexOf('[', at)
        if (open < 0) return emptyList()
        val close = matchingBracket(json, open) ?: return emptyList()
        val body = json.substring(open + 1, close)

        val out = mutableListOf<String>()
        var i = 0
        while (i < body.length) {
            when (body[i]) {
                '{' -> {
                    val end = matchingBrace(body, i) ?: break
                    out += body.substring(i, end + 1)
                    i = end + 1
                }
                '"' -> i = skipString(body, i)
                else -> i++
            }
        }
        return out
    }

    /** Index of the `}` matching the `{` at [open], or null. */
    fun matchingBrace(json: String, open: Int): Int? = match(json, open, '{', '}')

    /** Index of the `]` matching the `[` at [open], or null. */
    private fun matchingBracket(json: String, open: Int): Int? = match(json, open, '[', ']')

    /**
     * Depth-count the delimiters from [open], ignoring any inside strings.
     *
     * String-awareness matters: a title containing `}` would otherwise close
     * the object early and corrupt every field after it.
     */
    private fun match(json: String, open: Int, openChar: Char, closeChar: Char): Int? {
        if (open >= json.length || json[open] != openChar) return null
        var depth = 0
        var i = open
        while (i < json.length) {
            when (json[i]) {
                '"' -> i = skipString(json, i)
                openChar -> { depth++; i++ }
                closeChar -> {
                    depth--
                    if (depth == 0) return i
                    i++
                }
                else -> i++
            }
        }
        return null
    }

    /** Index just past the string literal starting at [start] (a quote). */
    private fun skipString(json: String, start: Int): Int {
        var i = start + 1
        while (i < json.length) {
            when (json[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        return i
    }

    /** Read the string literal starting at [start] (a quote), unescaping. */
    private fun readString(json: String, start: Int): String {
        val sb = StringBuilder()
        var i = start + 1
        while (i < json.length && json[i] != '"') {
            if (json[i] == '\\' && i + 1 < json.length) {
                i++
                when (val esc = json[i]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        // \uXXXX — decode when the four hex digits are present.
                        if (i + 4 < json.length) {
                            val hex = json.substring(i + 1, i + 5)
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()); i += 4 }
                                ?: sb.append(esc)
                        } else {
                            sb.append(esc)
                        }
                    }
                    else -> sb.append(esc)
                }
            } else {
                sb.append(json[i])
            }
            i++
        }
        return sb.toString()
    }
}
