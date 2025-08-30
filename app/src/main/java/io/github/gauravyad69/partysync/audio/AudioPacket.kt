package io.github.gauravyad69.partysync.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Audio data packet for network transmission
 * Includes sequencing, timing, and error detection
 */
data class AudioPacket(
    val sequenceNumber: Int,
    val timestamp: Long,
    val audioData: ByteArray,
    val checksum: Int = calculateChecksum(sequenceNumber, timestamp, audioData)
) {
    companion object {
        // Packet structure constants
        const val HEADER_SIZE = 20 // 4 + 8 + 4 + 4 bytes
        const val MAX_AUDIO_DATA_SIZE = 1400 // Leave room for UDP headers
        const val MAX_PACKET_SIZE = HEADER_SIZE + MAX_AUDIO_DATA_SIZE
        
        /**
         * Calculate CRC32 checksum for packet integrity
         */
        private fun calculateChecksum(sequenceNumber: Int, timestamp: Long, audioData: ByteArray): Int {
            val crc = CRC32()
            val buffer = ByteBuffer.allocate(12 + audioData.size)
                .order(ByteOrder.BIG_ENDIAN)
            
            buffer.putInt(sequenceNumber)
            buffer.putLong(timestamp)
            buffer.put(audioData)
            
            crc.update(buffer.array())
            return crc.value.toInt()
        }
        
        /**
         * Create packet from raw bytes received over network
         */
        fun fromBytes(data: ByteArray): AudioPacket? {
            if (data.size < HEADER_SIZE) return null
            
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            
            return try {
                val sequenceNumber = buffer.int
                val timestamp = buffer.long
                val audioDataSize = buffer.int
                val receivedChecksum = buffer.int
                
                if (audioDataSize < 0 || audioDataSize > MAX_AUDIO_DATA_SIZE) {
                    return null
                }
                
                if (data.size < HEADER_SIZE + audioDataSize) {
                    return null
                }
                
                val audioData = ByteArray(audioDataSize)
                buffer.get(audioData)
                
                val packet = AudioPacket(sequenceNumber, timestamp, audioData)
                
                // Verify checksum
                if (packet.checksum != receivedChecksum) {
                    null // Corrupted packet
                } else {
                    packet
                }
            } catch (e: Exception) {
                null // Invalid packet format
            }
        }
    }
    
    /**
     * Convert packet to bytes for network transmission
     */
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + audioData.size)
            .order(ByteOrder.BIG_ENDIAN)
        
        buffer.putInt(sequenceNumber)
        buffer.putLong(timestamp)
        buffer.putInt(audioData.size)
        buffer.putInt(checksum)
        buffer.put(audioData)
        
        return buffer.array()
    }
    
    /**
     * Get packet size including headers
     */
    fun getPacketSize(): Int = HEADER_SIZE + audioData.size
    
    /**
     * Validate packet integrity
     */
    fun isValid(): Boolean {
        return checksum == calculateChecksum(sequenceNumber, timestamp, audioData) &&
               audioData.size <= MAX_AUDIO_DATA_SIZE
    }
    
    /**
     * Get audio duration in milliseconds (approximate, assuming 44.1kHz 16-bit mono)
     */
    fun getAudioDurationMs(): Int {
        // 44100 samples/sec * 2 bytes/sample = 88200 bytes/sec
        return (audioData.size * 1000) / 88200
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as AudioPacket
        
        if (sequenceNumber != other.sequenceNumber) return false
        if (timestamp != other.timestamp) return false
        if (!audioData.contentEquals(other.audioData)) return false
        if (checksum != other.checksum) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = sequenceNumber
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + audioData.contentHashCode()
        result = 31 * result + checksum
        return result
    }
}

/**
 * Audio packet statistics for monitoring stream health
 */
data class AudioPacketStats(
    val totalPacketsSent: Long = 0,
    val totalPacketsReceived: Long = 0,
    val packetsLost: Long = 0,
    val packetsOutOfOrder: Long = 0,
    val averageLatencyMs: Double = 0.0,
    val lastSequenceNumber: Int = -1,
    val lastTimestamp: Long = 0
) {
    fun getPacketLossRate(): Double {
        return if (totalPacketsSent > 0) {
            packetsLost.toDouble() / totalPacketsSent.toDouble()
        } else 0.0
    }
    
    fun getDeliveryRate(): Double {
        return if (totalPacketsSent > 0) {
            totalPacketsReceived.toDouble() / totalPacketsSent.toDouble()
        } else 0.0
    }
}

/**
 * Audio stream configuration
 */
data class AudioStreamConfig(
    val sampleRate: Int = 44100,
    val channelCount: Int = 1,
    val bitsPerSample: Int = 16,
    val packetsPerSecond: Int = 50, // 20ms per packet
    val maxRetransmissions: Int = 2
) {
    fun getBytesPerPacket(): Int {
        val bytesPerSecond = sampleRate * channelCount * (bitsPerSample / 8)
        return bytesPerSecond / packetsPerSecond
    }
    
    fun getPacketDurationMs(): Int = 1000 / packetsPerSecond
}
