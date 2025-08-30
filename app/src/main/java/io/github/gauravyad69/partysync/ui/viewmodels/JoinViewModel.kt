package io.github.gauravyad69.partysync.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gauravyad69.partysync.audio.AudioPlaybackManager
import io.github.gauravyad69.partysync.audio.AudioStreamProtocol
import io.github.gauravyad69.partysync.audio.ExoPlayerAudioStreamer
import io.github.gauravyad69.partysync.audio.PlaybackStats
import io.github.gauravyad69.partysync.audio.SyncedPlayback
import io.github.gauravyad69.partysync.network.ConnectionState
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.network.NetworkConnection
import io.github.gauravyad69.partysync.network.NetworkDevice
import io.github.gauravyad69.partysync.network.NetworkManager
import io.github.gauravyad69.partysync.session.SessionManager
import io.github.gauravyad69.partysync.sync.SyncManager
import io.github.gauravyad69.partysync.utils.PermissionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress

data class JoinUiState(
    val availableHosts: List<NetworkDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val connectedHostName: String = "",
    val connectedHostAddress: String = "",
    val playbackState: SyncedPlayback? = null,
    val hasPermissions: Boolean = false,
    // New audio playback states
    val isPlayingAudio: Boolean = false,
    val bufferLevel: Float = 0f,
    val playbackLatency: Long = 0L,
    val playbackStats: PlaybackStats? = null
)

class JoinViewModel(application: Application) : AndroidViewModel(application) {
    
    private val networkManager = NetworkManager(application)
    private val audioStreamer = ExoPlayerAudioStreamer(application)
    val permissionHandler = PermissionHandler(application)
    
    // New audio playback components
    private val audioPlaybackManager = AudioPlaybackManager()
    private val audioStreamProtocol = AudioStreamProtocol()
    
    // Session management
    private val sessionManager = SessionManager(application)
    
    private var currentConnection: NetworkConnection? = null
    private var syncManager: SyncManager? = null
    private var hostAudioAddress: InetSocketAddress? = null
    private var currentConnectionType: ConnectionType = ConnectionType.WiFiDirect
    
    private val _uiState = MutableStateFlow(JoinUiState())
    val uiState: StateFlow<JoinUiState> = _uiState.asStateFlow()
    
    init {
        checkPermissions()
        observePlaybackState()
        initializeAudioPlayback()
    }
    
    private fun initializeAudioPlayback() {
        // Initialize audio playback manager
        audioPlaybackManager.initialize()
        
        // Observe audio playback states
        viewModelScope.launch {
            audioPlaybackManager.isPlaying.collectLatest { isPlaying ->
                _uiState.value = _uiState.value.copy(isPlayingAudio = isPlaying)
            }
        }
        
        viewModelScope.launch {
            audioPlaybackManager.bufferLevel.collectLatest { level ->
                _uiState.value = _uiState.value.copy(bufferLevel = level)
            }
        }
        
        viewModelScope.launch {
            audioPlaybackManager.playbackLatency.collectLatest { latency ->
                _uiState.value = _uiState.value.copy(playbackLatency = latency)
            }
        }
        
        // Set up audio stream protocol for receiving
        audioStreamProtocol.setOnAudioPacketReceived { packet, hostId ->
            // Add received audio packet to playback buffer
            audioPlaybackManager.addAudioPacket(packet)
        }
        
        Log.d("JoinViewModel", "Audio playback initialization completed")
    }
    
    private fun checkPermissions() {
        _uiState.value = _uiState.value.copy(
            hasPermissions = permissionHandler.hasAllPermissions()
        )
    }
    
    fun onPermissionsResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermissions = granted)
    }
    
    // New audio playback functions
    
    /**
     * Start receiving and playing audio stream from host
     */
    fun startAudioPlayback(hostAddress: String, port: Int = 8889) {
        if (!_uiState.value.hasPermissions) {
            Log.w("JoinViewModel", "Cannot start audio playback - missing permissions")
            return
        }
        
        if (!_uiState.value.isConnected) {
            Log.w("JoinViewModel", "Cannot start audio playback - not connected to host")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Parse host address on background thread
                hostAudioAddress = InetSocketAddress(hostAddress, port)
                
                // Start audio stream client
                if (!audioStreamProtocol.startAsClient()) {
                    Log.e("JoinViewModel", "Failed to start audio stream client")
                    return@launch
                }
                
                // Register with the server
                audioStreamProtocol.registerWithServer(hostAudioAddress!!)
                
                // Set up packet reception
                audioStreamProtocol.setOnAudioPacketReceived { packet, _ ->
                    audioPlaybackManager.addAudioPacket(packet)
                }
                
                // Start audio playback
                audioPlaybackManager.startPlayback()
                
                Log.d("JoinViewModel", "Audio playback started for host: $hostAddress:$port")
                
                // Update UI state
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isPlayingAudio = true)
                }
            } catch (e: Exception) {
                Log.e("JoinViewModel", "Failed to start audio playback", e)
            }
        }
    }
    
    /**
     * Stop audio playback
     */
    fun stopAudioPlayback() {
        audioPlaybackManager.stopPlayback()
        audioStreamProtocol.stop()
        hostAudioAddress = null
        Log.d("JoinViewModel", "Audio playback stopped")
    }
    
    /**
     * Restart audio playback with the currently connected host
     */
    fun restartAudioPlayback() {
        val hostAddress = _uiState.value.connectedHostAddress
        if (hostAddress.isNotEmpty()) {
            startAudioPlayback(hostAddress)
        } else {
            Log.w("JoinViewModel", "No host address available for restart")
        }
    }
    
    /**
     * Clear audio buffer (useful for resync)
     */
    fun clearAudioBuffer() {
        audioPlaybackManager.clearBuffer()
        Log.d("JoinViewModel", "Audio buffer cleared")
    }
    
    /**
     * Get current playback statistics
     */
    fun getPlaybackStats(): PlaybackStats {
        return audioPlaybackManager.getPlaybackStats()
    }
    
    /**
     * Request permissions for audio recording
     */
    fun requestPermissions() {
        checkPermissions()
    }
    
    // Existing functions
    
    fun startScanning(connectionType: ConnectionType = ConnectionType.WiFiDirect) {
        // Store the current connection type
        currentConnectionType = connectionType
        
        if (!permissionHandler.hasAllPermissions()) {
            return
        }
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isScanning = true)
                
                val connection = networkManager.createConnection(connectionType)
                currentConnection = connection
                
                observeConnectionState(connection)
                
                connection.discoverHosts().collectLatest { hosts ->
                    _uiState.value = _uiState.value.copy(
                        availableHosts = hosts,
                        isScanning = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    connectionState = ConnectionState.Error(e.message ?: "Scan failed")
                )
            }
        }
    }
    
    fun joinHost(device: NetworkDevice) {
        viewModelScope.launch {
            try {
                currentConnection?.let { connection ->
                    val result = connection.connectToHost(device)
                    
                    if (result.isSuccess) {
                        _uiState.value = _uiState.value.copy(
                            connectionState = ConnectionState.Connected,
                            isConnected = true,
                            connectedHostName = device.name,
                            connectedHostAddress = device.address
                        )                        // Start session notification
                        sessionManager.startJoinSession(
                            roomCode = device.name, // Using device name as room identifier
                            connectionType = currentConnectionType
                        )
                        
                        syncManager = SyncManager(
                            networkConnection = connection,
                            audioStreamer = audioStreamer,
                            isHost = false,
                            scope = viewModelScope
                        )
                        
                        // Start audio playback automatically when connected
                        // Assume audio is streamed on the same IP as the device
                        startAudioPlayback(device.address)
                        
                        Log.d("JoinViewModel", "Successfully joined host: ${device.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("JoinViewModel", "Failed to join host", e)
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error(e.message ?: "Connection failed")
                )
            }
        }
    }
    
    fun disconnect() {
        Log.d("JoinViewModel", "Disconnecting from host")
        
        // Stop audio playback first
        stopAudioPlayback()
        
        viewModelScope.launch {
            networkManager.disconnect()
            currentConnection = null
            syncManager = null
            
            _uiState.value = _uiState.value.copy(
                isConnected = false,
                connectedHostName = "",
                connectionState = ConnectionState.Disconnected,
                availableHosts = emptyList(),
                isPlayingAudio = false,
                bufferLevel = 0f,
                playbackLatency = 0L
            )
            
            // Stop session notification
            sessionManager.stopSession()
        }
    }
    
    fun refreshHosts() {
        _uiState.value = _uiState.value.copy(availableHosts = emptyList())
        startScanning()
    }
    
    private fun observeConnectionState(connection: NetworkConnection) {
        viewModelScope.launch {
            connection.connectionState.collectLatest { state: ConnectionState ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
    }
    
    private fun observePlaybackState() {
        viewModelScope.launch {
            audioStreamer.getPlaybackState().collectLatest { playback: SyncedPlayback ->
                _uiState.value = _uiState.value.copy(playbackState = playback)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Clean up audio playback resources
        audioPlaybackManager.release()
        audioStreamProtocol.stop()
        
        // Clean up existing resources
        audioStreamer.release()
        sessionManager.cleanup()
        viewModelScope.launch {
            networkManager.disconnect()
        }
        
        Log.d("JoinViewModel", "ViewModel cleared and resources released")
    }
}