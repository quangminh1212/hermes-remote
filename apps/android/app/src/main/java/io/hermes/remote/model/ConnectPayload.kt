package io.hermes.remote.model

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

/**
 * The JSON payload embedded in the QR code produced by the `hermes-remote`
 * npm package. Scanning it lets the app configure itself in one step.
 *
 * Example:
 * {
 *   "v": 1,
 *   "baseUrl": "https://xxx.trycloudflare.com",
 *   "fallbackUrl": "http://192.168.1.113:8642",
 *   "apiKey": "…",
 *   "profile": "",
 *   "model": "Claude-Fable.3",
 *   "profiles": ["dalek","doraemon"],
 *   "agents": ["claude","codex","grok"],
 *   "defaultAgent": "claude"
 * }
 */
data class ConnectPayload(
    @SerializedName("v") val version: Int = 1,
    @SerializedName("baseUrl") val baseUrl: String = "",
    @SerializedName("fallbackUrl") val fallbackUrl: String = "",
    @SerializedName("apiKey") val apiKey: String = "",
    @SerializedName("profile") val profile: String = "",
    @SerializedName("model") val model: String = "",
    @SerializedName("profiles") val profiles: List<String> = emptyList(),
    /**
     * Agent ids the PC's agent bridge can answer with (claude, codex, …); empty
     * when the bridge is off. Sending one of these as the model selects which
     * CLI replies.
     */
    @SerializedName("agents") val agents: List<String> = emptyList(),
    /** The agent the PC picks when a request names none. */
    @SerializedName("defaultAgent") val defaultAgent: String = ""
) {
    /** True when this payload has the minimum needed to connect. */
    fun isUsable(): Boolean = baseUrl.isNotBlank() && apiKey.isNotBlank()

    /**
     * Fold this payload into a ServerConfig.
     *
     * [agents] and [defaultAgent] must travel with the rest: they are what lets
     * the app offer an agent picker. Dropping them here was a real bug — the
     * payload was parsed with the agent list and the list was then discarded, so
     * the phone could only ever send the plain Hermes model and the user had no
     * way to reach Codex or Grok.
     *
     * When the PC named a default agent and the payload carries no model, the
     * default becomes the model, so the first request already goes to the right
     * CLI instead of falling through to the gateway's own model.
     */
    fun toServerConfig(): ServerConfig = ServerConfig(
        baseUrl = baseUrl,
        apiKey = apiKey,
        profile = profile,
        model = model.ifBlank { defaultAgent },
        agents = agents,
        defaultAgent = defaultAgent,
    )

    companion object {
        private val gson = Gson()

        /**
         * Parse a scanned QR string. Returns null when the text is not a valid
         * hermes-remote connect payload (e.g. some unrelated QR code).
         */
        fun parse(text: String?): ConnectPayload? {
            if (text.isNullOrBlank()) return null
            return try {
                val p = gson.fromJson(text, ConnectPayload::class.java) ?: return null
                if (p.isUsable()) p else null
            } catch (_: JsonSyntaxException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }
}
