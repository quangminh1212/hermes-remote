package io.hermes.remote.model

/**
 * A snapshot of what the Hermes gateway on the PC reports about itself.
 *
 * The app used to know only two things about the PC: a list of model ids and
 * whether a chat request succeeded. That is enough to send a prompt, but it
 * leaves the user guessing when something is wrong on the other side — an
 * unreachable host, a full disk, a stopped platform all looked identical.
 *
 * Everything here comes from endpoints the gateway already exposes
 * (`/health/detailed`, `/v1/toolsets`, `/api/sessions`); nothing is invented
 * client-side, so the app and the PC cannot drift apart.
 */
data class GatewayInfo(
    /** `/health/detailed`: "ok", "degraded", … */
    val status: String = "",
    /** `/health/detailed`: the Hermes version running on the PC. */
    val version: String = "",
    /** `/health/detailed`: per-subsystem status, e.g. "disk" -> "degraded". */
    val checks: Map<String, String> = emptyMap(),
    /**
     * `/health/detailed`: health of each connected platform, e.g.
     * "whatsapp" -> "fatal".
     *
     * The gateway reports platforms it could not connect, and that is exactly
     * the kind of thing the user would otherwise never learn from the phone —
     * the chat still works while, say, WhatsApp is down on the PC.
     */
    val platforms: Map<String, String> = emptyMap(),
    /** `/health/detailed`: extra numbers for the subsystems, e.g. disk usage. */
    val metrics: Map<String, String> = emptyMap(),
    /** `/v1/toolsets`: capabilities the PC currently offers. */
    val toolsets: List<Toolset> = emptyList(),
    /**
     * `/api/sessions`: past conversations, newest first.
     *
     * Only ids and titles are kept: the transcript of a session is fetched on
     * demand, so opening the app does not pull every past conversation.
     */
    val sessions: List<SessionSummary> = emptyList(),
) {
    /**
     * True when the PC answered `/health` but flagged a problem.
     *
     * "degraded" is the gateway's own word for "serving, but something needs
     * attention" — worth surfacing rather than hiding behind a green dot.
     */
    val isDegraded: Boolean get() = status.equals("degraded", ignoreCase = true)

    /** Subsystems the gateway marked as not ok, for a short warning line. */
    val problems: List<String>
        get() = checks.filterValues { !it.equals("ok", ignoreCase = true) }.keys.toList()

    /** Platforms that failed to connect on the PC, for a warning line. */
    val brokenPlatforms: List<String>
        get() = platforms.filterValues { it.equals("fatal", ignoreCase = true) }.keys.toList()

    /** Free disk space in GB, when the gateway reported it. */
    val freeDiskGb: Double?
        get() = metrics["free_bytes"]?.toDoubleOrNull()?.div(1_073_741_824.0)

    /** Disk used percentage, when reported. */
    val diskUsedPercent: Double? get() = metrics["used_percent"]?.toDoubleOrNull()
}

/** One capability group the PC exposes, e.g. "web" or "terminal". */
data class Toolset(
    val name: String,
    val label: String = "",
    val description: String = "",
    val enabled: Boolean = false,
    /** Individual tool names inside this set, e.g. ["web_search", "web_extract"]. */
    val tools: List<String> = emptyList(),
)

/** A past conversation on the PC, as listed by `/api/sessions`. */
data class SessionSummary(
    val id: String,
    val title: String = "",
    val model: String = "",
    val startedAt: Double = 0.0,
    val endedAt: Double? = null,
) {
    /** True while the PC still has this session open. */
    val isLive: Boolean get() = endedAt == null
}
