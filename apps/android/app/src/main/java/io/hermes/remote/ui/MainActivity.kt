package io.hermes.remote.ui

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.hermes.remote.R
import io.hermes.remote.data.ConfigStore
import io.hermes.remote.model.ChatMessage
import io.hermes.remote.model.ConnectPayload
import io.hermes.remote.model.DeliveryState
import io.hermes.remote.model.GatewayInfo
import io.hermes.remote.model.ServerConfig
import io.hermes.remote.ui.components.BrandLockup
import io.hermes.remote.ui.components.BrandMark
import io.hermes.remote.ui.components.GatewayStatusCard
import io.hermes.remote.ui.components.gatewayVersionLine
import io.hermes.remote.ui.components.Hairline
import io.hermes.remote.ui.components.HermesIcons
import io.hermes.remote.ui.components.InfoRow
import io.hermes.remote.ui.components.SectionHeader
import io.hermes.remote.ui.theme.Dense
import io.hermes.remote.ui.theme.ThemeMode
import io.hermes.remote.ui.theme.XLabCodeStyle
import io.hermes.remote.ui.theme.XLabThemeColors
import io.hermes.remote.ui.theme.XLabTheme
import io.hermes.remote.updater.ApkDownloader
import io.hermes.remote.updater.UpdateInstaller
import io.hermes.remote.voice.AndroidVoiceController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Concrete voice plumbing: system STT + TTS, plus the conversation loop. */
    private lateinit var voiceController: AndroidVoiceController

    /** Query handed in by a Google Assistant "App Action" (Phase B). */
    private var pendingAssistantQuery: String? = null

    /** Last query the Assistant handed us; drives the "from Assistant" banner. */
    private var lastAssistantQuery: String? = null

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(
            ConfigStore(applicationContext),
            getSharedPreferences("hermes_bridge_update", Context.MODE_PRIVATE),
            voiceController,
            AndroidStringProvider(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceController = AndroidVoiceController(applicationContext)
        voiceController.warmUp()
        // Close the continuous-conversation loop: when TTS finishes, the view
        // model decides whether to reopen the microphone.
        voiceController.speakFinishedListener = { viewModel.onSpeakFinished() }

        // A Google Assistant "ask Hermes …" action arrives as an intent extra.
        // `savedInstanceState != null` means this is a config-change/process
        // restore, not a fresh Assistant hand-off — honouring the intent again
        // would resend the prompt (and the reply would be spoken twice).
        pendingAssistantQuery =
            if (savedInstanceState == null) extractAssistantQuery(intent) else null

        // Kick off an update check when the app opens (rate-limited to 6h by
        // the view model, so repeated launches are cheap).
        viewModel.checkForUpdate()
        // Learn which agent CLIs the PC offers, so the picker works even when
        // the QR payload predates the agent fields.
        viewModel.refreshAgents()
        requestVoicePermissionsIfNeeded()

        setContent {
            // "Follow the system" is resolved here, once, so the theme itself
            // only ever receives a settled boolean and nothing downstream has
            // to know about ThemeMode.
            val themeMode by viewModel.themeMode.collectAsState()
            val dark = themeMode.resolveDark(isSystemInDarkTheme())
            XLabTheme(dark = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val state by viewModel.state.collectAsState()
                    ChatScreen(viewModel, state, themeMode)
                }
            }
        }

        // Run a pending Assistant query once the UI exists.
        pendingAssistantQuery?.let { query ->
            pendingAssistantQuery = null
            lastAssistantQuery = query
            viewModel.sendFromAssistant(query)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The Assistant hands off the query while the app may already be open
        // (launchMode=singleTask). Route it through the same path as a cold
        // start so the reply is spoken when speak-replies is on.
        extractAssistantQuery(intent)?.let { query ->
            lastAssistantQuery = query
            viewModel.sendFromAssistant(query)
        }
    }

    override fun onDestroy() {
        voiceController.release()
        super.onDestroy()
    }

    /** Ask for mic + notification permission the first time voice is used. */
    private fun requestVoicePermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            needed += android.Manifest.permission.RECORD_AUDIO
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            needed += android.Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                .launch(needed.toTypedArray())
        }
    }

    companion object {
        /** Action used by the App Actions capability (see res/xml/shortcuts.xml). */
        const val ACTION_ASK = "io.hermes.remote.action.ASK"

        /** Extra carrying the Assistant query text. */
        const val EXTRA_QUERY = "query"

        /**
         * Pull a query out of an Assistant / App-Action intent, if present.
         *
         * The parsing rules live in [io.hermes.remote.voice.AssistantIntent] so
         * they are unit-testable on the JVM; they must stay in sync with the
         * capability declarations in `res/xml/shortcuts.xml`.
         */
        fun extractAssistantQuery(intent: Intent?): String? =
            io.hermes.remote.voice.AssistantIntent.fromIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    viewModel: MainViewModel,
    state: ChatUiState,
    themeMode: ThemeMode,
) {
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Launch the QR scanner and feed a valid payload straight into the VM.
    var scanError by remember { mutableStateOf<String?>(null) }
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
                scanError = context.getString(R.string.qr_screen_bad_code)
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = XLabThemeColors.bgPrimary,
                    titleContentColor = XLabThemeColors.textPrimary,
                ),
                title = { BrandLockup() },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearChat) {
                            Text(
                                text = stringResource(R.string.chat_clear),
                                color = XLabThemeColors.textSecondary,
                            )
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.chat_settings),
                            tint = XLabThemeColors.textSecondary,
                        )
                    }
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
            // One status strip carries connection, agent and voice state. It
            // replaces four stacked full-width banners, which pushed the chat
            // off-screen on a phone.
            StatusStrip(
                config = state.config,
                hint = state.connectionHint,
                connected = state.connected,
                voice = state.voice,
                onStopListening = viewModel::stopVoiceInput,
                onStopSpeaking = viewModel::stopSpeaking,
            )

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
                                    context.getString(R.string.update_install_permission),
                                    Toast.LENGTH_LONG,
                                ).show()
                                UpdateInstaller.openInstallPermissionSettings(context)
                            },
                        )
                    },
                )
            }

            AssistantBanner(
                prompt = state.assistantPrompt,
                onDismiss = viewModel::clearAssistantPrompt,
            )

            // The transcript takes all remaining space. `fillMaxWidth` only:
            // the weight already supplies the height, and adding fillMaxSize
            // here would make the Box fight the allocation.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (state.messages.isEmpty()) {
                    EmptyState(
                        onScanQr = startScan,
                        scanError = scanError,
                        config = state.config,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.messages, key = { it.id }) { msg -> MessageBubble(msg) }
                    }
                }
            }

            // Quick actions scroll horizontally instead of wrapping into a tall
            // block that steals vertical space from the transcript.
            QuickActions(
                actions = viewModel.quickActions,
                enabled = !state.isStreaming,
                onPick = { viewModel.send(it) },
            )

            InputBar(
                value = state.input,
                enabled = !state.isStreaming,
                voice = state.voice,
                onValueChange = viewModel::onInputChange,
                onSend = { viewModel.send() },
                onMic = viewModel::startVoiceInput,
                onMicStop = viewModel::stopVoiceInput,
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            initial = state.config,
            hint = state.connectionHint,
            voice = state.voice,
            gateway = state.gateway,
            gatewayLoading = state.gatewayLoading,
            gatewayFailed = state.gatewayFailed,
            onDismiss = { showSettings = false },
            onSave = {
                viewModel.saveConfig(it)
                showSettings = false
                viewModel.refreshAgents()
            },
            onTest = viewModel::testConnection,
            onScanQr = {
                showSettings = false
                startScan()
            },
            onToggleConversation = viewModel::setConversationMode,
            onRefreshGateway = viewModel::refreshGatewayInfo,
            themeMode = themeMode,
            onThemeMode = viewModel::setThemeMode,
        )
    }
}

/**
 * The single status strip under the app bar.
 *
 * Shows connection health, the active agent as a row of selectable chips, and
 * whatever the voice assistant is doing. Keeping it to one card means the
 * transcript keeps most of the screen, and there is exactly one place to look
 * for "what is going on".
 */
/**
 * The status bar under the app bar.
 *
 * One thin line, editor-style: a state dot, the state, and the active agent.
 * This replaces a ~550px card that carried a pill, a voice row, an "AGENT"
 * heading, a chip row and a warning — a quarter of the screen spent on things
 * that change once a session.
 *
 * Vertical space is the scarce resource on a phone chat, so anything that is
 * not the transcript either earns its height or moves to settings.
 */
@Composable
private fun StatusStrip(
    config: ServerConfig,
    hint: String?,
    connected: Boolean,
    voice: VoiceUiState,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
) {
    val status = connectionStatus(config, hint, connected)
    val voiceLabel = voiceActivityLabel(voice)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(XLabThemeColors.bgSecondary)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(status.second)
            Spacer(Modifier.width(7.dp))
            Text(
                text = status.first,
                style = MaterialTheme.typography.labelSmall,
                color = XLabThemeColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (config.profile.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Meta(config.profile)
            }
            Spacer(Modifier.weight(1f))
            // The active agent is the one piece of settings worth always
            // showing: it decides who answers, and a wrong agent looks like a
            // wrong model rather than a wrong setting.
            val agent = config.effectiveModel.ifBlank { config.defaultAgent }
            if (agent.isNotBlank()) {
                Text(
                    text = agent,
                    style = XLabCodeStyle,
                    color = XLabThemeColors.textTertiary,
                    maxLines = 1,
                )
            }
        }

        // Voice activity gets its own line and appears only while it matters.
        if (voiceLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(XLabThemeColors.assistantWash)
                    .padding(start = 14.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = voiceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = XLabThemeColors.primaryLight,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when (voice.phase) {
                    VoicePhase.LISTENING -> SmallAction(
                        label = stringResource(R.string.voice_stop),
                        onClick = onStopListening,
                    )
                    VoicePhase.SPEAKING -> SmallAction(
                        label = stringResource(R.string.voice_stop_speaking),
                        icon = true,
                        onClick = onStopSpeaking,
                    )
                    else -> Unit
                }
            }
        }

        // A model that had to be redirected is worth one quiet line, because
        // the alternative is the user believing their choice was ignored.
        if (config.modelWasRetargeted) {
            Text(
                text = stringResource(
                    R.string.agent_retargeted,
                    config.model,
                    config.effectiveModel,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = XLabThemeColors.warning,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(XLabThemeColors.bgSecondary)
                    .padding(start = 14.dp, end = 18.dp, top = 3.dp, bottom = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Hairline()
    }
}

/** A 6dp state dot. Cheaper than a pill and it reads at a glance. */
@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color),
    )
}

/** Dim metadata, set in the mono face so it never competes with content. */
@Composable
private fun Meta(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = XLabThemeColors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A selectable agent chip, teal-filled when active. */
@Composable
private fun AgentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        // Square-ish, like an editor's tab or a mode button. A fully round pill
        // per agent turned the settings sheet into a row of lozenges.
        shape = RoundedCornerShape(Dense.chip),
        color = when {
            selected -> XLabThemeColors.primary
            else -> XLabThemeColors.bgTertiary
        },
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) XLabThemeColors.primary else XLabThemeColors.borderSubtle,
        ),
    ) {
        Text(
            text = label,
            style = XLabCodeStyle,
            color = if (selected) Color.White else XLabThemeColors.textSecondary,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

/**
 * A bubble's corners: tight everywhere, with the corner nearest the speaker's
 * side squared off.
 *
 * The asymmetry is what makes a run of messages read as one voice without an
 * avatar or a name on every line — the tail points at who said it.
 */
private fun editorBubbleShape(isUser: Boolean): RoundedCornerShape = RoundedCornerShape(
    topStart = Dense.bubble,
    topEnd = Dense.bubble,
    bottomStart = if (isUser) Dense.bubble else Dense.rule,
    bottomEnd = if (isUser) Dense.rule else Dense.bubble,
)

/** A compact text button for inline strip actions. */
@Composable
private fun SmallAction(
    label: String,
    onClick: () -> Unit,
    icon: Boolean = false,
) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        if (icon) {
            Icon(
                imageVector = HermesIcons.Stop,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = XLabThemeColors.primaryLight,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = XLabThemeColors.primaryLight,
        )
    }
}

/** Resolve the connection pill's text and dot colour. */
@Composable
private fun connectionStatus(config: ServerConfig, hint: String?, connected: Boolean): Pair<String, Color> {
    if (!config.isValid()) {
        return stringResource(R.string.conn_missing_config) to XLabThemeColors.warning
    }
    // `connected` is the source of truth; the hint is only narration. Colour is
    // never derived by parsing that prose, which breaks on wording changes.
    return when {
        connected -> (hint ?: stringResource(R.string.conn_ready)) to XLabThemeColors.success
        hint != null -> hint to XLabThemeColors.error
        else -> stringResource(R.string.conn_offline) to XLabThemeColors.textTertiary
    }
}

/** What the assistant is doing, or null when it is quiet and idle. */
@Composable
private fun voiceActivityLabel(voice: VoiceUiState): String? {
    val msg = voice.statusMessage
    return when (voice.phase) {
        VoicePhase.LISTENING ->
            voice.partial.ifBlank { stringResource(R.string.voice_phase_listening) }
        VoicePhase.THINKING -> stringResource(R.string.voice_phase_thinking)
        VoicePhase.SPEAKING -> stringResource(R.string.voice_phase_speaking)
        // Nothing is shown while idle. There used to be a "waiting for the wake
        // word" hint here, gated on the assistant being armed in the
        // background; that mode is gone, so an idle line would be noise.
        VoicePhase.IDLE -> when {
            !msg.isNullOrBlank() -> msg
            voice.conversationMode -> stringResource(R.string.voice_status_conversation)
            else -> null
        }
    }
}

/**
 * Banner shown when a prompt arrived from Google Assistant, so the user can see
 * the "Hey Google → Hermes" hop actually landed.
 */
@Composable
private fun AssistantBanner(prompt: String?, onDismiss: () -> Unit) {
    val text = prompt ?: return
    // Read before the draw lambda: drawBehind runs at draw time, outside the
    // composable scope, so it cannot resolve the composition local itself.
    val ruleColor = XLabThemeColors.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(XLabThemeColors.assistantWash)
            // A 2px leading rule instead of a rounded box. It is the same
            // device an editor uses for a diff gutter or an info callout: it
            // reads as an annotation on the transcript rather than as another
            // card competing with the messages.
            .drawBehind {
                drawRect(
                    color = ruleColor,
                    size = Size(Dense.rule.toPx(), size.height),
                )
            }
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.assistant_banner),
                style = MaterialTheme.typography.labelSmall,
                color = XLabThemeColors.primaryLight,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = XLabThemeColors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = XLabThemeColors.textTertiary,
            )
        }
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
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(Dense.control),
        colors = CardDefaults.cardColors(containerColor = XLabThemeColors.bgSecondary),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.update_available, info.versionName),
                style = MaterialTheme.typography.titleSmall,
                color = XLabThemeColors.textPrimary,
            )
            if (update.downloading) {
                LinearProgressIndicator(
                    progress = (update.progress ?: 0) / 100f,
                    modifier = Modifier.fillMaxWidth(),
                    color = XLabThemeColors.primary,
                    trackColor = XLabThemeColors.bgTertiary,
                )
                Text(
                    text = update.progress?.let { "$it%" }
                        ?: stringResource(R.string.update_downloading),
                    style = MaterialTheme.typography.bodySmall,
                    color = XLabThemeColors.textSecondary,
                )
            }
            update.error?.let {
                Text(
                    text = it,
                    color = XLabThemeColors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !update.downloading) {
                    Text(
                        text = stringResource(R.string.update_later),
                        color = XLabThemeColors.textSecondary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onUpdate, enabled = !update.downloading) {
                    Text(
                        text = stringResource(
                            if (update.downloading) R.string.update_downloading
                            else R.string.update_now,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * First-run guidance.
 *
 * Mirrors the numbered steps on the pairing page, so the phone and the browser
 * tell the user the same story, and shows what was learned from the QR scan.
 *
 * The scroll and the centring cannot both live on one Column: a vertically
 * scrollable Column is measured with an infinite height constraint, so
 * `Arrangement.Center` would centre the content in infinite space and push it
 * off-screen (which is exactly what it did — the panel rendered blank). The
 * outer Box supplies a real height and centres; the inner Column scrolls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(
    onScanQr: () -> Unit,
    scanError: String?,
    config: ServerConfig,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        // Top-aligned rather than centred: with a short panel, centring pushed
        // the content into the middle and left dead bands above and below. An
        // editor's welcome tab starts at the top for the same reason.
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            BrandMark(size = 40.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleMedium,
                color = XLabThemeColors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.setup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = XLabThemeColors.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onScanQr,
                shape = RoundedCornerShape(Dense.control),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_scan_qr_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            scanError?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = XLabThemeColors.error,
                )
            }

            if (config.isValid()) {
                Spacer(Modifier.height(18.dp))
                Hairline()
                InfoRow(
                    label = stringResource(R.string.conn_profile_label),
                    value = config.profile.ifBlank { "—" },
                )
                InfoRow(
                    label = stringResource(R.string.conn_model_label),
                    value = config.effectiveModel.ifBlank { "—" },
                )
                // The agent list is only worth its own row when there is a
                // choice to make. With a single agent it repeated the row
                // above verbatim, so it is folded away.
                if (config.agentChoices.size > 1) {
                    InfoRow(
                        label = stringResource(R.string.conn_agents_label),
                        value = "",
                        divider = false,
                        valueSlot = {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                config.agentChoices.forEach { id ->
                                    AgentChip(
                                        label = id,
                                        selected = id == config.effectiveModel.trim().lowercase(),
                                        enabled = false,
                                        onClick = {},
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * One chat bubble.
 *
 * Speaker is read from three things at once — which side it sits on, the
 * surface behind it, and the avatar — so it survives greyscale and a
 * colour-blind reader. Colour alone is never the only signal.
 *
 * There is deliberately no per-message "Hermes"/"You" caption: with one avatar
 * and two alignment sides, the label repeated what the layout already said
 * while adding a line of height to every single turn. An error is the one case
 * that gets a caption, because there the tone genuinely differs.
 */
@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.isUser
    val isError = msg.state == DeliveryState.ERROR

    val bubbleColor = when {
        isError -> XLabThemeColors.errorBubble
        isUser -> XLabThemeColors.userBubble
        else -> XLabThemeColors.assistantBubble
    }
    val bodyColor = when {
        isUser -> Color.White
        else -> XLabThemeColors.textPrimary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            BrandMark(size = 22.dp)
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (isError) {
                Text(
                    text = stringResource(R.string.chat_error_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = XLabThemeColors.error,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(editorBubbleShape(isUser))
                    .background(bubbleColor)
                    .padding(horizontal = 11.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = msg.content.ifBlank { "…" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = bodyColor,
                    )
                    if (msg.state == DeliveryState.STREAMING) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.5.dp,
                            color = XLabThemeColors.primaryLight,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The quick-prompt chips.
 *
 * This is a scrolling row, so the last chip is legitimately wider than the
 * space left. Without a cue that reads as a rendering bug rather than "there is
 * more to the right", so the trailing edge carries a fade into the background
 * colour — the standard affordance for a horizontal list on a dark surface.
 *
 * The fade is drawn only when the row actually has more to scroll, so a device
 * wide enough to show every chip gets no misleading gradient.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(
    actions: List<Pair<String, String>>,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val moreToSee by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index < listState.layoutInfo.totalItemsCount - 1
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(actions, key = { it.first }) { (label, prompt) ->
                Surface(
                    onClick = { onPick(prompt) },
                    enabled = enabled,
                    shape = RoundedCornerShape(Dense.chip),
                    color = XLabThemeColors.bgSecondary,
                    border = androidx.compose.foundation.BorderStroke(1.dp, XLabThemeColors.borderSubtle),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) XLabThemeColors.textSecondary else XLabThemeColors.textTertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1,
                    )
                }
            }
        }

        if (moreToSee) {
            // fillMaxHeight is deliberately avoided here. This Box is a
            // non-weighted child of the screen's Column, so it is measured with
            // an unbounded height; fillMaxHeight inside it resolves to zero and
            // poisons the Column's measurement, which starved the transcript's
            // weight(1f) sibling down to no height at all. matchParentSize()
            // takes its height from the already-measured LazyRow instead.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0.7f to Color.Transparent,
                            1f to XLabThemeColors.bgPrimary,
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    enabled: Boolean,
    voice: VoiceUiState,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onMicStop: () -> Unit,
) {
    val listening = voice.phase == VoicePhase.LISTENING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(XLabThemeColors.bgSecondary)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = stringResource(R.string.chat_placeholder),
                    color = XLabThemeColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            enabled = enabled,
            // Flat and near-square, like an editor's find box. A 14dp pill on
            // the primary input made the compose field look like a search bar
            // from a different app.
            shape = RoundedCornerShape(Dense.control),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSend() }),
        )

        // Mic: tap to talk, tap again to stop.
        FilledTonalButton(
            onClick = { if (listening) onMicStop() else onMic() },
            enabled = (enabled || listening) && voice.micAvailable,
            shape = RoundedCornerShape(Dense.control),
            modifier = Modifier.size(44.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = if (listening) HermesIcons.Stop else HermesIcons.Mic,
                contentDescription = stringResource(
                    if (listening) R.string.chat_mic_stop else R.string.chat_mic,
                ),
                modifier = Modifier.size(18.dp),
            )
        }

        Button(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            shape = RoundedCornerShape(Dense.control),
            modifier = Modifier.size(44.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = stringResource(R.string.chat_send),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(
    initial: ServerConfig,
    hint: String?,
    voice: VoiceUiState,
    gateway: GatewayInfo?,
    gatewayLoading: Boolean,
    gatewayFailed: Boolean,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit,
    onTest: (ServerConfig) -> Unit,
    onScanQr: () -> Unit,
    onToggleConversation: (Boolean) -> Unit,
    onRefreshGateway: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
) {
    var url by remember { mutableStateOf(initial.baseUrl) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var profile by remember { mutableStateOf(initial.profile) }
    var model by remember { mutableStateOf(initial.model) }
    var showAdvanced by remember { mutableStateOf(false) }

    val current = ServerConfig(url, key, profile, model, initial.agents, initial.defaultAgent)

    val context = LocalContext.current
    var showAssistantHelp by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = XLabThemeColors.bgPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                // Extra right inset: the assistive-touch bubble many OEMs float
                // over the right edge otherwise sits on top of the field labels
                // and the Save button.
                .padding(end = 12.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleMedium,
                color = XLabThemeColors.textPrimary,
            )

            OutlinedButton(
                onClick = onScanQr,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dense.control),
            ) {
                Text(
                    text = stringResource(R.string.setup_scan_qr),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            // Appearance sits high because it is the one setting a user may
            // change on their very first run, before they have connected
            // anything at all.
            SectionHeader(title = stringResource(R.string.settings_theme_title))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeMode.entries.forEach { mode ->
                    AgentChip(
                        label = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                ThemeMode.DARK -> R.string.settings_theme_dark
                                ThemeMode.LIGHT -> R.string.settings_theme_light
                            },
                        ),
                        selected = mode == themeMode,
                        onClick = { onThemeMode(mode) },
                    )
                }
            }

            // What the PC reports about itself, straight from the gateway's own
            // endpoints. Sits next to the connection fields because it answers
            // the same question in more detail: not just "can I reach it" but
            // "is it healthy, and what is it doing".
            SectionHeader(
                title = stringResource(R.string.gw_section_title),
                // The version belongs here, not in the card: the header already
                // prints the title, and the card repeating it would stack "Your
                // PC" over "Your PC".
                subtitle = gatewayVersionLine(gateway),
                action = {
                    TextButton(onClick = onRefreshGateway) {
                        Text(
                            text = stringResource(R.string.gw_refresh),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
            )
            GatewayStatusCard(
                info = gateway,
                loading = gatewayLoading,
                onRefresh = onRefreshGateway,
                failed = gatewayFailed,
            )

            // Agent choice sits above the raw connection fields: it is the
            // setting people actually change, while URL and key are set once
            // by the QR scan and rarely touched again.
            //
            // Hidden with a single agent: a picker with one option cannot be
            // used, so it would only be a heading followed by a dead chip.
            if (initial.agentChoices.size > 1) {
                SectionHeader(
                    title = stringResource(R.string.agent_title),
                    subtitle = stringResource(R.string.agent_hint),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    initial.agentChoices.forEach { id ->
                        AgentChip(
                            label = id,
                            selected = id == model.trim().lowercase(),
                            onClick = { model = id },
                        )
                    }
                }
            }

            // URL and key live behind "Advanced": the QR scan sets them, and a
            // user who paired a phone never needs to see them again. Showing
            // them up front made the sheet read as a config file rather than a
            // settings screen.
            SectionHeader(
                title = stringResource(R.string.setup_advanced),
                action = {
                    TextButton(onClick = { showAdvanced = !showAdvanced }) {
                        Text(
                            text = stringResource(
                                if (showAdvanced) R.string.setup_hide_advanced
                                else R.string.setup_show_advanced,
                            ),
                            color = XLabThemeColors.primaryLight,
                        )
                    }
                },
            )

            if (showAdvanced) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.setup_server_url_label)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dense.control),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.setup_api_key_label)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dense.control),
                )

                OutlinedTextField(
                    value = profile,
                    onValueChange = { profile = it },
                    label = { Text(stringResource(R.string.setup_profile_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dense.control),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.setup_model_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dense.control),
                )
                // Test is a diagnostic, not a step in setting up: a paired user
                // never needs it, and showing it next to Save invited people to
                // press a button that only reports what Save already tells them.
                OutlinedButton(
                    onClick = { onTest(current) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dense.control),
                ) {
                    Text(stringResource(R.string.setup_test))
                }
            }

            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = XLabThemeColors.textSecondary,
                )
            }

            Hairline()

            SectionHeader(title = stringResource(R.string.settings_voice_title))

            // The app's only hands-free mode. An always-listening wake word
            // used to live here; it needed a third-party key and a permanent
            // background microphone, so it was removed in favour of this.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_conversation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = XLabThemeColors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = voice.conversationMode,
                    onCheckedChange = { on -> onToggleConversation(on) },
                )
            }
            Text(
                text = stringResource(R.string.settings_conversation_hint),
                style = MaterialTheme.typography.bodySmall,
                color = XLabThemeColors.textTertiary,
            )

            Hairline()

            AssistantHelpSection(
                expanded = showAssistantHelp,
                onToggle = { showAssistantHelp = !showAssistantHelp },
            )

            Hairline()

            // One primary action. The empty saved-hint case is covered by the
            // hint text above, so a second button here only split attention.
            Button(
                onClick = { onSave(current) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dense.control),
            ) {
                Text(stringResource(R.string.setup_save))
            }
        }
    }
}

/**
 * Tells the user exactly what to say to Google Assistant to reach Hermes.
 *
 * Wording matters here: free-text requests must use the *custom* capability
 * ("ask <app> <request>") declared in res/xml/shortcuts.xml. The built-in
 * "open <app>" route only opens the app, it carries no sentence.
 */
@Composable
private fun AssistantHelpSection(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(
            title = stringResource(R.string.assistant_help_title),
            subtitle = stringResource(R.string.assistant_help_summary),
            action = {
                TextButton(onClick = onToggle) {
                    Text(
                        text = stringResource(
                            if (expanded) R.string.assistant_help_hide
                            else R.string.assistant_help_show,
                        ),
                        color = XLabThemeColors.primaryLight,
                    )
                }
            },
        )

        if (expanded) {
            Text(
                text = stringResource(R.string.assistant_help_say_to_assistant),
                style = MaterialTheme.typography.labelLarge,
                color = XLabThemeColors.textPrimary,
            )
            Text(
                text = stringResource(R.string.assistant_help_examples),
                style = MaterialTheme.typography.bodySmall,
                color = XLabThemeColors.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.assistant_help_note),
                style = MaterialTheme.typography.labelSmall,
                color = XLabThemeColors.textTertiary,
            )
        }
    }
}
