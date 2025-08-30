package io.github.gauravyad69.partysync.audio

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handles audio streaming over UDP network connections
 * Manages packet transmission, reception, and stream synchronization
 */
class AudioStreamProtocol {
    private val tag = "AudioStreamProtocol"
    
    // Network configuration
    companion object {
        const val DEFAULT_AUDIO_PORT = 8889  // Different port to avoid conflict with WiFi Direct
        const val MULTICAST_ADDRESS = "224.0.0.251" // mDNS multicast address
        const val MAX_CLIENTS = 16
        const val PACKET_TIMEOUT_MS = 5000L
        const val CLEANUP_INTERVAL_MS = 10000L
    }
    
    // Network components
    private var serverSocket: DatagramSocket? = null
    private var multicastSocket: MulticastSocket? = null
    private var isRunning = false
    private val protocolScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Stream management
    private val sequenceCounter = AtomicInteger(0)
    private val clientAddresses = ConcurrentHashMap<String, InetSocketAddress>()
    private val packetStats = ConcurrentHashMap<String, AudioPacketStats>()
    
    // State flows
    private val _streamStats = MutableStateFlow(AudioPacketStats())
    val streamStats: StateFlow<AudioPacketStats> = _streamStats
    
    private val _connectedClients = MutableStateFlow<Set<String>>(emptySet())
    val connectedClients: StateFlow<Set<String>> = _connectedClients
    
    // Callbacks
    private var onAudioPacketReceived: ((AudioPacket, String) -> Unit)? = null
    private var onClientConnected: ((String) -> Unit)? = null
    private var onClientDisconnected: ((String) -> Unit)? = null
    
    /**
     * Start as audio stream server (host)
     */
    fun startAsServer(port: Int = DEFAULT_AUDIO_PORT): Boolean {
        if (isRunning) {
            Log.w(tag, "Stream protocol already running")
            return true
        }
        
        return try {
            // Create UDP server socket
            serverSocket = DatagramSocket(port)
            serverSocket?.soTimeout = 1000 // 1 second timeout for receive
            
            // Create multicast socket for discovery
            multicastSocket = MulticastSocket(port + 1)
            val group = InetAddress.getByName(MULTICAST_ADDRESS)
            multicastSocket?.joinGroup(group)
            
            isRunning = true
            
            // Start server receive loop
            protocolScope.launch { serverReceiveLoop() }
            
            // Start cleanup task
            protocolScope.launch { cleanupTask() }
            
            Log.d(tag, "Audio stream server started on port $port")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start audio stream server", e)
            cleanup()
            false
        }
    }
    
    /**
     * Start as audio stream client (joiner)
     */
    fun startAsClient(): Boolean {
        if (isRunning) {
            Log.w(tag, "Stream protocol already running")
            return true
        }
        
        return try {
            // Create client socket (will bind to random port)
            serverSocket = DatagramSocket()
            serverSocket?.soTimeout = 1000
            
            isRunning = true
            
            // Start client receive loop
            protocolScope.launch { clientReceiveLoop() }
            
            Log.d(tag, "Audio stream client started")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start audio stream client", e)
            cleanup()
            false
        }
    }
    
    /**
     * Register client with server by sending a connection packet
     */
    fun registerWithServer(serverAddress: InetSocketAddress) {
        if (!isRunning || serverSocket == null) {
            Log.w(tag, "Cannot register - client not running")
            return
        }
        
        try {
            // Send a small registration packet to the server
            val registrationData = ByteArray(4) // Empty registration packet
            val packet = AudioPacket(
                sequenceNumber = 0, // Special sequence for registration
                timestamp = System.currentTimeMillis(),
                audioData = registrationData
            )
            
            val packetBytes = packet.toBytes()
            val datagramPacket = DatagramPacket(
                packetBytes,
                packetBytes.size,
                serverAddress.address,
                serverAddress.port
            )
            
            serverSocket?.send(datagramPacket)
            Log.d(tag, "Registered with server at $serverAddress")
            
        } catch (e: Exception) {
            Log.e(tag, "Failed to register with server", e)
        }
    }
    
    /**
     * Send audio packet to all connected clients (server mode)
     */
    fun broadcastAudioPacket(audioData: ByteArray) {
        if (!isRunning || serverSocket == null) {
            Log.w(tag, "Cannot broadcast - server not running")
            return
        }
        
        val packet = AudioPacket(
            sequenceNumber = sequenceCounter.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            audioData = audioData
        )
        
        val packetBytes = packet.toBytes()
        val clients = clientAddresses.values.toList()
        
        for (clientAddress in clients) {
            try {
                val datagramPacket = DatagramPacket(
                    packetBytes,
                    packetBytes.size,
                    clientAddress.address,
                    clientAddress.port
                )
                
                serverSocket?.send(datagramPacket)
                
                // Update stats
                val clientKey = "${clientAddress.address.hostAddress}:${clientAddress.port}"
                val stats = packetStats[clientKey] ?: AudioPacketStats()
                packetStats[clientKey] = stats.copy(totalPacketsSent = stats.totalPacketsSent + 1)
                
            } catch (e: Exception) {
                Log.e(tag, "Failed to send audio packet to $clientAddress", e)
            }
        }
        
        // Update global stats
        _streamStats.value = _streamStats.value.copy(
            totalPacketsSent = _streamStats.value.totalPacketsSent + clients.size
        )
    }
    
    /**
     * Send audio packet to specific server (client mode)
     */
    fun sendAudioPacket(audioData: ByteArray, serverAddress: InetSocketAddress) {
        if (!isRunning || serverSocket == null) {
            Log.w(tag, "Cannot send packet - client not running")
            return
        }
        
        val packet = AudioPacket(
            sequenceNumber = sequenceCounter.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            audioData = audioData
        )
        
        try {
            val packetBytes = packet.toBytes()
            val datagramPacket = DatagramPacket(
                packetBytes,
                packetBytes.size,
                serverAddress.address,
                serverAddress.port
            )
            
            serverSocket?.send(datagramPacket)
            
            _streamStats.value = _streamStats.value.copy(
                totalPacketsSent = _streamStats.value.totalPacketsSent + 1
            )
            
        } catch (e: Exception) {
            Log.e(tag, "Failed to send audio packet to server", e)
        }
    }
    
    /**
     * Server receive loop - handles incoming packets from clients
     */
    private suspend fun serverReceiveLoop() {
        val buffer = ByteArray(AudioPacket.MAX_PACKET_SIZE)
        
        while (isRunning) {
            try {
                val datagramPacket = DatagramPacket(buffer, buffer.size)
                serverSocket?.receive(datagramPacket)
                
                val clientAddress = datagramPacket.socketAddress as InetSocketAddress
                val clientKey = "${clientAddress.address.hostAddress}:${clientAddress.port}"
                
                // Register new client
                if (!clientAddresses.containsKey(clientKey)) {
                    clientAddresses[clientKey] = clientAddress
                    _connectedClients.value = clientAddresses.keys.toSet()
                    onClientConnected?.invoke(clientKey)
                    Log.d(tag, "New client connected: $clientKey")
                }
                
                // Parse audio packet
                val packetData = buffer.copyOf(datagramPacket.length)
                val audioPacket = AudioPacket.fromBytes(packetData)
                
                if (audioPacket != null && audioPacket.isValid()) {
                    onAudioPacketReceived?.invoke(audioPacket, clientKey)
                    
                    // Update client stats
                    val stats = packetStats[clientKey] ?: AudioPacketStats()
                    packetStats[clientKey] = stats.copy(
                        totalPacketsReceived = stats.totalPacketsReceived + 1,
                        lastSequenceNumber = audioPacket.sequenceNumber,
                        lastTimestamp = audioPacket.timestamp
                    )
                } else {
                    Log.w(tag, "Received invalid audio packet from $clientKey")
                }
                
            } catch (e: SocketTimeoutException) {
                // Normal timeout, continue loop
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(tag, "Error in server receive loop", e)
                }
            }
        }
    }
    
    /**
     * Client receive loop - handles incoming packets from server
     */
    private suspend fun clientReceiveLoop() {
        val buffer = ByteArray(AudioPacket.MAX_PACKET_SIZE)
        
        while (isRunning) {
            try {
                val datagramPacket = DatagramPacket(buffer, buffer.size)
                serverSocket?.receive(datagramPacket)
                
                val serverAddress = datagramPacket.socketAddress as InetSocketAddress
                val serverKey = "${serverAddress.address.hostAddress}:${serverAddress.port}"
                
                val packetData = buffer.copyOf(datagramPacket.length)
                val audioPacket = AudioPacket.fromBytes(packetData)
                
                if (audioPacket != null && audioPacket.isValid()) {
                    onAudioPacketReceived?.invoke(audioPacket, serverKey)
                    
                    _streamStats.value = _streamStats.value.copy(
                        totalPacketsReceived = _streamStats.value.totalPacketsReceived + 1,
                        lastSequenceNumber = audioPacket.sequenceNumber,
                        lastTimestamp = audioPacket.timestamp
                    )
                } else {
                    Log.w(tag, "Received invalid audio packet from server")
                }
                
            } catch (e: SocketTimeoutException) {
                // Normal timeout, continue loop
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(tag, "Error in client receive loop", e)
                }
            }
        }
    }
    
    /**
     * Cleanup disconnected clients
     */
    private suspend fun cleanupTask() {
        while (isRunning) {
            delay(CLEANUP_INTERVAL_MS)
            
            val currentTime = System.currentTimeMillis()
            val disconnectedClients = mutableListOf<String>()
            
            for ((clientKey, stats) in packetStats) {
                if (currentTime - stats.lastTimestamp > PACKET_TIMEOUT_MS) {
                    disconnectedClients.add(clientKey)
                }
            }
            
            // Remove disconnected clients
            for (clientKey in disconnectedClients) {
                clientAddresses.remove(clientKey)
                packetStats.remove(clientKey)
                onClientDisconnected?.invoke(clientKey)
                Log.d(tag, "Client disconnected due to timeout: $clientKey")
            }
            
            if (disconnectedClients.isNotEmpty()) {
                _connectedClients.value = clientAddresses.keys.toSet()
            }
        }
    }
    
    /**
     * Set callback for received audio packets
     */
    fun setOnAudioPacketReceived(callback: (AudioPacket, String) -> Unit) {
        onAudioPacketReceived = callback
    }
    
    /**
     * Set callback for client connection events
     */
    fun setOnClientConnected(callback: (String) -> Unit) {
        onClientConnected = callback
    }
    
    /**
     * Set callback for client disconnection events
     */
    fun setOnClientDisconnected(callback: (String) -> Unit) {
        onClientDisconnected = callback
    }
    
    /**
     * Get current network statistics
     */
    fun getNetworkStats(): Map<String, AudioPacketStats> {
        return packetStats.toMap()
    }
    
    /**
     * Check if the audio stream server is currently running
     */
    fun isServerRunning(): Boolean {
        return isRunning && serverSocket != null && !serverSocket!!.isClosed
    }
    
    /**
     * Stop the audio stream protocol
     */
    fun stop() {
        Log.d(tag, "Stopping audio stream protocol")
        isRunning = false
        cleanup()
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        try {
            serverSocket?.close()
            multicastSocket?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error cleaning up sockets", e)
        }
        
        serverSocket = null
        multicastSocket = null
        clientAddresses.clear()
        packetStats.clear()
        protocolScope.cancel()
        
        _connectedClients.value = emptySet()
        _streamStats.value = AudioPacketStats()
    }
}
