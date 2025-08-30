package io.github.gauravyad69.partysync.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gauravyad69.partysync.audio.ExoPlayerAudioStreamer
import io.github.gauravyad69.partysync.audio.SyncedPlayback
import io.github.gauravyad69.partysync.network.ConnectionState
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.network.NetworkConnection
import io.github.gauravyad69.partysync.network.NetworkDevice
import io.github.gauravyad69.partysync.network.NetworkManager
import io.github.gauravyad69.partysync.sync.SyncManager
import io.github.gauravyad69.partysync.utils.PermissionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class JoinUiState(
    val availableHosts: List<NetworkDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val connectedHostName: String = "",
    val playbackState: SyncedPlayback? = null,
    val hasPermissions: Boolean = false
)

class JoinViewModel(application: Application) : AndroidViewModel(application) {
    
    private val networkManager = NetworkManager(application)
    private val audioStreamer = ExoPlayerAudioStreamer(application)
    private val permissionHandler = PermissionHandler(application)
    
    private var currentConnection: NetworkConnection? = null
    private var syncManager: SyncManager? = null
    
    private val _uiState = MutableStateFlow(JoinUiState())
    val uiState: StateFlow<JoinUiState> = _uiState.asStateFlow()
    
    init {
        checkPermissions()
        observePlaybackState()
    }
    
    private fun checkPermissions() {
        _uiState.value = _uiState.value.copy(
            hasPermissions = permissionHandler.hasAllPermissions()
        )
    }
    
    fun onPermissionsResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermissions = granted)
    }
    
    fun startScanning(connectionType: ConnectionType = ConnectionType.WiFiDirect) {
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
                            isConnected = true,
                            connectedHostName = device.name
                        )
                        
                        syncManager = SyncManager(
                            networkConnection = connection,
                            audioStreamer = audioStreamer,
                            isHost = false,
                            scope = viewModelScope
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error(e.message ?: "Connection failed")
                )
            }
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            networkManager.disconnect()
            currentConnection = null
            syncManager = null
            
            _uiState.value = _uiState.value.copy(
                isConnected = false,
                connectedHostName = "",
                connectionState = ConnectionState.Disconnected,
                availableHosts = emptyList()
            )
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
    
    fun requestPermissions() {
        // This would typically trigger a permission request in the UI
        // For now, we'll just recheck permissions
        checkPermissions()
    }
    
    fun disconnect() {
        viewModelScope.launch {
            try {
                syncManager = null
                currentConnection?.disconnect()
                currentConnection = null
                
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    connectedHostName = "",
                    connectionState = ConnectionState.Disconnected
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error("Disconnect failed: ${e.message}")
                )
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