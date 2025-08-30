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
            // Since connectionState is a Flow, we need to check if we have a connection
            // For simplicity, we'll just check if currentConnection is not null
            // and assume if it exists, it might be active
            true
        } ?: false
    }
    
    suspend fun disconnect() {
        currentConnection?.disconnect()
        currentConnection = null
    }
}