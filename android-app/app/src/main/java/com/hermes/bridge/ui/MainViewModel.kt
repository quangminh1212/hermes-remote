package com.hermes.bridge.ui

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.bridge.websocket.ConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the main screen. */
data class MainUiState(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val pairingCode: String = "",
    val connectionState: ConnectionManager.State = ConnectionManager.State.DISCONNECTED,
    val accessibilityEnabled: Boolean = false,
) {
    companion object {
        const val DEFAULT_SERVER_URL = "ws://192.168.1.100:8765"
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ConnectionManager.state.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
    }

    fun onServerUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(serverUrl = value)
    }

    fun onPairingCodeChange(value: String) {
        _uiState.value = _uiState.value.copy(pairingCode = value)
    }

    fun refreshAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled()
        _uiState.value = _uiState.value.copy(accessibilityEnabled = enabled)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val context = getApplication<Application>()
        val expected = "${context.packageName}/${SERVICE_CLASS}"
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    companion object {
        const val SERVICE_CLASS = "com.hermes.bridge.service.AccessibilityBridgeService"
    }
}
