package io.github.gauravyad69.partysync.network

import kotlinx.coroutines.flow.Flow

enum class ConnectionType(val displayName: String) {
    Bluetooth("Bluetooth"),
    WiFiDirect("WiFi Direct"),
    LocalHotspot("Local Hotspot")
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(
        val message: String = "Connecting...",
        val progress: Float = 0f // 0.0 to 1.0
    ) : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class NetworkDevice(
    val id: String,
    val name: String,
    val address: String,
    val isHost: Boolean = false
)

interface NetworkConnection {
    val connectionState: Flow<ConnectionState>
    val connectionType: ConnectionType
    
    suspend fun startHost(roomName: String): Result<String>
    suspend fun discoverHosts(): Flow<List<NetworkDevice>>
    suspend fun connectToHost(device: NetworkDevice): Result<Unit>
    suspend fun disconnect()
    suspend fun sendData(data: ByteArray): Result<Unit>
    fun receiveData(): Flow<ByteArray>
}