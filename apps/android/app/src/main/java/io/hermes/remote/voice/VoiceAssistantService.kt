package io.hermes.remote.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.hermes.remote.R
import io.hermes.remote.data.ConfigStore
import io.hermes.remote.ui.MainActivity

/**
 * Foreground service that keeps the "Hey Hermes" wake-word listener alive while
 * the app is backgrounded.
 *
 * The service does not own the conversation: when the wake word fires it raises
 * [VoiceBus] so the UI (or the service's own notification action) can start a
 * listening turn. This keeps a single source of truth for chat state in
 * [io.hermes.remote.ui.MainViewModel].
 *
 * Type `microphone`. Started only when the user enables voice mode, and stopped
 * when they disable it — a persistent notification is always shown while it runs.
 */
class VoiceAssistantService : Service() {

    private lateinit var detector: WakeWordDetector

    override fun onCreate() {
        super.onCreate()
        detector = WakeWordDetector(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startListening()
        }
        return START_STICKY
    }

    private fun startListening() {
        startForegroundCompat(buildNotification(getString(R.string.voice_notif_listening)))

        val key = ConfigStore(this).loadPorcupineKey()
        val started = detector.start(key) {
            Log.i(TAG, "Wake word detected")
            VoiceBus.emitWake()
        }
        if (!started) {
            // No key / engine failed: keep the service alive but idle so the UI
            // toggle stays consistent, and tell the user why.
            VoiceBus.emitWakeUnavailable()
            updateNotification(getString(R.string.voice_notif_unavailable))
        }
    }

    override fun onDestroy() {
        detector.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Notifications -------------------------------------------------------

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.voice_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.voice_channel_desc) }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceAssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.voice_notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.voice_notif_stop), stop)
            .build()
    }

    companion object {
        private const val TAG = "HermesVoiceService"
        private const val CHANNEL_ID = "hermes_voice"
        private const val NOTIF_ID = 4211

        const val ACTION_START = "io.hermes.remote.action.VOICE_START"
        const val ACTION_STOP = "io.hermes.remote.action.VOICE_STOP"

        /** Start the always-listening service (no-op if already running). */
        fun start(context: Context) {
            val intent = Intent(context, VoiceAssistantService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stop the service and release the microphone. */
        fun stop(context: Context) {
            context.startService(
                Intent(context, VoiceAssistantService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
