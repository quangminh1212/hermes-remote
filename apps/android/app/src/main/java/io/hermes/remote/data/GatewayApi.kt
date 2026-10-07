package io.hermes.remote.data

import io.hermes.remote.model.GatewayInfo
import io.hermes.remote.model.ServerConfig
import io.hermes.remote.model.SessionSummary
import io.hermes.remote.model.Toolset
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads the gateway's own description of itself.
 *
 * Split from [ChatApi] on purpose: that class owns the conversation, this one
 * only reports state, and mixing "send my words to the model" with "tell me
 * how the PC is doing" would make both harder to reason about.
 *
 * Every call here is best-effort and returns null/empty rather than throwing.
 * A sync failure must never stop the user from sending a message — the chat
 * path stays the priority.
 *
 * @param client shared OkHttp instance; callers pass [ChatApi.defaultClient] so
 *   connection pools and timeouts stay consistent across both APIs.
 */
class GatewayApi(
    private val client: OkHttpClient = ChatApi.defaultClient(),
) {

    /**
     * Fetch the PC's health, toolsets and sessions.
     *
     * Returns null when the server cannot be reached at all, so the caller can
     * distinguish "PC is down" from "PC answered but has no sessions".
     */
    fun fetchInfo(config: ServerConfig): GatewayInfo? {
        val health = getJson(config, config.healthEndpoint) ?: return null
        val parsed = HealthParser.parse(health)
        return parsed.copy(
            toolsets = getJson(config, config.toolsetsEndpoint)?.let(ToolsetParser::parse).orEmpty(),
            sessions = getJson(config, config.sessionsEndpoint)?.let(SessionParser::parse).orEmpty(),
        )
    }

    /**
     * `GET` a JSON document, or null on any failure.
     *
     * A 404 on the optional endpoints is expected against an older gateway and
     * is not an error worth surfacing; the caller simply shows less.
     */
    private fun getJson(config: ServerConfig, url: String): String? {
        if (url.isBlank()) return null
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            // Malformed URL from a hand-typed base URL: treat as "no data".
            null
        }
    }
}

/**
 * Parsers for `/health/detailed`, `/v1/toolsets` and `/api/sessions`.
 *
 * Hand-written rather than reflection-based, matching [ChatApi.parseModelIds]:
 * the app ships without a JSON reflection dependency and these payloads have a
 * stable, small shape.
 */
internal object HealthParser {

    fun parse(json: String): GatewayInfo {
        val status = JsonScan.stringField(json, "status") ?: ""
        val version = JsonScan.stringField(json, "version") ?: ""
        return GatewayInfo(
            status = status,
            version = version,
            checks = parseReadinessChecks(json),
            platforms = parsePlatformStates(json),
            metrics = parseSubsystemMetrics(json),
        )
    }

    /**
     * Pull `readiness.checks`: `{"checks":{"state_db":{"status":"ok"},…}}`.
     *
     * Scoped to the object *inside* `readiness` on purpose. The payload has
     * other objects carrying a `status` key (readiness itself, each platform),
     * and scanning the whole document would attribute their values to
     * subsystems that report something else.
     */
    private fun parseReadinessChecks(json: String): Map<String, String> {
        val readiness = json.indexOf("\"readiness\"")
        if (readiness < 0) return emptyMap()
        val checksKey = json.indexOf("\"checks\"", readiness)
        if (checksKey < 0) return emptyMap()
        val open = json.indexOf('{', checksKey)
        if (open < 0) return emptyMap()
        val close = JsonScan.matchingBrace(json, open) ?: return emptyMap()
        return parseStatusMap(json.substring(open + 1, close))
    }

    /**
     * Pull `platforms`: `{"whatsapp":{"state":"fatal"},…}`.
     *
     * Platforms report `state`, not `status` — reading the wrong key here is
     * exactly the kind of mistake that makes a feature silently useless.
     *
     * The key appears twice in the real payload: once as a *number* under
     * `readiness.checks.gateway` ("platforms": 4, how many are connected) and
     * once as the object at the top level. Taking the first match picks the
     * counter and then scans forward into an unrelated object, so this walks
     * every occurrence and keeps the one whose value is actually an object.
     */
    private fun parsePlatformStates(json: String): Map<String, String> {
        var from = 0
        while (true) {
            val key = json.indexOf("\"platforms\"", from)
            if (key < 0) return emptyMap()
            var i = key + "\"platforms\"".length
            while (i < json.length && json[i].isWhitespace()) i++
            if (i < json.length && json[i] == ':') {
                i++
                while (i < json.length && json[i].isWhitespace()) i++
                if (i < json.length && json[i] == '{') {
                    val close = JsonScan.matchingBrace(json, i)
                    if (close != null) {
                        val parsed = parseStatusMap(json.substring(i + 1, close), field = "state")
                        // Only accept a map that actually yielded platforms.
                        if (parsed.isNotEmpty()) return parsed
                    }
                }
            }
            from = key + 1
        }
    }

    /**
     * The per-subsystem numbers worth showing: disk usage.
     *
     * Kept deliberately tiny. The full payload has dozens of counters that
     * would be noise on a phone; disk is the one with a natural "this is
     * nearly full" threshold the user can act on.
     */
    private fun parseSubsystemMetrics(json: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (field in METRIC_FIELDS) {
            JsonScan.numberField(json, field)?.let { out[field] = it.toString() }
        }
        return out
    }

    private val METRIC_FIELDS = listOf("used_percent", "free_bytes")

    /**
     * Parse `"<name>": { … "<field>": "<value>" … }` pairs.
     *
     * [field] is `status` for readiness checks and `state` for platforms.
     */
    private fun parseStatusMap(body: String, field: String = "status"): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var i = 0
        while (true) {
            val nameStart = body.indexOf('"', i)
            if (nameStart < 0) break
            val nameEnd = body.indexOf('"', nameStart + 1)
            if (nameEnd < 0) break
            val name = body.substring(nameStart + 1, nameEnd)

            val objStart = body.indexOf('{', nameEnd)
            if (objStart < 0) break
            val objEnd = JsonScan.matchingBrace(body, objStart) ?: break
            val obj = body.substring(objStart, objEnd + 1)
            JsonScan.stringField(obj, field)?.let { out[name] = it }

            i = objEnd + 1
        }
        return out
    }
}

/** Parses `/v1/toolsets` into [Toolset]s. */
internal object ToolsetParser {

    fun parse(json: String): List<Toolset> {
        val out = mutableListOf<Toolset>()
        // Each entry starts with a "name" field; walk object by object.
        for (obj in JsonScan.objectsInArray(json, "data")) {
            val name = JsonScan.stringField(obj, "name") ?: continue
            out += Toolset(
                name = name,
                label = JsonScan.stringField(obj, "label") ?: "",
                description = JsonScan.stringField(obj, "description") ?: "",
                enabled = JsonScan.boolField(obj, "enabled") ?: false,
                tools = JsonScan.stringArrayField(obj, "tools"),
            )
        }
        return out
    }
}

/** Parses `/api/sessions` into [SessionSummary]s, newest first. */
internal object SessionParser {

    fun parse(json: String): List<SessionSummary> {
        val out = mutableListOf<SessionSummary>()
        for (obj in JsonScan.objectsInArray(json, "data")) {
            val id = JsonScan.stringField(obj, "id") ?: continue
            out += SessionSummary(
                id = id,
                title = JsonScan.stringField(obj, "title") ?: "",
                model = JsonScan.stringField(obj, "model") ?: "",
                startedAt = JsonScan.numberField(obj, "started_at") ?: 0.0,
                endedAt = when {
                    obj.contains("\"ended_at\"") -> JsonScan.numberField(obj, "ended_at")
                    else -> null
                },
            )
        }
        // The gateway usually sorts already; sorting here keeps the UI stable
        // if it does not.
        return out.sortedByDescending { it.startedAt }
    }
}
