package com.hermes.bridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    
    private val _connectionState = MutableStateFlow(ConnectionStatus.Disconnected)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()
    
    private val _serverUrl = MutableStateFlow("ws://localhost:8765/ws")
    val serverUrl = _serverUrl.asStateFlow()
    
    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: String? get() = _pairingCode.value
    
    fun updateServerUrl(newUrl: String) {
        _serverUrl.value = newUrl
    }
    
    fun setPairingCode(code: String?) {
        _pairingCode.value = code
    }
    
    fun isValidConfig(): Boolean {
        val url = serverUrl.value.trim()
        
        if (url.isEmpty()) return false
        
        // Basic URL validation
        return url.startsWith("ws://") || url.startsWith("wss://")
    }
}
