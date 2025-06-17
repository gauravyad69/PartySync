package io.github.gauravyad69.speakershare.network

import kotlinx.coroutines.flow.Flow

sealed class ConnectionType {
    object WiFiDirect : ConnectionType()
    object LocalHotspot : ConnectionType()
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
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