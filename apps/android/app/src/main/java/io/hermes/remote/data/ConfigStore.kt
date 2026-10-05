package io.hermes.remote.data

import android.content.Context
import io.hermes.remote.model.ServerConfig

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
    )

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_URL, config.baseUrl)
            .putString(KEY_KEY, config.apiKey)
            .putString(KEY_PROFILE, config.profile)
            .putString(KEY_MODEL, config.model)
            .apply()
    }

    // --- Voice assistant settings (kept alongside the server config) ---------

    /** Whether the always-listening wake-word service should run. */
    fun isVoiceAssistantEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_ENABLED, false)

    fun setVoiceAssistantEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
    }

    /** Whether assistant replies should be read aloud. */
    fun isSpeakRepliesEnabled(): Boolean = prefs.getBoolean(KEY_SPEAK_REPLIES, true)

    fun setSpeakRepliesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPEAK_REPLIES, enabled).apply()
    }

    /** Picovoice AccessKey for the "Hey Hermes" wake word (empty = disabled). */
    fun loadPorcupineKey(): String = prefs.getString(KEY_PORCUPINE, "").orEmpty()

    fun savePorcupineKey(key: String) {
        prefs.edit().putString(KEY_PORCUPINE, key.trim()).apply()
    }

    companion object {
        private const val PREFS = "hermes_bridge_config"
        private const val KEY_URL = "base_url"
        private const val KEY_KEY = "api_key"
        private const val KEY_PROFILE = "profile"
        private const val KEY_MODEL = "model"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_SPEAK_REPLIES = "speak_replies"
        private const val KEY_PORCUPINE = "porcupine_key"
    }
}
