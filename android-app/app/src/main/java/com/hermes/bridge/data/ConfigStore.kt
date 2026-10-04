package com.hermes.bridge.data

import android.content.Context
import com.hermes.bridge.model.ServerConfig

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

    companion object {
        private const val PREFS = "hermes_bridge_config"
        private const val KEY_URL = "base_url"
        private const val KEY_KEY = "api_key"
        private const val KEY_PROFILE = "profile"
        private const val KEY_MODEL = "model"
    }
}
