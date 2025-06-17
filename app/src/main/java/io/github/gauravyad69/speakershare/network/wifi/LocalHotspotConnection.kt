package io.github.gauravyad69.speakershare.network.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
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

class LocalHotspotConnection(
    private val context: Context
) : NetworkConnection {
    
    companion object {
        private const val TAG = "LocalHotspotConnection"
        private const val SERVER_PORT = 8889
        private const val HOTSPOT_SSID = "SpeakerShare_"
        private const val HOTSPOT_PASSWORD = "speakershare123"
    }
    
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()
    
    override val connectionType: ConnectionType = ConnectionType.LocalHotspot
    
    private var isHost = false
    private var currentSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    private fun isWiFiInCompatibleState(): Boolean {
        return try {
            // Check if WiFi is enabled and not connected to a network
            val isEnabled = wifiManager.isWifiEnabled
            val connectionInfo = wifiManager.connectionInfo
            val isConnected = connectionInfo != null && connectionInfo.networkId != -1
            
            Log.d(TAG, "WiFi enabled: $isEnabled, connected: $isConnected")
            
            // For hotspot to work, WiFi should typically be off or not connected
            !isConnected
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi state", e)
            false
        }
    }
    
    override suspend fun startHost(roomName: String): Result<String> {
        return try {
            _connectionState.value = ConnectionState.Connecting
            isHost = true
            
            // Check WiFi state first
            if (!isWiFiInCompatibleState()) {
                val errorMsg = "WiFi is connected to a network. Please disconnect from WiFi to create a hotspot."
                Log.w(TAG, errorMsg)
                _connectionState.value = ConnectionState.Error(errorMsg)
                return Result.failure(Exception(errorMsg))
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Use WifiManager.LocalOnlyHotspotReservation for newer Android versions
                startLocalOnlyHotspot(roomName)
                // Don't immediately return success - wait for the callback
            } else {
                // For older versions, we'll simulate hotspot creation
                Log.w(TAG, "Android version too old for Local Hotspot, using fallback")
                _connectionState.value = ConnectionState.Connected
                startServer()
            }
            
            // Return success for now, but the actual result will be reported via connectionState
            Result.success("$HOTSPOT_SSID$roomName")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting hotspot", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun startLocalOnlyHotspot(roomName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                    Log.d(TAG, "Local hotspot started successfully")
                    _connectionState.value = ConnectionState.Connected
                    startServer()
                }
                
                override fun onStopped() {
                    Log.d(TAG, "Local hotspot stopped")
                    _connectionState.value = ConnectionState.Disconnected
                }
                
                override fun onFailed(reason: Int) {
                    val errorMsg = getHotspotErrorMessage(reason)
                    Log.e(TAG, "Failed to start local hotspot: $reason - $errorMsg")
                    _connectionState.value = ConnectionState.Error(errorMsg)
                }
            }, null)
        }
    }
    
    private fun getHotspotErrorMessage(reason: Int): String {
        return when (reason) {
            1 -> "No channel available for hotspot" // ERROR_NO_CHANNEL
            2 -> "Generic hotspot error" // ERROR_GENERIC  
            3 -> "WiFi is in incompatible mode. Please disconnect from WiFi networks and try again." // ERROR_INCOMPATIBLE_MODE
            4 -> "Tethering is not allowed on this device" // ERROR_TETHERING_DISALLOWED
            else -> "Unknown hotspot error (code: $reason)"
        }
    }
    
    override suspend fun discoverHosts(): Flow<List<NetworkDevice>> = callbackFlow {
        val scanResults = wifiManager.scanResults
        val devices = scanResults
            .filter { it.SSID.startsWith(HOTSPOT_SSID) }
            .map { result ->
                NetworkDevice(
                    id = result.BSSID,
                    name = result.SSID.removePrefix(HOTSPOT_SSID),
                    address = result.BSSID,
                    isHost = true
                )
            }
        
        trySend(devices)
        
        awaitClose {
            // Cleanup if needed
        }
    }
    
    override suspend fun connectToHost(device: NetworkDevice): Result<Unit> {
        return try {
            _connectionState.value = ConnectionState.Connecting
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                connectWithNetworkSpecifier(device)
            } else {
                connectLegacy(device)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to host", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            Result.failure(e)
        }
    }
    
    private fun connectWithNetworkSpecifier(device: NetworkDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid("$HOTSPOT_SSID${device.name}")
                .setWpa2Passphrase(HOTSPOT_PASSWORD)
                .build()
            
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Connected to hotspot network")
                    _connectionState.value = ConnectionState.Connected
                    connectToServer()
                }
                
                override fun onUnavailable() {
                    Log.e(TAG, "Network unavailable")
                    _connectionState.value = ConnectionState.Error("Network unavailable")
                }
            }
            
            connectivityManager.requestNetwork(request, networkCallback!!)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun connectLegacy(device: NetworkDevice) {
        val wifiConfig = WifiConfiguration().apply {
            SSID = "\"$HOTSPOT_SSID${device.name}\""
            preSharedKey = "\"$HOTSPOT_PASSWORD\""
        }
        
        val networkId = wifiManager.addNetwork(wifiConfig)
        if (networkId != -1) {
            wifiManager.disconnect()
            wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()
            
            _connectionState.value = ConnectionState.Connected
            connectToServer()
        } else {
            _connectionState.value = ConnectionState.Error("Failed to add network configuration")
        }
    }
    
    override suspend fun disconnect() {
        try {
            currentSocket?.close()
            serverSocket?.close()
            
            networkCallback?.let { callback ->
                connectivityManager.unregisterNetworkCallback(callback)
                networkCallback = null
            }
            
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
    
    private fun connectToServer() {
        Thread {
            try {
                currentSocket = Socket()
                currentSocket?.connect(InetSocketAddress("192.168.49.1", SERVER_PORT), 5000)
                Log.d(TAG, "Connected to server at 192.168.49.1:$SERVER_PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to server", e)
                _connectionState.value = ConnectionState.Error("Failed to connect to server")
            }
        }.start()
    }
}