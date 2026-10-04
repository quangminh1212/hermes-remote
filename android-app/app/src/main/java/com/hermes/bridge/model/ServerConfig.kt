package com.hermes.bridge.model

/** Connection + identity settings the user configures on the settings sheet. */
data class ServerConfig(
    val baseUrl: String,
    val apiKey: String,
    val profile: String,
    val model: String,
) {
    /** Normalized base URL ending without a trailing slash. */
    val cleanBaseUrl: String get() = baseUrl.trim().trimEnd('/')

    /**
     * Full chat-completions endpoint.
     *
     * We accept two shapes of [cleanBaseUrl] so pasting either works:
     *  - `https://host` or `https://host/p/<profile>` -> append `/v1/...`
     *  - `https://host/v1` or `.../v1/chat/completions` -> normalize to the same target
     */
    val chatEndpoint: String
        get() {
            val base = cleanBaseUrl
            val profileSegment = if (profile.isNotBlank()) "/p/$profile" else ""
            val root = when {
                base.endsWith("/v1/chat/completions") -> base.removeSuffix("/chat/completions")
                base.endsWith("/v1") -> base
                // The user (or a tunnel URL) may already include the /p/<profile> part.
                profileSegment.isNotEmpty() && base.contains(profileSegment) -> "$base/v1"
                else -> "$base$profileSegment/v1"
            }
            return "$root/chat/completions"
        }

    val modelsEndpoint: String get() = chatEndpoint.removeSuffix("/chat/completions") + "/models"

    fun isValid(): Boolean = cleanBaseUrl.startsWith("http") && apiKey.isNotBlank()

    companion object {
        // Empty profile => talk to `/v1` with the top-level API_SERVER_KEY from .env.
        // A named profile (dalek/doraemon/heimeringer) needs its own per-profile
        // API_SERVER_KEY or the gateway fails closed with 401.
        const val DEFAULT_PROFILE = ""
        const val DEFAULT_MODEL = "Claude-Fable.3"
        const val DEFAULT_BASE_URL = "http://192.168.1.113:8642"

        val DEFAULT = ServerConfig(
            baseUrl = DEFAULT_BASE_URL,
            apiKey = "",
            profile = DEFAULT_PROFILE,
            model = DEFAULT_MODEL,
        )

        val KNOWN_PROFILES = listOf("dalek", "doraemon", "heimeringer")
    }
}
