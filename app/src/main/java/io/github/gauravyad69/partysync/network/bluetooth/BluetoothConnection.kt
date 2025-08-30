package io.github.gauravyad69.partysync.network.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import io.github.gauravyad69.partysync.network.ConnectionState
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.network.NetworkConnection
import io.github.gauravyad69.partysync.network.NetworkDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.util.UUID

class BluetoothConnection(
    private val context: Context
) : NetworkConnection {
    
    companion object {
        private const val TAG = "BluetoothConnection"
        private const val SERVICE_NAME = "PartySync"
        // Standard UUID for SPP (Serial Port Profile) - ensures compatibility
        private val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val AUDIO_BUFFER_SIZE = 1024 * 4 // 4KB chunks for audio
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()
    
    override val connectionType: ConnectionType = ConnectionType.Bluetooth
    
    private var isHost = false
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private val connectedClients = mutableListOf<BluetoothSocket>()
    
    private var discoveryReceiver: BluetoothDiscoveryReceiver? = null
    private val _discoveredDevices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    
    init {
        if (!isBluetoothSupported()) {
            _connectionState.value = ConnectionState.Error("Bluetooth is not supported on this device")
        } else if (!isBluetoothEnabled()) {
            _connectionState.value = ConnectionState.Error("Bluetooth is not enabled")
        }
    }
    
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    override suspend fun startHost(roomName: String): Result<String> {
        return try {
            if (!checkBluetoothPermissions()) {
                return Result.failure(Exception("Bluetooth permissions not granted"))
            }
            
            _connectionState.value = ConnectionState.Connecting("Starting Bluetooth server...")
            isHost = true
            
            // Make device discoverable
            makeDeviceDiscoverable()
            
            // Start server socket to accept connections
            startBluetoothServer()
            
            Log.d(TAG, "Bluetooth host started with room: $roomName")
            _connectionState.value = ConnectionState.Connected
            
            Result.success("BT_$roomName")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Bluetooth host", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
    
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override suspend fun discoverHosts(): Flow<List<NetworkDevice>> = callbackFlow {
        try {
            if (!checkBluetoothPermissions()) {
                trySend(emptyList())
                return@callbackFlow
            }
            
            // Register discovery receiver
            discoveryReceiver = BluetoothDiscoveryReceiver { devices ->
                trySend(devices)
            }
            discoveryReceiver?.register(context)
            
            // Start discovery
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            
            if (!bluetoothAdapter?.startDiscovery()!!) {
                Log.e(TAG, "Failed to start Bluetooth discovery")
                trySend(emptyList())
            } else {
                Log.d(TAG, "Bluetooth discovery started")
            }
            
            awaitClose {
                bluetoothAdapter?.cancelDiscovery()
                discoveryReceiver?.unregister(context)
                discoveryReceiver = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during Bluetooth discovery", e)
            trySend(emptyList())
        }
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connectToHost(device: NetworkDevice): Result<Unit> {
        return try {
            if (!checkBluetoothPermissions()) {
                return Result.failure(Exception("Bluetooth permissions not granted"))
            }
            
            _connectionState.value = ConnectionState.Connecting("Connecting to Bluetooth device...")
            
            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                ?: return Result.failure(Exception("Invalid Bluetooth device"))
            
            // Cancel discovery to improve connection performance
            bluetoothAdapter.cancelDiscovery()
            
            // Create client socket
            clientSocket = bluetoothDevice.createRfcommSocketToServiceRecord(SERVICE_UUID)
            
            // Connect with timeout
            clientSocket?.connect()
            
            Log.d(TAG, "Connected to Bluetooth device: ${device.name}")
            _connectionState.value = ConnectionState.Connected
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Bluetooth device", e)
            _connectionState.value = ConnectionState.Error("Connection failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun disconnect() {
        try {
            // Close all connections
            connectedClients.forEach { socket ->
                try {
                    socket.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing client socket", e)
                }
            }
            connectedClients.clear()
            
            clientSocket?.close()
            clientSocket = null
            
            serverSocket?.close()
            serverSocket = null
            
            // Cancel discovery
            bluetoothAdapter?.cancelDiscovery()
            
            // Unregister receiver
            discoveryReceiver?.unregister(context)
            discoveryReceiver = null
            
            _connectionState.value = ConnectionState.Disconnected
            Log.d(TAG, "Bluetooth connection closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }
    
    override suspend fun sendData(data: ByteArray): Result<Unit> {
        return try {
            if (isHost) {
                // Send to all connected clients
                val failedClients = mutableListOf<BluetoothSocket>()
                
                connectedClients.forEach { socket ->
                    try {
                        socket.outputStream.write(data)
                        socket.outputStream.flush()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send data to client", e)
                        failedClients.add(socket)
                    }
                }
                
                // Remove failed connections
                failedClients.forEach { failedSocket ->
                    connectedClients.remove(failedSocket)
                    try {
                        failedSocket.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing failed socket", e)
                    }
                }
                
                Result.success(Unit)
            } else {
                // Send to host
                clientSocket?.let { socket ->
                    socket.outputStream.write(data)
                    socket.outputStream.flush()
                    Result.success(Unit)
                } ?: Result.failure(IOException("No active connection"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data", e)
            Result.failure(e)
        }
    }
    
    override fun receiveData(): Flow<ByteArray> = callbackFlow {
        try {
            if (isHost) {
                // Host receives from all clients
                connectedClients.forEach { socket ->
                    startReceivingFromSocket(socket) { data ->
                        trySend(data)
                    }
                }
            } else {
                // Client receives from host
                clientSocket?.let { socket ->
                    startReceivingFromSocket(socket) { data ->
                        trySend(data)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up data reception", e)
        }
        
        awaitClose {
            // Cleanup handled in disconnect()
        }
    }
    
    private fun startReceivingFromSocket(socket: BluetoothSocket, onDataReceived: (ByteArray) -> Unit) {
        Thread {
            try {
                val buffer = ByteArray(AUDIO_BUFFER_SIZE)
                while (socket.isConnected) {
                    try {
                        val bytesRead = socket.inputStream.read(buffer)
                        if (bytesRead > 0) {
                            onDataReceived(buffer.copyOf(bytesRead))
                        }
                    } catch (e: IOException) {
                        // Socket closed or connection lost
                        Log.d(TAG, "Socket disconnected, stopping data reception")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error receiving data from socket", e)
            }
        }.start()
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun makeDeviceDiscoverable() {
        try {
            // Note: Modern Android requires user interaction for discoverability
            // This will be handled in the UI layer with proper intent
            Log.d(TAG, "Device should be made discoverable through UI")
        } catch (e: Exception) {
            Log.w(TAG, "Could not make device discoverable", e)
        }
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startBluetoothServer() {
        Thread {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                Log.d(TAG, "Bluetooth server socket created")
                
                while (serverSocket != null) {
                    try {
                        val socket = serverSocket?.accept() // This blocks until connection
                        socket?.let {
                            Log.d(TAG, "New client connected: ${it.remoteDevice.address}")
                            connectedClients.add(it)
                            
                            // Start receiving data from this client
                            startReceivingFromSocket(it) { data ->
                                // Handle incoming data from client
                                Log.d(TAG, "Received ${data.size} bytes from client")
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error accepting client connection", e)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Bluetooth server", e)
            }
        }.start()
    }
    
    private fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }
    
    private fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 12 permissions
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }
}
