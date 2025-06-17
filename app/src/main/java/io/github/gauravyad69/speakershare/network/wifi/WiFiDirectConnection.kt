package io.github.gauravyad69.speakershare.network.wifi

import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import io.github.gauravyad69.speakershare.network.ConnectionState
import io.github.gauravyad69.speakershare.network.ConnectionType
import io.github.gauravyad69.speakershare.network.NetworkConnection
import io.github.gauravyad69.speakershare.network.NetworkDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WiFiDirectConnection(
    private val context: Context
) : NetworkConnection {
    
    private val manager: WifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }
    
    private val channel: Channel by lazy {
        manager.initialize(context, context.mainLooper, null)
    }
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()
    
    override val connectionType: ConnectionType = ConnectionType.WiFiDirect
    
    private var isHost = false
    private val discoveredDevices = mutableListOf<WifiP2pDevice>()
    
    override suspend fun startHost(roomName: String): Result<String> {
        return try {
            _connectionState.value = ConnectionState.Connecting
            
            // Create WiFi Direct group
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    isHost = true
                    _connectionState.value = ConnectionState.Connected
                }
                
                override fun onFailure(reason: Int) {
                    _connectionState.value = ConnectionState.Error("Failed to create group: $reason")
                }
            })
            
            Result.success("WiFi_Direct_$roomName")
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
    
    override suspend fun discoverHosts(): Flow<List<NetworkDevice>> {
        // Implement peer discovery
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Discovery started successfully
            }
            
            override fun onFailure(reason: Int) {
                _connectionState.value = ConnectionState.Error("Discovery failed: $reason")
            }
        })
        
        // Return flow of discovered devices (you'll need to implement broadcast receiver)
        TODO("Implement device discovery flow")
    }
    
    override suspend fun connectToHost(device: NetworkDevice): Result<Unit> {
        return try {
            val config = WifiP2pConfig().apply {
                deviceAddress = device.address
            }
            
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _connectionState.value = ConnectionState.Connected
                }
                
                override fun onFailure(reason: Int) {
                    _connectionState.value = ConnectionState.Error("Connection failed: $reason")
                }
            })
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun disconnect() {
        if (isHost) {
            manager.removeGroup(channel, null)
        } else {
            manager.cancelConnect(channel, null)
        }
        _connectionState.value = ConnectionState.Disconnected
    }
    
    override suspend fun sendData(data: ByteArray): Result<Unit> {
        // Implement data sending over WiFi Direct sockets
        TODO("Implement data sending")
    }
    
    override fun receiveData(): Flow<ByteArray> {
        // Implement data receiving
        TODO("Implement data receiving")
    }
}