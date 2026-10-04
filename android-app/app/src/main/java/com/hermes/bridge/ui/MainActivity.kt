package com.hermes.bridge.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.bridge.data.ConfigStore
import com.hermes.bridge.model.ChatMessage
import com.hermes.bridge.model.ConnectPayload
import com.hermes.bridge.model.DeliveryState
import com.hermes.bridge.model.ServerConfig
import com.hermes.bridge.updater.ApkDownloader
import com.hermes.bridge.updater.UpdateInstaller

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(
            ConfigStore(applicationContext),
            getSharedPreferences("hermes_bridge_update", Context.MODE_PRIVATE),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kick off an update check when the app opens (rate-limited to 6h by
        // the view model, so repeated launches are cheap).
        viewModel.checkForUpdate()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Launch the QR scanner and feed a valid payload straight into the VM.
    var scanError by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringExtra(QrScanActivity.EXTRA_QR_RESULT)
            val payload = ConnectPayload.parse(text)
            if (payload != null) {
                scanError = null
                viewModel.applyConnectPayload(payload)
            } else {
                scanError = "Mã QR không phải của Hermes Remote"
            }
        }
    }
    val startScan: () -> Unit = {
        scanLauncher.launch(Intent(context, QrScanActivity::class.java))
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hermes Remote") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onClick = viewModel::clearChat) { Text("Xoá") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            ConnectionBanner(state.config, state.connectionHint)

            if (state.update.available != null) {
                UpdateBanner(
                    update = state.update,
                    onDismiss = viewModel::dismissUpdate,
                    onUpdate = {
                        viewModel.downloadUpdate(
                            downloader = ApkDownloader(context),
                            canInstall = { UpdateInstaller.canInstall(context) },
                            onReady = { file ->
                                if (!UpdateInstaller.install(context, file)) {
                                    UpdateInstaller.openInstallPermissionSettings(context)
                                }
                            },
                            onNeedsPermission = {
                                Toast.makeText(
                                    context,
                                    "Cần bật \"Cho phép cài đặt ứng dụng\" rồi thử lại",
                                    Toast.LENGTH_LONG,
                                ).show()
                                UpdateInstaller.openInstallPermissionSettings(context)
                            },
                        )
                    },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    EmptyState(onScanQr = startScan, scanError = scanError ?: state.connectionHint)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.messages, key = { it.id }) { msg -> MessageBubble(msg) }
                    }
                }
            }

            QuickActions(
                actions = viewModel.quickActions,
                enabled = !state.isStreaming,
                onPick = { viewModel.send(it) },
            )

            InputBar(
                value = state.input,
                enabled = !state.isStreaming,
                onValueChange = viewModel::onInputChange,
                onSend = { viewModel.send() },
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            initial = state.config,
            hint = state.connectionHint,
            onDismiss = { showSettings = false },
            onSave = {
                viewModel.saveConfig(it)
                showSettings = false
            },
            onTest = viewModel::testConnection,
            onScanQr = {
                showSettings = false
                startScan()
            },
        )
    }
}

@Composable
private fun UpdateBanner(
    update: UpdateUiState,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    val info = update.available ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Có bản cập nhật ${info.versionName}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (update.downloading) {
                Spacer(Modifier.height(6.dp))
                val label = update.progress?.let { "$it%" } ?: "Đang tải…"
                LinearProgressIndicator(
                    progress = (update.progress ?: 0) / 100f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
            update.error?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, enabled = !update.downloading) {
                    Text("Để sau")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onUpdate, enabled = !update.downloading) {
                    Text(if (update.downloading) "Đang tải…" else "Cập nhật")
                }
            }
        }
    }
}

@Composable
private fun ConnectionBanner(config: ServerConfig, hint: String?) {
    val profile = config.profile.ifBlank { "?" }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "Profile: $profile · Model: ${config.model}",
                style = MaterialTheme.typography.labelLarge,
            )
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EmptyState(onScanQr: () -> Unit, scanError: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📷", style = MaterialTheme.typography.displayLarge)
            Text(
                "Chạy `npx hermes-remote` trên máy tính,\nrồi quét mã QR trên trang mở ra để kết nối.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            androidx.compose.material3.Button(onClick = onScanQr) { Text("Quét QR") }
            if (scanError != null) {
                Text(
                    scanError,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1565C0),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.isUser
    val bubbleColor = when {
        msg.state == DeliveryState.ERROR -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    text = if (isUser) "Bạn" else "Hermes",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(msg.content.ifBlank { "…" })
                    if (msg.state == DeliveryState.STREAMING) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .widthIn(max = 14.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(
    actions: List<Pair<String, String>>,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        actions.forEach { (label, prompt) ->
            AssistChip(
                onClick = { onPick(prompt) },
                enabled = enabled,
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Nhắn lệnh cho Hermes…") },
            maxLines = 4,
            enabled = enabled,
        )
        androidx.compose.material3.Button(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
        ) { Text("Gửi") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    initial: ServerConfig,
    hint: String?,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit,
    onTest: (ServerConfig) -> Unit,
    onScanQr: () -> Unit,
) {
    var url by remember { mutableStateOf(initial.baseUrl) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var profile by remember { mutableStateOf(initial.profile) }
    var model by remember { mutableStateOf(initial.model) }

    val current = ServerConfig(url, key, profile, model)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Cài đặt kết nối", style = MaterialTheme.typography.titleLarge)

            androidx.compose.material3.OutlinedButton(
                onClick = onScanQr,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("📷 Quét QR từ máy tính") }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL (vd: https://abc.trycloudflare.com)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API key (API_SERVER_KEY)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = profile,
                onValueChange = { profile = it },
                label = { Text("Profile (để trống = dùng key .env; hoặc dalek / doraemon / heimeringer)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1565C0))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = { onTest(current) }) {
                    Text("Kiểm tra")
                }
                androidx.compose.material3.Button(
                    onClick = { onSave(current) },
                    modifier = Modifier.weight(1f),
                ) { Text("Lưu") }
            }
        }
    }
}
