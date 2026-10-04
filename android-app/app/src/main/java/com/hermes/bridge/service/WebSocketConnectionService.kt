package com.hermes.bridge.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hermes.bridge.R
import com.hermes.bridge.protocol.CommandRequest
import com.hermes.bridge.websocket.ConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that holds the relay connection alive independently of the UI.
 */
class WebSocketConnectionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null
    private val executor = CommandExecutor()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(getString(R.string.notif_text_connecting)),
                )
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
                startConnection(url, code)
            }
            ACTION_STOP -> {
                ConnectionManager.disconnect()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startConnection(url: String, code: String) {
        ConnectionManager.connect(
            serverUrl = url,
            pairingCode = code,
            commandHandler = { command: CommandRequest -> executor.execute(command) },
        )
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            ConnectionManager.state.collect { state -> updateNotification(state) }
        }
    }

    @Suppress("MissingPermission")
    private fun updateNotification(state: ConnectionManager.State) {
        if (!hasNotificationPermission()) return
        val text = when (state) {
            ConnectionManager.State.CONNECTED -> getString(R.string.notif_text_connected)
            ConnectionManager.State.CONNECTING -> getString(R.string.notif_text_connecting)
            else -> getString(R.string.status_disconnected)
        }
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing", e)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotification(text: CharSequence) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW,
        ).setName(getString(R.string.notif_channel_name)).build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        ConnectionManager.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WsConnectionService"
        private const val CHANNEL_ID = "hermes_bridge_connection"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.hermes.bridge.action.START"
        const val ACTION_STOP = "com.hermes.bridge.action.STOP"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_CODE = "extra_code"

        fun start(context: Context, url: String, code: String) {
            val intent = Intent(context, WebSocketConnectionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_CODE, code)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "start requested for $url")
        }

        fun stop(context: Context) {
            val intent = Intent(context, WebSocketConnectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
