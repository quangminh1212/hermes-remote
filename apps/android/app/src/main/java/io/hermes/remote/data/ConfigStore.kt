package io.hermes.remote.data

import android.content.Context
import io.hermes.remote.model.ServerConfig
import io.hermes.remote.ui.theme.ThemeMode

/** Persists [ServerConfig] in SharedPreferences. */
class ConfigStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): ServerConfig = ServerConfig(
        baseUrl = prefs.getString(KEY_URL, ServerConfig.DEFAULT_BASE_URL)
            ?: ServerConfig.DEFAULT_BASE_URL,
        apiKey = prefs.getString(KEY_KEY, "").orEmpty(),
        profile = prefs.getString(KEY_PROFILE, ServerConfig.DEFAULT_PROFILE)
            ?: ServerConfig.DEFAULT_PROFILE,
        model = prefs.getString(KEY_MODEL, ServerConfig.DEFAULT_MODEL)
            ?: ServerConfig.DEFAULT_MODEL,
        // Stored comma-separated: agent ids are simple lower-case tokens with no
        // commas, so a JSON dependency is not needed just for this list.
        agents = prefs.getString(KEY_AGENTS, "").orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() },
        defaultAgent = prefs.getString(KEY_DEFAULT_AGENT, "").orEmpty(),
    )

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_URL, config.baseUrl)
            .putString(KEY_KEY, config.apiKey)
            .putString(KEY_PROFILE, config.profile)
            .putString(KEY_MODEL, config.model)
            .putString(KEY_AGENTS, config.agents.joinToString(","))
            .putString(KEY_DEFAULT_AGENT, config.defaultAgent)
            .apply()
    }

    /** Record only the agent list, leaving the rest of the config untouched. */
    fun saveAgents(agents: List<String>, defaultAgent: String) {
        prefs.edit()
            .putString(KEY_AGENTS, agents.joinToString(","))
            .putString(KEY_DEFAULT_AGENT, defaultAgent)
            .apply()
    }

    // --- Voice settings (kept alongside the server config) -------------------

    /** Whether assistant replies should be read aloud. */
    fun isSpeakRepliesEnabled(): Boolean = prefs.getBoolean(KEY_SPEAK_REPLIES, true)

    fun setSpeakRepliesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPEAK_REPLIES, enabled).apply()
    }

    /**
     * The colour mode the user picked.
     *
     * Stored by enum name so a future reordering of [ThemeMode] cannot silently
     * change what an existing install means.
     */
    fun loadThemeMode(): ThemeMode =
        ThemeMode.fromId(prefs.getString(KEY_THEME_MODE, null))

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFS = "hermes_bridge_config"
        private const val KEY_URL = "base_url"
        private const val KEY_KEY = "api_key"
        private const val KEY_PROFILE = "profile"
        private const val KEY_MODEL = "model"
        private const val KEY_AGENTS = "agents"
        private const val KEY_DEFAULT_AGENT = "default_agent"
        private const val KEY_SPEAK_REPLIES = "speak_replies"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
