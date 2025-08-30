package io.github.gauravyad69.partysync.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer

/**
 * Manages audio capture from microphone with real-time processing
 * Handles encoding and streaming preparation for network transmission
 */
class AudioCaptureManager {
    private val tag = "AudioCaptureManager"
    
    // Audio configuration constants
    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_MULTIPLIER = 4
    }
    
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val captureScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // State management
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel
    
    // Audio data callback
    private var onAudioDataCaptured: ((ByteArray) -> Unit)? = null
    
    private val bufferSize: Int by lazy {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        minBufferSize * BUFFER_SIZE_MULTIPLIER
    }
    
    /**
     * Initialize audio recording setup
     */
    fun initialize(): Boolean {
        return try {
            if (audioRecord != null) {
                Log.w(tag, "AudioRecord already initialized")
                return true
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            val state = audioRecord?.state
            if (state == AudioRecord.STATE_INITIALIZED) {
                Log.d(tag, "AudioRecord initialized successfully")
                true
            } else {
                Log.e(tag, "Failed to initialize AudioRecord, state: $state")
                audioRecord?.release()
                audioRecord = null
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error initializing AudioRecord", e)
            false
        }
    }
    
    /**
     * Start capturing audio from microphone
     */
    fun startCapture(onAudioData: (ByteArray) -> Unit) {
        if (_isCapturing.value) {
            Log.w(tag, "Audio capture already running")
            return
        }
        
        if (audioRecord == null && !initialize()) {
            Log.e(tag, "Cannot start capture - AudioRecord not initialized")
            return
        }
        
        onAudioDataCaptured = onAudioData
        
        captureJob = captureScope.launch {
            try {
                audioRecord?.startRecording()
                _isCapturing.value = true
                
                val buffer = ByteArray(bufferSize)
                Log.d(tag, "Started audio capture with buffer size: $bufferSize")
                
                while (isActive && _isCapturing.value) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (bytesRead > 0) {
                        // Calculate audio level for UI feedback
                        val audioLevel = calculateAudioLevel(buffer, bytesRead)
                        _audioLevel.value = audioLevel
                        
                        // Create properly sized buffer for actual data
                        val audioData = buffer.copyOf(bytesRead)
                        onAudioDataCaptured?.invoke(audioData)
                        
                        // Small delay to prevent overwhelming the system
                        delay(10)
                    } else {
                        Log.w(tag, "No audio data read, bytes: $bytesRead")
                        delay(50) // Longer delay on no data
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error during audio capture", e)
            } finally {
                audioRecord?.stop()
                _isCapturing.value = false
                Log.d(tag, "Audio capture stopped")
            }
        }
    }
    
    /**
     * Stop capturing audio
     */
    fun stopCapture() {
        Log.d(tag, "Stopping audio capture")
        _isCapturing.value = false
        captureJob?.cancel()
        captureJob = null
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord", e)
        }
        
        _audioLevel.value = 0f
        onAudioDataCaptured = null
    }
    
    /**
     * Calculate audio level for visualization (0.0 to 1.0)
     */
    private fun calculateAudioLevel(buffer: ByteArray, length: Int): Float {
        if (length <= 0) return 0f
        
        var sum = 0L
        var i = 0
        while (i < length - 1) {
            // Convert bytes to 16-bit samples
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += (sample * sample).toLong()
            i += 2
        }
        
        val rms = kotlin.math.sqrt(sum.toDouble() / (length / 2))
        val normalizedLevel = (rms / 32768.0).coerceIn(0.0, 1.0)
        
        return normalizedLevel.toFloat()
    }
    
    /**
     * Get current audio configuration info
     */
    fun getAudioConfig(): AudioConfig {
        return AudioConfig(
            sampleRate = SAMPLE_RATE,
            channelCount = if (CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO) 1 else 2,
            bitsPerSample = 16,
            bufferSize = bufferSize
        )
    }
    
    /**
     * Clean up resources
     */
    fun release() {
        Log.d(tag, "Releasing AudioCaptureManager")
        stopCapture()
        
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioRecord", e)
        }
        
        audioRecord = null
        captureScope.cancel()
    }
}

/**
 * Audio configuration data class
 */
data class AudioConfig(
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
    val bufferSize: Int
) {
    fun getBytesPerSecond(): Int {
        return sampleRate * channelCount * (bitsPerSample / 8)
    }
    
    fun getMillisecondsPerBuffer(): Int {
        return (bufferSize * 1000) / getBytesPerSecond()
    }
}
