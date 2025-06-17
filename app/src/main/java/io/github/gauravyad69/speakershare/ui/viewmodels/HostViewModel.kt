package io.github.gauravyad69.speakershare.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gauravyad69.speakershare.audio.AudioTrack
import io.github.gauravyad69.speakershare.audio.ExoPlayerAudioStreamer
import io.github.gauravyad69.speakershare.audio.SyncedPlayback
import io.github.gauravyad69.speakershare.network.ConnectionState
import io.github.gauravyad69.speakershare.network.ConnectionType
import io.github.gauravyad69.speakershare.network.NetworkConnection
import io.github.gauravyad69.speakershare.network.NetworkManager
import io.github.gauravyad69.speakershare.sync.SyncManager
import io.github.gauravyad69.speakershare.utils.DeviceConfigManager
import io.github.gauravyad69.speakershare.utils.DeviceStatus
import io.github.gauravyad69.speakershare.utils.PermissionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class HostUiState(
    val roomName: String = "",
    val selectedConnectionType: ConnectionType = ConnectionType.WiFiDirect,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isHosting: Boolean = false,
    val connectedDevices: List<String> = emptyList(),
    val currentTrack: AudioTrack? = null,
    val playbackState: SyncedPlayback? = null,
    val hasPermissions: Boolean = false
)

class HostViewModel(application: Application) : AndroidViewModel(application) {
    
    private val networkManager = NetworkManager(application)
    private val audioStreamer = ExoPlayerAudioStreamer(application)
    private val permissionHandler = PermissionHandler(application)
    private val deviceConfigManager = DeviceConfigManager(application) // Add this
    
    private var currentConnection: NetworkConnection? = null
    private var syncManager: SyncManager? = null
    
    private val _uiState = MutableStateFlow(HostUiState())
    val uiState: StateFlow<HostUiState> = _uiState.asStateFlow()
    
    init {
        checkPermissions()
        observePlaybackState()
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
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting)
                
                // Step 1: Prepare device for the selected connection type
                val preparationResult = when (_uiState.value.selectedConnectionType) {
                    ConnectionType.LocalHotspot -> {
                        Log.d("HostViewModel", "Preparing device for Local Hotspot...")
                        deviceConfigManager.prepareForLocalHotspot()
                    }
                    ConnectionType.WiFiDirect -> {
                        Log.d("HostViewModel", "Preparing device for WiFi Direct...")
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
                val connection = networkManager.createConnection(_uiState.value.selectedConnectionType)
                currentConnection = connection
                
                observeConnectionState(connection)
                
                val roomName = _uiState.value.roomName.ifEmpty { "Room_${System.currentTimeMillis()}" }
                Log.d("HostViewModel", "Attempting to start host with connection type: ${_uiState.value.selectedConnectionType}")
                
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
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Connecting)
                
                val result = when (_uiState.value.selectedConnectionType) {
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
    
    fun stopHosting() {
        viewModelScope.launch {
            networkManager.disconnect()
            currentConnection = null
            syncManager = null
            
            _uiState.value = _uiState.value.copy(
                isHosting = false,
                connectionState = ConnectionState.Disconnected,
                connectedDevices = emptyList()
            )
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
        audioStreamer.release()
        viewModelScope.launch {
            networkManager.disconnect()
        }
    }
}