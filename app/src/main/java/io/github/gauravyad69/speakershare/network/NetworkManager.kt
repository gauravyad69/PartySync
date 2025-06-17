package io.github.gauravyad69.speakershare.network

import android.content.Context
import io.github.gauravyad69.speakershare.network.wifi.LocalHotspotConnection
import io.github.gauravyad69.speakershare.network.wifi.WiFiDirectConnection
import kotlinx.coroutines.flow.Flow

class NetworkManager(private val context: Context) {
    
    private var currentConnection: NetworkConnection? = null
    
    fun createConnection(type: ConnectionType): NetworkConnection {
        val connection = when (type) {
            ConnectionType.WiFiDirect -> WiFiDirectConnection(context)
            ConnectionType.LocalHotspot -> LocalHotspotConnection(context)
        }
        currentConnection = connection
        return connection
    }
    
    fun getCurrentConnection(): NetworkConnection? = currentConnection
    
    suspend fun disconnect() {
        currentConnection?.disconnect()
        currentConnection = null
    }
}