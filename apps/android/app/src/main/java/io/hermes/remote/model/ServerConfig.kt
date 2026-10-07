package io.hermes.remote.model

/**
 * Connection + identity settings the user configures on the settings sheet.
 *
 * [model] doubles as the agent selector when talking to the agent bridge: the
 * bridge routes on the `model` field of the request, so choosing "codex" there
 * is what makes Codex answer. [agents] is the list the PC reported, kept so the
 * picker can offer it without probing; it is empty when the bridge is off or
 * when an older QR payload had no such field.
 */
data class ServerConfig(
    val baseUrl: String,
    val apiKey: String,
    val profile: String,
    val model: String,
    /** Agent ids the PC's bridge can answer with; empty when unknown. */
    val agents: List<String> = emptyList(),
    /** The agent the PC picks when a request names none. */
    val defaultAgent: String = "",
) {
    /** True when the PC offered a choice of agent CLIs. */
    val hasAgents: Boolean get() = agents.isNotEmpty()

    /**
     * The model value that will actually be sent.
     *
     * When the PC reported agents, the configured [model] is only valid if it
     * names one of them — otherwise the bridge answers 404 `unknown agent`.
     * A freshly-paired phone used to hit exactly that: the app's default model
     * is the Hermes gateway's `Claude-Fable.3`, which is not an agent id, so the
     * first message failed. Falling back to the PC's [defaultAgent], then to the
     * first reported agent, keeps the request valid without user intervention.
     *
     * When no agents were reported (bridge off, or an older payload) the
     * configured model is passed through untouched, because then it really is a
     * gateway model name.
     */
    val effectiveModel: String
        get() {
            if (!hasAgents) return model
            val wanted = model.trim().lowercase()
            agents.firstOrNull { it.trim().lowercase() == wanted }?.let { return it }
            defaultAgent.trim().takeIf { it.isNotEmpty() }?.let { d ->
                agents.firstOrNull { it.trim().lowercase() == d.lowercase() }?.let { return it }
            }
            return agents.first()
        }

    /** True when [model] had to be redirected to a valid agent id. */
    val modelWasRetargeted: Boolean
        get() = hasAgents && effectiveModel.trim().lowercase() != model.trim().lowercase()

    /**
     * The pickable agent ids: [agents] narrowed to real, unique, non-blank ids,
     * with the effective model first so the current selection is at the top.
     *
     * Empty when the PC reported no agents — the UI then shows no picker at all,
     * rather than offering the gateway's model name as if it were a CLI.
     *
     * The payload comes from the PC and could repeat an id or send blanks; the
     * picker must not show duplicate or empty rows. Ids are lower-cased because
     * the bridge matches them case-insensitively.
     */
    val agentChoices: List<String>
        get() {
            if (!hasAgents) return emptyList()
            val seen = LinkedHashSet<String>()
            for (a in listOf(effectiveModel) + agents) {
                val id = a.trim().lowercase()
                if (id.isNotEmpty()) seen += id
            }
            return seen.toList()
        }

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

    /**
     * Root the non-`/v1` routes hang off, e.g. `https://host` or `host/p/dalek`.
     *
     * `/health`, `/v1/toolsets` and `/api/sessions` are not all under the same
     * prefix: health and sessions sit above `/v1`, so deriving them from
     * [chatEndpoint] would produce `.../v1/health` and 404. This strips back to
     * the server root instead.
     */
    private val serverRoot: String
        get() {
            val base = cleanBaseUrl
            if (base.endsWith("/v1/chat/completions")) return base.removeSuffix("/v1/chat/completions")
            if (base.endsWith("/v1")) return base.removeSuffix("/v1")
            val profileSegment = if (profile.isNotBlank()) "/p/$profile" else ""
            // Keep an explicit /p/<profile> the user pasted, since the gateway
            // scopes health and sessions to the profile too.
            return if (profileSegment.isNotEmpty() && base.contains(profileSegment)) base else "$base$profileSegment"
        }

    /** `GET /health` — cheap liveness probe, no auth required by the gateway. */
    val healthEndpoint: String get() = "$serverRoot/health/detailed"

    /** `GET /v1/toolsets` — capabilities the PC offers. */
    val toolsetsEndpoint: String get() = "$serverRoot/v1/toolsets"

    /** `GET /api/sessions` — past conversations on the PC. */
    val sessionsEndpoint: String get() = "$serverRoot/api/sessions"

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
