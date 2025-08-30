package io.github.gauravyad69.partysync.session

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import io.github.gauravyad69.partysync.audio.AudioStreamingMode
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.service.PartySessionService
import kotlinx.coroutines.flow.StateFlow

/**
 * Manager for PartySync sessions
 * Handles session lifecycle and notification management
 */
class SessionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SessionManager"
    }
    
    private var sessionService: PartySessionService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d(TAG, "Session service connected")
            val binder = service as PartySessionService.LocalBinder
            sessionService = binder.getService()
            isBound = true
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.d(TAG, "Session service disconnected")
            sessionService = null
            isBound = false
        }
    }
    
    // Session state flows (delegated to service when available)
    val isHosting: StateFlow<Boolean>?
        get() = sessionService?.isHosting
    
    val isJoined: StateFlow<Boolean>?
        get() = sessionService?.isJoined
    
    val roomCode: StateFlow<String?>?
        get() = sessionService?.roomCode
    
    val connectionType: StateFlow<ConnectionType?>?
        get() = sessionService?.connectionType
    
    val streamingMode: StateFlow<AudioStreamingMode?>?
        get() = sessionService?.streamingMode
    
    val isStreaming: StateFlow<Boolean>?
        get() = sessionService?.isStreaming
    
    val connectedClients: StateFlow<Int>?
        get() = sessionService?.connectedClients
    
    /**
     * Start hosting a session
     */
    fun startHostSession(
        roomCode: String,
        connectionType: ConnectionType,
        streamingMode: AudioStreamingMode
    ) {
        Log.d(TAG, "Starting host session: $roomCode")
        
        val intent = Intent(context, PartySessionService::class.java).apply {
            action = PartySessionService.ACTION_START_HOST_SESSION
            putExtra(PartySessionService.EXTRA_ROOM_CODE, roomCode)
            putExtra(PartySessionService.EXTRA_CONNECTION_TYPE, connectionType)
            putExtra(PartySessionService.EXTRA_STREAMING_MODE, streamingMode)
        }
        
        context.startForegroundService(intent)
        bindToService()
    }
    
    /**
     * Start joining a session
     */
    fun startJoinSession(
        roomCode: String,
        connectionType: ConnectionType
    ) {
        Log.d(TAG, "Starting join session: $roomCode")
        
        val intent = Intent(context, PartySessionService::class.java).apply {
            action = PartySessionService.ACTION_START_JOIN_SESSION
            putExtra(PartySessionService.EXTRA_ROOM_CODE, roomCode)
            putExtra(PartySessionService.EXTRA_CONNECTION_TYPE, connectionType)
        }
        
        context.startForegroundService(intent)
        bindToService()
    }
    
    /**
     * Stop current session
     */
    fun stopSession() {
        Log.d(TAG, "Stopping session")
        
        val intent = Intent(context, PartySessionService::class.java).apply {
            action = PartySessionService.ACTION_STOP_SESSION
        }
        
        context.startService(intent)
        unbindFromService()
    }
    
    /**
     * Toggle streaming for host sessions
     */
    fun toggleStreaming() {
        if (sessionService?.isHosting?.value == true) {
            val intent = Intent(context, PartySessionService::class.java).apply {
                action = PartySessionService.ACTION_TOGGLE_STREAMING
            }
            context.startService(intent)
        }
    }
    
    /**
     * Update streaming state (called from ViewModel)
     */
    fun updateStreamingState(isStreaming: Boolean) {
        sessionService?.updateStreamingState(isStreaming)
    }
    
    /**
     * Update connected clients count (called from ViewModel)
     */
    fun updateConnectedClients(count: Int) {
        sessionService?.updateConnectedClients(count)
    }
    
    /**
     * Check if there's an active session
     */
    fun hasActiveSession(): Boolean {
        return sessionService?.let { service ->
            service.isHosting.value || service.isJoined.value
        } ?: false
    }
    
    /**
     * Get current session info for display
     */
    fun getCurrentSessionInfo(): SessionInfo? {
        return sessionService?.let { service ->
            if (service.isHosting.value || service.isJoined.value) {
                SessionInfo(
                    isHosting = service.isHosting.value,
                    roomCode = service.roomCode.value,
                    connectionType = service.connectionType.value,
                    streamingMode = service.streamingMode.value,
                    isStreaming = service.isStreaming.value,
                    connectedClients = service.connectedClients.value
                )
            } else null
        }
    }
    
    private fun bindToService() {
        if (!isBound) {
            val intent = Intent(context, PartySessionService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    private fun unbindFromService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            sessionService = null
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        unbindFromService()
    }
}

/**
 * Data class representing current session information
 */
data class SessionInfo(
    val isHosting: Boolean,
    val roomCode: String?,
    val connectionType: ConnectionType?,
    val streamingMode: AudioStreamingMode?,
    val isStreaming: Boolean,
    val connectedClients: Int
)
