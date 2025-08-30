package io.github.gauravyad69.partysync.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages system audio capture for streaming what's playing on the device
 * Perfect for streaming Spotify, YouTube, games, or any other app's audio
 * Requires Android 10+ (API 29) for AudioPlaybackCapture
 */
@RequiresApi(Build.VERSION_CODES.Q)
class SystemAudioManager {
    private val tag = "SystemAudioManager"
    
    // Audio configuration
    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_MULTIPLIER = 4
    }
    
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val captureScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaProjection: MediaProjection? = null
    
    // State management
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel
    
    private val _systemVolume = MutableStateFlow(1.0f)
    val systemVolume: StateFlow<Float> = _systemVolume
    
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
     * Check if system audio capture is supported on this device
     */
    fun isSystemAudioSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
    
    /**
     * Initialize system audio capture with MediaProjection
     * Requires MediaProjection permission from user
     */
    fun initialize(mediaProjection: MediaProjection?): Boolean {
        if (!isSystemAudioSupported()) {
            Log.e(tag, "System audio capture requires Android 10+ (API 29)")
            return false
        }
        
        if (mediaProjection == null) {
            Log.e(tag, "MediaProjection is required for system audio capture")
            return false
        }
        
        return try {
            if (audioRecord != null) {
                Log.w(tag, "AudioRecord already initialized")
                return true
            }
            
            this.mediaProjection = mediaProjection
            
            // Create AudioPlaybackCaptureConfiguration for system audio
            val audioPlaybackCaptureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA) // Capture media audio
                .addMatchingUsage(AudioAttributes.USAGE_GAME) // Capture game audio
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN) // Capture other audio
                .build()
            
            // Create AudioFormat for capture
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build()
            
            // Create AudioRecord with playback capture configuration
            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(audioPlaybackCaptureConfig)
                .build()
            
            val state = audioRecord?.state
            if (state == AudioRecord.STATE_INITIALIZED) {
                Log.d(tag, "System audio capture initialized successfully")
                true
            } else {
                Log.e(tag, "Failed to initialize system audio capture, state: $state")
                cleanup()
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error initializing system audio capture", e)
            cleanup()
            false
        }
    }
    
    /**
     * Start capturing system audio
     */
    fun startCapture(onAudioData: (ByteArray) -> Unit) {
        if (_isCapturing.value) {
            Log.w(tag, "System audio capture already running")
            return
        }
        
        if (audioRecord == null) {
            Log.e(tag, "Cannot start capture - system audio not initialized")
            return
        }
        
        onAudioDataCaptured = onAudioData
        
        captureJob = captureScope.launch {
            try {
                audioRecord?.startRecording()
                _isCapturing.value = true
                
                val buffer = ByteArray(bufferSize)
                Log.d(tag, "Started system audio capture")
                
                while (isActive && _isCapturing.value) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (bytesRead > 0) {
                        // Apply volume adjustment if needed
                        val processedAudio = applyVolumeAdjustment(buffer, bytesRead)
                        
                        // Calculate audio level
                        val audioLevel = calculateAudioLevel(processedAudio, bytesRead)
                        _audioLevel.value = audioLevel
                        
                        // Convert stereo to mono for streaming (reduces bandwidth)
                        val monoAudio = convertStereoToMono(processedAudio, bytesRead)
                        
                        onAudioDataCaptured?.invoke(monoAudio)
                        
                        delay(5) // Smaller delay for system audio
                    } else {
                        delay(20)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error during system audio capture", e)
            } finally {
                audioRecord?.stop()
                _isCapturing.value = false
                Log.d(tag, "System audio capture stopped")
            }
        }
    }
    
    /**
     * Stop system audio capture
     */
    fun stopCapture() {
        Log.d(tag, "Stopping system audio capture")
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
     * Set system volume multiplier (0.0 to 2.0)
     */
    fun setSystemVolume(volume: Float) {
        _systemVolume.value = volume.coerceIn(0f, 2f)
    }
    
    /**
     * Apply volume adjustment to system audio
     */
    private fun applyVolumeAdjustment(buffer: ByteArray, length: Int): ByteArray {
        val volume = _systemVolume.value
        if (volume == 1.0f) return buffer
        
        val result = ByteArray(length)
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            
            val adjusted = (shortSample * volume).toInt().coerceIn(-32768, 32767)
            
            result[i] = (adjusted and 0xFF).toByte()
            result[i + 1] = ((adjusted shr 8) and 0xFF).toByte()
            
            i += 2
        }
        return result
    }
    
    /**
     * Convert stereo audio to mono to reduce bandwidth
     */
    private fun convertStereoToMono(buffer: ByteArray, length: Int): ByteArray {
        val monoLength = length / 2
        val monoBuffer = ByteArray(monoLength)
        
        var monoIndex = 0
        var stereoIndex = 0
        
        while (stereoIndex < length - 3) {
            // Get left and right channel samples
            val leftSample = (buffer[stereoIndex].toInt() and 0xFF) or 
                           (buffer[stereoIndex + 1].toInt() shl 8)
            val rightSample = (buffer[stereoIndex + 2].toInt() and 0xFF) or 
                            (buffer[stereoIndex + 3].toInt() shl 8)
            
            // Average the two channels
            val monoSample = ((leftSample.toShort() + rightSample.toShort()) / 2).toInt()
            
            // Store mono sample
            monoBuffer[monoIndex] = (monoSample and 0xFF).toByte()
            monoBuffer[monoIndex + 1] = ((monoSample shr 8) and 0xFF).toByte()
            
            stereoIndex += 4 // Move to next stereo sample (4 bytes)
            monoIndex += 2   // Move to next mono sample (2 bytes)
        }
        
        return monoBuffer
    }
    
    /**
     * Calculate audio level for visualization
     */
    private fun calculateAudioLevel(buffer: ByteArray, length: Int): Float {
        if (length <= 0) return 0f
        
        var sum = 0L
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += (sample * sample).toLong()
            i += 2
        }
        
        val rms = kotlin.math.sqrt(sum.toDouble() / (length / 2))
        return (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()
    }
    
    /**
     * Get system audio configuration
     */
    fun getSystemAudioConfig(): AudioConfig {
        return AudioConfig(
            sampleRate = SAMPLE_RATE,
            channelCount = 1, // Mono output after conversion
            bitsPerSample = 16,
            bufferSize = bufferSize / 2 // Smaller after mono conversion
        )
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        try {
            audioRecord?.release()
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(tag, "Error cleaning up system audio", e)
        }
        audioRecord = null
        mediaProjection = null
    }
    
    /**
     * Release all resources
     */
    fun release() {
        Log.d(tag, "Releasing SystemAudioManager")
        stopCapture()
        cleanup()
        captureScope.cancel()
    }
}
