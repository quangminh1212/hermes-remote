package com.hermes.bridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hermes.bridge.websocket.WebSocketClient

/**
 * Foreground service that maintains persistent WebSocket connection
 * to Hermes Agent relay server
 */
class WebSocketConnectionService : Service() {
    
    companion object {
        const val CHANNEL_ID = "hermes_bridge_connection"
        const val NOTIFICATION_ID = 1001
    }
    
    private var webSocketClient: WebSocketClient? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverUrl = intent?.getStringExtra("SERVER_URL") ?: "ws://localhost:8765/ws"
        val pairingCode = intent?.getStringExtra("PAIRING_CODE")
        
        // Initialize WebSocket client
        webSocketClient = WebSocketClient()
        
        // Set accessibility service instance and WebSocket client
        AccessibilityBridgeService.webSocketClient = webSocketClient
        
        // Connect to server
        webSocketClient?.connect(serverUrl, pairingCode)
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        webSocketClient?.disconnect()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hermes Bridge Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains connection to Hermes Agent server"
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Bridge Connected")
            .setContentText("Waiting for commands from Hermes Agent")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
