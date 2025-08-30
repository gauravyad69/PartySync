package io.github.gauravyad69.partysync.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.gauravyad69.partysync.R
import io.github.gauravyad69.partysync.MainActivity
import io.github.gauravyad69.partysync.audio.AudioStreamingMode
import io.github.gauravyad69.partysync.network.ConnectionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service for managing PartySync sessions
 * Shows persistent notification and manages session lifecycle
 */
class PartySessionService : Service() {
    
    companion object {
        private const val TAG = "PartySessionService"
        private const val NOTIFICATION_CHANNEL_ID = "party_session_channel"
        private const val NOTIFICATION_ID = 1001
        
        // Service actions
        const val ACTION_START_HOST_SESSION = "start_host_session"
        const val ACTION_START_JOIN_SESSION = "start_join_session"
        const val ACTION_STOP_SESSION = "stop_session"
        const val ACTION_TOGGLE_STREAMING = "toggle_streaming"
        
        // Intent extras
        const val EXTRA_ROOM_CODE = "room_code"
        const val EXTRA_CONNECTION_TYPE = "connection_type"
        const val EXTRA_STREAMING_MODE = "streaming_mode"
    }
    
    private val binder = LocalBinder()
    
    // Session state
    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting.asStateFlow()
    
    private val _isJoined = MutableStateFlow(false)
    val isJoined: StateFlow<Boolean> = _isJoined.asStateFlow()
    
    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()
    
    private val _connectionType = MutableStateFlow<ConnectionType?>(null)
    val connectionType: StateFlow<ConnectionType?> = _connectionType.asStateFlow()
    
    private val _streamingMode = MutableStateFlow<AudioStreamingMode?>(null)
    val streamingMode: StateFlow<AudioStreamingMode?> = _streamingMode.asStateFlow()
    
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()
    
    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()
    
    private var notificationManager: NotificationManager? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): PartySessionService = this@PartySessionService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PartySessionService created")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_HOST_SESSION -> {
                val roomCode = intent.getStringExtra(EXTRA_ROOM_CODE)
                val connectionType = intent.getSerializableExtra(EXTRA_CONNECTION_TYPE) as? ConnectionType
                val streamingMode = intent.getSerializableExtra(EXTRA_STREAMING_MODE) as? AudioStreamingMode
                startHostSession(roomCode, connectionType, streamingMode)
            }
            ACTION_START_JOIN_SESSION -> {
                val roomCode = intent.getStringExtra(EXTRA_ROOM_CODE)
                val connectionType = intent.getSerializableExtra(EXTRA_CONNECTION_TYPE) as? ConnectionType
                startJoinSession(roomCode, connectionType)
            }
            ACTION_STOP_SESSION -> {
                stopSession()
            }
            ACTION_TOGGLE_STREAMING -> {
                toggleStreaming()
            }
        }
        
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PartySync Session",
                NotificationManager.IMPORTANCE_LOW // Low importance for less intrusive
            ).apply {
                description = "Shows current PartySync session status"
                setSound(null, null) // No sound
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun startHostSession(roomCode: String?, connectionType: ConnectionType?, streamingMode: AudioStreamingMode?) {
        Log.d(TAG, "Starting host session - Room: $roomCode, Connection: $connectionType, Mode: $streamingMode")
        
        _isHosting.value = true
        _isJoined.value = false
        _roomCode.value = roomCode
        _connectionType.value = connectionType
        _streamingMode.value = streamingMode
        
        startForegroundService()
    }
    
    private fun startJoinSession(roomCode: String?, connectionType: ConnectionType?) {
        Log.d(TAG, "Starting join session - Room: $roomCode, Connection: $connectionType")
        
        _isHosting.value = false
        _isJoined.value = true
        _roomCode.value = roomCode
        _connectionType.value = connectionType
        _streamingMode.value = null // Clients don't stream
        
        startForegroundService()
    }
    
    private fun stopSession() {
        Log.d(TAG, "Stopping session")
        
        _isHosting.value = false
        _isJoined.value = false
        _roomCode.value = null
        _connectionType.value = null
        _streamingMode.value = null
        _isStreaming.value = false
        _connectedClients.value = 0
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun toggleStreaming() {
        if (_isHosting.value) {
            _isStreaming.value = !_isStreaming.value
            updateNotification()
        }
    }
    
    fun updateStreamingState(isStreaming: Boolean) {
        _isStreaming.value = isStreaming
        updateNotification()
    }
    
    fun updateConnectedClients(count: Int) {
        _connectedClients.value = count
        updateNotification()
    }
    
    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun updateNotification() {
        val notification = createNotification()
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    private fun createNotification(): Notification {
        // Intent to open the app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Intent to stop session
        val stopIntent = Intent(this, PartySessionService::class.java).apply {
            action = ACTION_STOP_SESSION
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification content
        val title = if (_isHosting.value) {
            "Hosting: ${_roomCode.value ?: "Unknown"}"
        } else {
            "Joined: ${_roomCode.value ?: "Unknown"}"
        }
        
        val connectionInfo = _connectionType.value?.name ?: "Unknown"
        val streamingInfo = if (_isHosting.value) {
            val mode = _streamingMode.value?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "None"
            val status = if (_isStreaming.value) "Streaming" else "Stopped"
            val clients = _connectedClients.value
            "$mode • $status • $clients client${if (clients != 1) "s" else ""}"
        } else {
            "Connected via $connectionInfo"
        }
        
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // You might want a better icon
            .setContentTitle(title)
            .setContentText(streamingInfo)
            .setSubText(connectionInfo)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
        
        // Add streaming toggle for hosts
        if (_isHosting.value) {
            val toggleIntent = Intent(this, PartySessionService::class.java).apply {
                action = ACTION_TOGGLE_STREAMING
            }
            val togglePendingIntent = PendingIntent.getService(
                this, 2, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val toggleText = if (_isStreaming.value) "Pause" else "Resume"
            val toggleIcon = if (_isStreaming.value) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            
            builder.addAction(toggleIcon, toggleText, togglePendingIntent)
        }
        
        return builder.build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PartySessionService destroyed")
    }
}
