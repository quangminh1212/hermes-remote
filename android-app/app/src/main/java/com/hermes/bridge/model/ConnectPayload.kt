package com.hermes.bridge.model

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
 *   "profiles": ["dalek","doraemon"]
 * }
 */
data class ConnectPayload(
    @SerializedName("v") val version: Int = 1,
    @SerializedName("baseUrl") val baseUrl: String = "",
    @SerializedName("fallbackUrl") val fallbackUrl: String = "",
    @SerializedName("apiKey") val apiKey: String = "",
    @SerializedName("profile") val profile: String = "",
    @SerializedName("model") val model: String = "",
    @SerializedName("profiles") val profiles: List<String> = emptyList()
) {
    /** True when this payload has the minimum needed to connect. */
    fun isUsable(): Boolean = baseUrl.isNotBlank() && apiKey.isNotBlank()

    /** Fold this payload into a ServerConfig. */
    fun toServerConfig(): ServerConfig = ServerConfig(
        baseUrl = baseUrl,
        apiKey = apiKey,
        profile = profile,
        model = model
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
