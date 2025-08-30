package io.github.gauravyad69.partysync.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gauravyad69.partysync.audio.AudioCaptureManager
import io.github.gauravyad69.partysync.audio.AudioStreamProtocol
import io.github.gauravyad69.partysync.audio.AudioStreamingConfig
import io.github.gauravyad69.partysync.audio.AudioStreamingManager
import io.github.gauravyad69.partysync.audio.AudioStreamingMode
import io.github.gauravyad69.partysync.audio.AudioTrack
import io.github.gauravyad69.partysync.audio.ExoPlayerAudioStreamer
import io.github.gauravyad69.partysync.audio.SyncedPlayback
import io.github.gauravyad69.partysync.network.ConnectionState
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.network.NetworkConnection
import io.github.gauravyad69.partysync.network.NetworkManager
import io.github.gauravyad69.partysync.sync.SyncManager
import io.github.gauravyad69.partysync.utils.DeviceConfigManager
import io.github.gauravyad69.partysync.utils.DeviceStatus
import io.github.gauravyad69.partysync.utils.PermissionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class HostUiState(
    val roomName: String = "",
    val selectedConnectionType: ConnectionType = ConnectionType.Bluetooth,
    val selectedStreamingMode: AudioStreamingMode = AudioStreamingMode.MICROPHONE,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isHosting: Boolean = false,
    val connectedDevices: List<String> = emptyList(),
    val currentTrack: AudioTrack? = null,
    val playbackState: SyncedPlayback? = null,
    val hasPermissions: Boolean = false,
    // New audio streaming states
    val isCapturingAudio: Boolean = false,
    val audioLevel: Float = 0f,
    val streamingClients: Set<String> = emptySet(),
    val canChangeMode: Boolean = true // Can change mode when not actively streaming
)

class HostViewModel(application: Application) : AndroidViewModel(application) {
    
    private val networkManager = NetworkManager(application)
    private val audioStreamer = ExoPlayerAudioStreamer(application)
    private val permissionHandler = PermissionHandler(application)
    private val deviceConfigManager = DeviceConfigManager(application)
    
    // New audio streaming components
    private val audioCaptureManager = AudioCaptureManager()
    private val audioStreamProtocol = AudioStreamProtocol()
    private val audioStreamingManager = AudioStreamingManager(application, audioStreamProtocol)
    
    private var currentConnection: NetworkConnection? = null
    private var syncManager: SyncManager? = null
    
    private val _uiState = MutableStateFlow(HostUiState())
    val uiState: StateFlow<HostUiState> = _uiState.asStateFlow()
    
    init {
        checkPermissions()
        observePlaybackState()
        initializeAudioStreaming()
        checkForExistingConnections()
    }
    
    /**
     * Check if there are existing active connections from previous sessions
     */
    private fun checkForExistingConnections() {
        viewModelScope.launch {
            try {
                Log.d("HostViewModel", "Checking for existing connections...")
                
                // Check if NetworkManager has any active connections
                val hasActiveConnection = networkManager.hasActiveConnection()
                if (hasActiveConnection) {
                    Log.w("HostViewModel", "Found existing active connection - cleaning up...")
                    cleanupAllConnections()
                }
                
                // Check if audio stream server is already running
                val isAudioServerActive = audioStreamProtocol.isServerRunning()
                if (isAudioServerActive) {
                    Log.w("HostViewModel", "Found existing audio server - stopping...")
                    audioStreamProtocol.stop()
                }
                
                // Check if any streaming managers are active
                if (audioStreamingManager.isStreaming()) {
                    Log.w("HostViewModel", "Found existing audio streaming - stopping...")
                    audioStreamingManager.stopStreaming()
                }
                
                Log.d("HostViewModel", "Connection cleanup completed")
                
            } catch (e: Exception) {
                Log.e("HostViewModel", "Error during connection cleanup", e)
            }
        }
    }
    
    /**
     * Clean up all connections and streaming
     */
    private suspend fun cleanupAllConnections() {
        try {
            // Stop audio streaming first
            audioStreamingManager.stopStreaming()
            audioStreamProtocol.stop()
            
            // Disconnect network connections
            networkManager.disconnect()
            
            // Reset local state
            currentConnection = null
            syncManager = null
            
            _uiState.value = _uiState.value.copy(
                isHosting = false,
                connectionState = ConnectionState.Disconnected,
                connectedDevices = emptyList(),
                isCapturingAudio = false,
                canChangeMode = true,
                audioLevel = 0f,
                streamingClients = emptySet()
            )
            
            Log.d("HostViewModel", "All connections cleaned up successfully")
        } catch (e: Exception) {
            Log.e("HostViewModel", "Error during cleanup", e)
        }
    }
    
    private fun initializeAudioStreaming() {
        // Initialize audio capture manager
        audioCaptureManager.initialize()
        
        // Observe audio streaming states
        viewModelScope.launch {
            audioCaptureManager.isCapturing.collectLatest { isCapturing ->
                _uiState.value = _uiState.value.copy(isCapturingAudio = isCapturing)
            }
        }
        
        viewModelScope.launch {
            audioCaptureManager.audioLevel.collectLatest { level ->
                _uiState.value = _uiState.value.copy(audioLevel = level)
            }
        }
        
        viewModelScope.launch {
            audioStreamProtocol.connectedClients.collectLatest { clients ->
                _uiState.value = _uiState.value.copy(streamingClients = clients)
            }
        }
        
        // Set up audio stream protocol callbacks
        audioStreamProtocol.setOnClientConnected { clientId ->
            Log.d("HostViewModel", "Client connected for audio streaming: $clientId")
        }
        
        audioStreamProtocol.setOnClientDisconnected { clientId ->
            Log.d("HostViewModel", "Client disconnected from audio streaming: $clientId")
        }
    }
    
    // Add this function to get device status
    fun getDeviceStatus(): DeviceStatus {
        return deviceConfigManager.getDeviceStatus()
    }
    
    // Update startHosting to prepare device automatically
    fun startHosting() {
        Log.d("HostViewModel", "startHosting called - hasPermissions: ${_uiState.value.hasPermissions}")
        
        if (!permissionHandler.hasAllPermissions()) {
            Log.w("HostViewModel", "Cannot start hosting - missing permissions")
            checkPermissions()
            return
        }
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting("Initializing...", 0.1f))
                
                // Step 0: Clean up any existing connections first
                Log.d("HostViewModel", "Cleaning up existing connections before starting...")
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting("Cleaning up existing connections...", 0.2f))
                cleanupAllConnections()
                delay(500) // Give cleanup time to complete
                
                // Step 1: Prepare device for the selected connection type
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting("Preparing device...", 0.3f))
                val preparationResult = when (_uiState.value.selectedConnectionType) {
                    ConnectionType.Bluetooth -> {
                        Log.d("HostViewModel", "Preparing device for Bluetooth...")
                        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting("Configuring Bluetooth...", 0.4f))
                        deviceConfigManager.prepareForBluetooth()
                    }
                    ConnectionType.LocalHotspot -> {
                        Log.d("HostViewModel", "Preparing device for Local Hotspot...")
                        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting("Setting up WiFi hotspot...", 0.4f))
                        deviceConfigManager.prepareForLocalHotspot()
                    }
                    ConnectionType.WiFiDirect -> {
                        Log.d("HostViewModel", "Preparing device for WiFi Direct...")
                        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting("Disconnecting from WiFi...", 0.4f))
                        deviceConfigManager.prepareForWiFiDirect()
                    }
                }
                
                if (preparationResult.isFailure) {
                    val error = preparationResult.exceptionOrNull()
                    Log.e("HostViewModel", "Device preparation failed", error)
                    _uiState.value = _uiState.value.copy(
                        connectionState = ConnectionState.Error(error?.message ?: "Failed to prepare device")
                    )
                    return@launch
                }
                
                // Step 2: Create connection and start hosting
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting("Creating connection...", 0.6f))
                val connection = networkManager.createConnection(_uiState.value.selectedConnectionType)
                currentConnection = connection
                
                observeConnectionState(connection)
                
                val roomName = _uiState.value.roomName.ifEmpty { "Room_${System.currentTimeMillis()}" }
                Log.d("HostViewModel", "Attempting to start host with connection type: ${_uiState.value.selectedConnectionType}")
                
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting(
                    when (_uiState.value.selectedConnectionType) {
                        ConnectionType.Bluetooth -> "Starting Bluetooth server..."
                        ConnectionType.WiFiDirect -> "Creating WiFi Direct group..."
                        ConnectionType.LocalHotspot -> "Starting WiFi hotspot..."
                    }, 0.8f
                ))
                
                val result = connection.startHost(roomName)
                
                if (result.isSuccess) {
                    Log.d("HostViewModel", "Host started successfully")
                    _uiState.value = _uiState.value.copy(isHosting = true)
                    
                    syncManager = SyncManager(
                        networkConnection = connection,
                        audioStreamer = audioStreamer,
                        isHost = true,
                        scope = viewModelScope
                    )
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("HostViewModel", "Failed to start host", error)
                    _uiState.value = _uiState.value.copy(
                        connectionState = ConnectionState.Error(error?.message ?: "Failed to start hosting")
                    )
                }
            } catch (e: Exception) {
                Log.e("HostViewModel", "Error starting host", e)
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error(e.message ?: "Unknown error")
                )
            }
        }
    }
    
    // Add function to manually prepare device
    fun prepareDevice() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting("Preparing device...", 0.5f))
                
                val result = when (_uiState.value.selectedConnectionType) {
                    ConnectionType.Bluetooth -> deviceConfigManager.prepareForBluetooth()
                    ConnectionType.LocalHotspot -> deviceConfigManager.prepareForLocalHotspot()
                    ConnectionType.WiFiDirect -> deviceConfigManager.prepareForWiFiDirect()
                }
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Disconnected)
                } else {
                    val error = result.exceptionOrNull()
                    _uiState.value = _uiState.value.copy(
                        connectionState = ConnectionState.Error(error?.message ?: "Failed to prepare device")
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error(e.message ?: "Unknown error")
                )
            }
        }
    }
    
     private fun checkPermissions() {
        val hasPermissions = permissionHandler.hasAllPermissions()
        val missingPermissions = permissionHandler.getMissingPermissions()
        
        Log.d("HostViewModel", "Android SDK: ${android.os.Build.VERSION.SDK_INT}")
        Log.d("HostViewModel", "Checking permissions: $hasPermissions")
        Log.d("HostViewModel", "Missing permissions: $missingPermissions")
        
        _uiState.value = _uiState.value.copy(
            hasPermissions = hasPermissions
        )
    }
    
      fun onPermissionsResult(granted: Boolean) {
        Log.d("HostViewModel", "Permission result received: $granted")
        
        // Always recheck permissions after the dialog
        checkPermissions()
        
        // Also update the state with the result
        _uiState.value = _uiState.value.copy(hasPermissions = granted)
        
        Log.d("HostViewModel", "UI State updated - hasPermissions: ${_uiState.value.hasPermissions}")
    }
      // Add a manual refresh function for debugging
    fun refreshPermissions() {
        Log.d("HostViewModel", "Manual permission refresh requested")
        checkPermissions()
    }
    
    fun updateRoomName(name: String) {
        _uiState.value = _uiState.value.copy(roomName = name)
    }
    
    fun updateConnectionType(type: ConnectionType) {
        _uiState.value = _uiState.value.copy(selectedConnectionType = type)
    }
    
    fun updateStreamingMode(mode: AudioStreamingMode) {
        if (!_uiState.value.canChangeMode) {
            Log.w("HostViewModel", "Cannot change streaming mode while actively streaming")
            return
        }
        
        _uiState.value = _uiState.value.copy(selectedStreamingMode = mode)
        
        // Update recommended connection types for this mode
        val recommendedConnections = mode.getRecommendedConnections()
        if (_uiState.value.selectedConnectionType !in recommendedConnections && recommendedConnections.isNotEmpty()) {
            // Auto-select the first recommended connection type
            _uiState.value = _uiState.value.copy(selectedConnectionType = recommendedConnections.first())
        }
    }
    
    fun canChangeStreamingMode(): Boolean {
        return _uiState.value.canChangeMode && !_uiState.value.isCapturingAudio
    }
    
    // New audio streaming functions
    
    /**
     * Start capturing and streaming audio based on selected mode
     */
    fun startAudioCapture() {
        if (!_uiState.value.hasPermissions) {
            Log.w("HostViewModel", "Cannot start audio capture - missing permissions")
            return
        }
        
        if (!_uiState.value.isHosting) {
            Log.w("HostViewModel", "Cannot start audio capture - not hosting")
            return
        }
        
        if (_uiState.value.isCapturingAudio) {
            Log.w("HostViewModel", "Audio capture already active")
            return
        }
        
        // Start streaming with the selected mode - AudioStreamingManager will handle server start
        val mode = _uiState.value.selectedStreamingMode
        val config = AudioStreamingConfig(
            mode = mode,
            connectionType = _uiState.value.selectedConnectionType,
            roomName = _uiState.value.roomName
        )
        
        val success = audioStreamingManager.startHostStreaming(config)
        if (!success) {
            Log.e("HostViewModel", "Failed to start audio streaming")
            return
        }
        
        // Update state - mode cannot be changed while streaming
        _uiState.value = _uiState.value.copy(
            isCapturingAudio = true,
            canChangeMode = false
        )
        
        Log.d("HostViewModel", "Audio capture and streaming started for mode: $mode")
    }
    
    /**
     * Stop audio capture and streaming
     */
    fun stopAudioCapture() {
        audioStreamingManager.stopStreaming()
        audioStreamProtocol.stop()
        
        // Update state - mode can be changed again
        _uiState.value = _uiState.value.copy(
            isCapturingAudio = false,
            canChangeMode = true,
            audioLevel = 0f
        )
        
        Log.d("HostViewModel", "Audio capture and streaming stopped")
    }
    
    /**
     * Get current audio streaming statistics
     */
    fun getAudioStreamStats() = audioStreamProtocol.getNetworkStats()
    
    /**
     * Get audio configuration info
     */
    fun getAudioConfig() = audioCaptureManager.getAudioConfig()
    
    // Existing functions
    
    fun stopHosting() {
        Log.d("HostViewModel", "Stopping hosting...")
        viewModelScope.launch {
            cleanupAllConnections()
        }
    }
    
    fun loadTrack(track: AudioTrack) {
        viewModelScope.launch {
            val result = audioStreamer.loadTrack(track)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(currentTrack = track)
            }
        }
    }
    
    fun playMusic() {
        viewModelScope.launch {
            syncManager?.sendPlayCommand()
        }
    }
    
    fun pauseMusic() {
        viewModelScope.launch {
            syncManager?.sendPauseCommand()
        }
    }
    
    fun seekTo(position: Long) {
        viewModelScope.launch {
            syncManager?.sendSeekCommand(position)
        }
    }
    
    // New streaming mode specific functions
    
    /**
     * Load and play audio file for synchronized playback (CUSTOM_PLAYER mode)
     */
    fun loadAndPlayAudioFile(filePath: String) {
        if (_uiState.value.selectedStreamingMode != AudioStreamingMode.CUSTOM_PLAYER) {
            Log.w("HostViewModel", "loadAndPlayAudioFile only available in CUSTOM_PLAYER mode")
            return
        }
        
        viewModelScope.launch {
            // For now, we'll use the music track functionality
            // This would need to be enhanced to support file loading
            audioStreamingManager.playMusic()
            
            _uiState.value = _uiState.value.copy(
                isCapturingAudio = true,
                canChangeMode = false
            )
        }
    }
    
    /**
     * Pause synchronized playback
     */
    fun pausePlayback() {
        if (_uiState.value.selectedStreamingMode == AudioStreamingMode.CUSTOM_PLAYER) {
            audioStreamingManager.pauseMusic()
        }
    }
    
    /**
     * Resume synchronized playback
     */
    fun resumePlayback() {
        if (_uiState.value.selectedStreamingMode == AudioStreamingMode.CUSTOM_PLAYER) {
            audioStreamingManager.playMusic()
        }
    }
    
    /**
     * Seek to position in synchronized playback
     */
    fun seekToPosition(positionMs: Long) {
        if (_uiState.value.selectedStreamingMode == AudioStreamingMode.CUSTOM_PLAYER) {
            audioStreamingManager.seekTo(positionMs)
        }
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
    fun switchToLocalHotspot() {
    Log.d("HostViewModel", "Switching to Local Hotspot due to WiFi Direct issues")
    updateConnectionType(ConnectionType.LocalHotspot)
    _uiState.value = _uiState.value.copy(
        selectedConnectionType = ConnectionType.LocalHotspot,
        connectionState = ConnectionState.Disconnected
    )
}
    // Add function to reset WiFi Direct state
    fun resetWiFiDirectState() {
        Log.d("HostViewModel", "Resetting WiFi Direct state")
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting("Resetting WiFi Direct...", 0.3f))
                
                // Reset WiFi Direct state and try again
                networkManager.disconnect()
                delay(1000)
                
                startHosting()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error(e.message ?: "Failed to reset WiFi Direct")
                )
            }
        }
    }

    // Add function to retry with WiFi reset
    fun retryWithWiFiReset() {
        Log.d("HostViewModel", "Retrying with WiFi reset")
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting("Retrying with WiFi reset...", 0.2f))
                
                // First try to prepare device, then start hosting
                val result = when (_uiState.value.selectedConnectionType) {
                    ConnectionType.Bluetooth -> deviceConfigManager.prepareForBluetooth()
                    ConnectionType.LocalHotspot -> deviceConfigManager.prepareForLocalHotspot()
                    ConnectionType.WiFiDirect -> deviceConfigManager.prepareForWiFiDirect()
                }
                
                if (result.isSuccess) {
                    delay(1000)
                    startHosting()
                } else {
                    val error = result.exceptionOrNull()
                    _uiState.value = _uiState.value.copy(
                        connectionState = ConnectionState.Error(error?.message ?: "Failed to prepare device")
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error(e.message ?: "Unknown error")
                )
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up audio streaming resources
        audioCaptureManager.release()
        audioStreamProtocol.stop()
        
        // Clean up existing resources
        audioStreamer.release()
        viewModelScope.launch {
            networkManager.disconnect()
        }
        
        Log.d("HostViewModel", "ViewModel cleared and resources released")
    }
}