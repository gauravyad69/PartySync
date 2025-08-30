package io.github.gauravyad69.partysync.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

/**
 * Manages audio playback for received network audio streams
 * Handles buffering, synchronization, and audio output
 */
class AudioPlaybackManager {
    private val tag = "AudioPlaybackManager"
    
    // Audio configuration constants
    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_MULTIPLIER = 4
        const val TARGET_BUFFER_MS = 100 // Target buffer size in milliseconds
        const val MIN_BUFFER_MS = 50     // Minimum buffer before playing
        const val MAX_BUFFER_MS = 300    // Maximum buffer to prevent lag
    }
    
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val playbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Audio buffer management
    private val audioBuffer = PriorityQueue<AudioPacket> { a, b -> 
        a.sequenceNumber.compareTo(b.sequenceNumber) 
    }
    private val bufferLock = Any()
    
    // State management
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _bufferLevel = MutableStateFlow(0f)
    val bufferLevel: StateFlow<Float> = _bufferLevel
    
    private val _playbackLatency = MutableStateFlow(0L)
    val playbackLatency: StateFlow<Long> = _playbackLatency
    
    // Synchronization
    private var baseTimestamp: Long = 0
    private var lastSequenceNumber: Int = -1
    private var packetsDropped: Long = 0
    private var packetsPlayed: Long = 0
    
    private val bufferSize: Int by lazy {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        max(minBufferSize * BUFFER_SIZE_MULTIPLIER, getTargetBufferSizeBytes())
    }
    
    /**
     * Initialize audio playback system
     */
    fun initialize(): Boolean {
        return try {
            if (audioTrack != null) {
                Log.w(tag, "AudioTrack already initialized")
                return true
            }
            
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build()
            
            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            
            val state = audioTrack?.state
            if (state == AudioTrack.STATE_INITIALIZED) {
                Log.d(tag, "AudioTrack initialized successfully with buffer size: $bufferSize")
                true
            } else {
                Log.e(tag, "Failed to initialize AudioTrack, state: $state")
                audioTrack?.release()
                audioTrack = null
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error initializing AudioTrack", e)
            false
        }
    }
    
    /**
     * Start audio playback
     */
    fun startPlayback() {
        if (_isPlaying.value) {
            Log.w(tag, "Audio playback already running")
            return
        }
        
        if (audioTrack == null && !initialize()) {
            Log.e(tag, "Cannot start playback - AudioTrack not initialized")
            return
        }
        
        try {
            audioTrack?.play()
            _isPlaying.value = true
            
            playbackJob = playbackScope.launch {
                playbackLoop()
            }
            
            Log.d(tag, "Audio playback started")
        } catch (e: Exception) {
            Log.e(tag, "Error starting audio playback", e)
            _isPlaying.value = false
        }
    }
    
    /**
     * Stop audio playback
     */
    fun stopPlayback() {
        Log.d(tag, "Stopping audio playback")
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioTrack", e)
        }
        
        // Clear buffer
        synchronized(bufferLock) {
            audioBuffer.clear()
        }
        
        _bufferLevel.value = 0f
        _playbackLatency.value = 0L
        baseTimestamp = 0
        lastSequenceNumber = -1
        
        Log.d(tag, "Audio playback stopped")
    }
    
    /**
     * Add audio packet to playback buffer
     */
    fun addAudioPacket(packet: AudioPacket) {
        if (!packet.isValid()) {
            Log.w(tag, "Dropping invalid audio packet")
            return
        }
        
        synchronized(bufferLock) {
            // Initialize base timestamp on first packet
            if (baseTimestamp == 0L) {
                baseTimestamp = packet.timestamp
                Log.d(tag, "Set base timestamp: $baseTimestamp")
            }
            
            // Check for packet loss
            if (lastSequenceNumber >= 0 && packet.sequenceNumber != lastSequenceNumber + 1) {
                val expectedSeq = lastSequenceNumber + 1
                val actualSeq = packet.sequenceNumber
                
                if (actualSeq > expectedSeq) {
                    // Packets were lost
                    val lostCount = actualSeq - expectedSeq
                    packetsDropped += lostCount
                    Log.w(tag, "Lost $lostCount packets (expected $expectedSeq, got $actualSeq)")
                } else if (actualSeq < expectedSeq) {
                    // Late packet, might be duplicate or out of order
                    Log.w(tag, "Received late/duplicate packet: $actualSeq (expected $expectedSeq)")
                    return // Drop late packets
                }
            }
            
            // Add packet to buffer
            audioBuffer.offer(packet)
            lastSequenceNumber = max(lastSequenceNumber, packet.sequenceNumber)
            
            // Remove old packets to prevent buffer overflow
            while (audioBuffer.size > getMaxBufferPackets()) {
                val droppedPacket = audioBuffer.poll()
                if (droppedPacket != null) {
                    packetsDropped++
                    Log.w(tag, "Buffer overflow, dropped packet ${droppedPacket.sequenceNumber}")
                }
            }
            
            // Update buffer level
            updateBufferLevel()
        }
    }
    
    /**
     * Main playback loop
     */
    private suspend fun playbackLoop() {
        val playBuffer = ByteArray(getTargetBufferSizeBytes())
        
        while (currentCoroutineContext().isActive && _isPlaying.value) {
            try {
                val audioData = getNextAudioChunk(playBuffer.size)
                
                if (audioData.isNotEmpty()) {
                    val bytesWritten = audioTrack?.write(audioData, 0, audioData.size) ?: 0
                    
                    if (bytesWritten > 0) {
                        packetsPlayed++
                        
                        // Calculate and update latency
                        val currentTime = System.currentTimeMillis()
                        synchronized(bufferLock) {
                            if (audioBuffer.isNotEmpty()) {
                                val nextPacket = audioBuffer.peek()
                                if (nextPacket != null) {
                                    _playbackLatency.value = currentTime - nextPacket.timestamp
                                }
                            }
                        }
                    } else {
                        Log.w(tag, "Failed to write audio data, result: $bytesWritten")
                    }
                } else {
                    // No data available, wait briefly
                    delay(10)
                }
                
                // Small delay to prevent CPU spinning
                delay(5)
                
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive) {
                    Log.e(tag, "Error in playback loop", e)
                }
            }
        }
    }
    
    /**
     * Get next audio chunk from buffer
     */
    private fun getNextAudioChunk(maxSize: Int): ByteArray {
        val result = ByteArray(maxSize)
        var bytesCollected = 0
        
        synchronized(bufferLock) {
            // Wait for minimum buffer before starting playback
            val bufferMs = getCurrentBufferMs()
            if (bufferMs < MIN_BUFFER_MS && packetsPlayed == 0L) {
                return ByteArray(0) // Not enough buffer yet
            }
            
            // Collect audio data from packets
            while (audioBuffer.isNotEmpty() && bytesCollected < maxSize) {
                val packet = audioBuffer.poll()
                if (packet != null) {
                    val remainingSpace = maxSize - bytesCollected
                    val bytesToCopy = min(packet.audioData.size, remainingSpace)
                    
                    System.arraycopy(
                        packet.audioData, 0,
                        result, bytesCollected,
                        bytesToCopy
                    )
                    
                    bytesCollected += bytesToCopy
                    
                    // If packet doesn't fit completely, we should ideally put remainder back
                    // For simplicity, we'll just move to next packet
                }
            }
            
            updateBufferLevel()
        }
        
        return if (bytesCollected > 0) {
            result.copyOf(bytesCollected)
        } else {
            ByteArray(0)
        }
    }
    
    /**
     * Update buffer level percentage
     */
    private fun updateBufferLevel() {
        val currentBufferMs = getCurrentBufferMs()
        val bufferPercentage = (currentBufferMs.toFloat() / TARGET_BUFFER_MS.toFloat()).coerceIn(0f, 1f)
        _bufferLevel.value = bufferPercentage
    }
    
    /**
     * Get current buffer duration in milliseconds
     */
    private fun getCurrentBufferMs(): Int {
        val totalBytes = audioBuffer.sumOf { it.audioData.size }
        val bytesPerSecond = SAMPLE_RATE * 2 // 16-bit mono
        return if (bytesPerSecond > 0) {
            (totalBytes.toLong() * 1000L / bytesPerSecond.toLong()).toInt()
        } else 0
    }
    
    /**
     * Get target buffer size in bytes
     */
    private fun getTargetBufferSizeBytes(): Int {
        val bytesPerSecond = SAMPLE_RATE * 2 // 16-bit mono
        return (bytesPerSecond.toLong() * TARGET_BUFFER_MS.toLong() / 1000L).toInt()
    }
    
    /**
     * Get maximum buffer size in packets
     */
    private fun getMaxBufferPackets(): Int {
        val targetBytes = (SAMPLE_RATE.toLong() * 2L * MAX_BUFFER_MS.toLong() / 1000L).toInt()
        val avgPacketSize = 1000 // Approximate audio packet size
        return maxOf(10, targetBytes / avgPacketSize)
    }
    
    /**
     * Get playback statistics
     */
    fun getPlaybackStats(): PlaybackStats {
        synchronized(bufferLock) {
            return PlaybackStats(
                packetsPlayed = packetsPlayed,
                packetsDropped = packetsDropped,
                currentBufferMs = getCurrentBufferMs(),
                bufferLevel = _bufferLevel.value,
                latencyMs = _playbackLatency.value,
                isPlaying = _isPlaying.value
            )
        }
    }
    
    /**
     * Clear playback buffer
     */
    fun clearBuffer() {
        synchronized(bufferLock) {
            audioBuffer.clear()
            updateBufferLevel()
            Log.d(tag, "Audio buffer cleared")
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        Log.d(tag, "Releasing AudioPlaybackManager")
        stopPlayback()
        
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioTrack", e)
        }
        
        audioTrack = null
        playbackScope.cancel()
    }
}

/**
 * Playback statistics data class
 */
data class PlaybackStats(
    val packetsPlayed: Long,
    val packetsDropped: Long,
    val currentBufferMs: Int,
    val bufferLevel: Float,
    val latencyMs: Long,
    val isPlaying: Boolean
) {
    fun getPacketLossRate(): Double {
        val totalPackets = packetsPlayed + packetsDropped
        return if (totalPackets > 0) {
            packetsDropped.toDouble() / totalPackets.toDouble()
        } else 0.0
    }
}
