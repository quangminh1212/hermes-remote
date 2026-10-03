package com.hermes.bridge.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels observableby
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hermes.bridge.service.WebSocketConnectionService
import com.hermes.bridge.R

class MainActivity : AppCompatActivity() {
    
    private val viewModel: MainViewModel = MainViewModel()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(
                        connectionStatus = viewModel.connectionState,
                        serverUrl = viewModel.serverUrl.value,
                        pairingCode = viewModel.pairingCode,
                        onServerUrlChange = viewModel::updateServerUrl,
                        onPairingCodeChange = viewModel::updatePairingCode,
                        onStartConnection = ::startConnectionService,
                        onCheckAccessibility = ::openAccessibilitySettings
                    )
                }
            }
        }
    }
    
    private fun startConnectionService() {
        if (viewModel.isValidConfig()) {
            val intent = Intent(this, WebSocketConnectionService::class.java).apply {
                putExtra("SERVER_URL", viewModel.serverUrl.value)
                putExtra("PAIRING_CODE", viewModel.pairingCode)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            Toast.makeText(this, "Connecting to server...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Invalid configuration", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    connectionStatus: ConnectionStatus,
    serverUrl: String,
    pairingCode: String?,
    onServerUrlChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onStartConnection: () -> Unit,
    onCheckAccessibility: () -> Unit
) {
    var showInstructions by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Hermes Bridge",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Connect your Android device to Hermes Agent",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        
        // Status indicator
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    when (connectionStatus) {
                        ConnectionStatus.Connected -> Color.Green
                        ConnectionStatus.Connecting -> Color.Yellow
                        else -> Color.Red
                    },
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        )
        
        Text(
            text = when (connectionStatus) {
                ConnectionStatus.Connected -> "✓ Connected"
                ConnectionStatus.Connecting -> "⏳ Connecting..."
                ConnectionStatus.Disconnected -> "✗ Disconnected"
            },
            style = MaterialTheme.typography.bodyMedium
        )
        
        // Server URL input
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Server WebSocket URL") },
            placeholder = { Text("ws://your-server-ip:8765/ws") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // Pairing code display (if paired)
        if (pairingCode != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Pairing Code",
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Text(
                        text = pairingCode,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Button(
                        onClick = { showInstructions = !showInstructions },
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text("Show Instructions")
                    }
                }
            }
        }
        
        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStartConnection,
                enabled = connectionStatus == ConnectionStatus.Disconnected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Connection")
            }
            
            Button(
                onClick = onCheckAccessibility,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Accessibility Service")
            }
        }
        
        // Instructions card
        if (showInstructions && pairingCode != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Setup Instructions:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "1. On your PC, run the Python relay server\n" +
                             "2. Note the pairing code shown above\n" +
                             "3. In Hermes Agent tools, call:\n   android_pair_device(display_name=\"My Phone\")\n" +
                             "4. Enter the pairing code in the Android app\n" +
                             "5. Start connection and enjoy!",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Connected : ConnectionStatus()
}
