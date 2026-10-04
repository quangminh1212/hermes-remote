package com.hermes.bridge.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hermes.bridge.service.WebSocketConnectionService
import com.hermes.bridge.websocket.ConnectionManager

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel, ::openAccessibilitySettings, ::openBatterySettings)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccessibilityStatus()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openBatterySettings() {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

@Composable
private fun MainScreen(
    viewModel: MainViewModel,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Hermes Bridge", style = MaterialTheme.typography.headlineMedium)
        StatusCard(state.connectionState, state.accessibilityEnabled)

        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = viewModel::onServerUrlChange,
            label = { Text("Server WebSocket URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.pairingCode,
            onValueChange = viewModel::onPairingCodeChange,
            label = { Text("Pairing Code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row2(
            left = {
                Button(
                    onClick = {
                        WebSocketConnectionService.start(context, state.serverUrl, state.pairingCode)
                    },
                    enabled = state.connectionState == ConnectionManager.State.DISCONNECTED,
                ) { Text("Start") }
            },
            right = {
                OutlinedButton(
                    onClick = { WebSocketConnectionService.stop(context) },
                    enabled = state.connectionState != ConnectionManager.State.DISCONNECTED,
                ) { Text("Stop") }
            },
        )

        Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
            Text("Enable Accessibility Service")
        }
        OutlinedButton(onClick = onOpenBattery, modifier = Modifier.fillMaxWidth()) {
            Text("Disable Battery Optimization")
        }
    }
}

@Composable
private fun StatusCard(
    connectionState: ConnectionManager.State,
    accessibilityEnabled: Boolean,
) {
    val (label, color) = when (connectionState) {
        ConnectionManager.State.CONNECTED -> "Connected" to Color(0xFF2E7D32)
        ConnectionManager.State.CONNECTING -> "Connecting…" to Color(0xFFF9A825)
        ConnectionManager.State.ERROR -> "Error" to Color(0xFFC62828)
        ConnectionManager.State.DISCONNECTED -> "Disconnected" to Color(0xFF616161)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Connection: $label", color = color, style = MaterialTheme.typography.titleMedium)
            Text(
                "Accessibility: " + if (accessibilityEnabled) "Enabled" else "Disabled",
                color = if (accessibilityEnabled) Color(0xFF2E7D32) else Color(0xFFC62828),
            )
        }
    }
}

@Composable
private fun Row2(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        left()
        right()
    }
}
