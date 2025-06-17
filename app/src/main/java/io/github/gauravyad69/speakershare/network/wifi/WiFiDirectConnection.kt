package io.github.gauravyad69.speakershare.network.wifi

import android.Manifest
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.util.Log
import androidx.annotation.RequiresPermission
import io.github.gauravyad69.speakershare.network.ConnectionState
import io.github.gauravyad69.speakershare.network.ConnectionType
import io.github.gauravyad69.speakershare.network.NetworkConnection
import io.github.gauravyad69.speakershare.network.NetworkDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class WiFiDirectConnection(
    private val context: Context
) : NetworkConnection {
    
    companion object {
        private const val TAG = "WiFiDirectConnection"
        private const val SERVER_PORT = 8888
    }
    
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
    private var currentSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var broadcastReceiver: WiFiDirectBroadcastReceiver? = null
    private val discoveredPeers = MutableStateFlow<List<NetworkDevice>>(emptyList())
    
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    override suspend fun startHost(roomName: String): Result<String> {
        return try {
            _connectionState.value = ConnectionState.Connecting
            
            registerBroadcastReceiver()
            
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Group created successfully")
                    isHost = true
                    _connectionState.value = ConnectionState.Connected
                    startServer()
                }
                
                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to create group: $reason")
                    _connectionState.value = ConnectionState.Error("Failed to create group: $reason")
                }
            })
            
            Result.success("WiFi_Direct_$roomName")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting host", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
    
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    override suspend fun discoverHosts(): Flow<List<NetworkDevice>> {
        registerBroadcastReceiver()
        
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started successfully")
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Discovery failed: $reason")
                _connectionState.value = ConnectionState.Error("Discovery failed: $reason")
            }
        })
        
        return discoveredPeers.asStateFlow()
    }
    
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    override suspend fun connectToHost(device: NetworkDevice): Result<Unit> {
        return try {
            _connectionState.value = ConnectionState.Connecting
            
            val config = WifiP2pConfig().apply {
                deviceAddress = device.address
            }
            
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Connection initiated successfully")
                }
                
                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Connection failed: $reason")
                    _connectionState.value = ConnectionState.Error("Connection failed: $reason")
                }
            })
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to host", e)
            Result.failure(e)
        }
    }
    
    override suspend fun disconnect() {
        try {
            currentSocket?.close()
            serverSocket?.close()
            
            if (isHost) {
                manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d(TAG, "Group removed successfully")
                    }
                    override fun onFailure(reason: Int) {
                        Log.e(TAG, "Failed to remove group: $reason")
                    }
                })
            } else {
                manager.cancelConnect(channel, null)
            }
            
            unregisterBroadcastReceiver()
            _connectionState.value = ConnectionState.Disconnected
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }
    
    override suspend fun sendData(data: ByteArray): Result<Unit> {
        return try {
            currentSocket?.let { socket ->
                socket.outputStream.write(data)
                socket.outputStream.flush()
                Result.success(Unit)
            } ?: Result.failure(IOException("No active connection"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data", e)
            Result.failure(e)
        }
    }
    
    override fun receiveData(): Flow<ByteArray> = callbackFlow {
        try {
            currentSocket?.let { socket ->
                val buffer = ByteArray(1024)
                while (!socket.isClosed) {
                    val bytesRead = socket.inputStream.read(buffer)
                    if (bytesRead > 0) {
                        trySend(buffer.copyOf(bytesRead))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving data", e)
        }
        
        awaitClose {
            currentSocket?.close()
        }
    }
    
    private fun registerBroadcastReceiver() {
        if (broadcastReceiver == null) {
            broadcastReceiver = WiFiDirectBroadcastReceiver(
                manager = manager,
                channel = channel,
                onPeersChanged = { peers ->
                    discoveredPeers.value = peers
                },
                onConnectionChanged = { info ->
                    handleConnectionInfo(info)
                },
                onThisDeviceChanged = { device ->
                    Log.d(TAG, "This device: ${device?.deviceName}")
                }
            )
            context.registerReceiver(broadcastReceiver, intentFilter)
        }
    }
    
    private fun unregisterBroadcastReceiver() {
        broadcastReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            broadcastReceiver = null
        }
    }
    
    private fun handleConnectionInfo(info: WifiP2pInfo?) {
        info?.let {
            Log.d(TAG, "Connection info - Group owner: ${it.isGroupOwner}, Group owner IP: ${it.groupOwnerAddress}")
            
            if (it.groupFormed) {
                _connectionState.value = ConnectionState.Connected
                
                if (!isHost && it.groupOwnerAddress != null) {
                    // Connect to host as client
                    connectToServer(it.groupOwnerAddress.hostAddress)
                }
            }
        }
    }
    
    private fun startServer() {
        Thread {
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                Log.d(TAG, "Server started on port $SERVER_PORT")
                
                while (!Thread.currentThread().isInterrupted) {
                    currentSocket = serverSocket?.accept()
                    Log.d(TAG, "Client connected: ${currentSocket?.remoteSocketAddress}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }.start()
    }
    
    private fun connectToServer(hostAddress: String?) {
        hostAddress?.let { address ->
            Thread {
                try {
                    currentSocket = Socket()
                    currentSocket?.connect(InetSocketAddress(address, SERVER_PORT), 5000)
                    Log.d(TAG, "Connected to server at $address:$SERVER_PORT")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to server", e)
                    _connectionState.value = ConnectionState.Error("Failed to connect to server")
                }
            }.start()
        }
    }
}