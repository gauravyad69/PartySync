package io.github.gauravyad69.partysync.network

import android.content.Context
import io.github.gauravyad69.partysync.network.bluetooth.BluetoothConnection
import io.github.gauravyad69.partysync.network.wifi.LocalHotspotConnection
import io.github.gauravyad69.partysync.network.wifi.WiFiDirectConnection
import kotlinx.coroutines.flow.Flow

class NetworkManager(private val context: Context) {
    
    private var currentConnection: NetworkConnection? = null
    
    fun createConnection(type: ConnectionType): NetworkConnection {
        val connection = when (type) {
            ConnectionType.Bluetooth -> BluetoothConnection(context)
            ConnectionType.WiFiDirect -> WiFiDirectConnection(context)
            ConnectionType.LocalHotspot -> LocalHotspotConnection(context)
        }
        currentConnection = connection
        return connection
    }
    
    fun getCurrentConnection(): NetworkConnection? = currentConnection
    
    fun hasActiveConnection(): Boolean {
        return currentConnection?.let { connection ->
            when (connection.getConnectionState()) {
                is ConnectionState.Connected, is ConnectionState.Connecting -> true
                else -> false
            }
        } ?: false
    }
    
    suspend fun disconnect() {
        currentConnection?.disconnect()
        currentConnection = null
    }
}