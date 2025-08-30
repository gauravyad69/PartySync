package io.github.gauravyad69.partysync.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Manages microphone audio capture for live streaming
 * Perfect for karaoke, commentary, DJ talking, live performances
 */
class MicrophoneAudioManager {
    private val tag = "MicrophoneAudioManager"

    // Audio configuration
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

    // Smooth audio level for better visualization
    private var smoothedAudioLevel = 0f
    private val audioLevelSmoothingFactor = 0.3f // Higher = more responsive

    private val _microphoneGain = MutableStateFlow(1.0f)
    val microphoneGain: StateFlow<Float> = _microphoneGain

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
     * Initialize microphone recording
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
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
                Log.d(tag, "Microphone initialized successfully")
                true
            } else {
                Log.e(tag, "Failed to initialize microphone, state: $state")
                cleanup()
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error initializing microphone", e)
            false
        }
    }

    /**
     * Start capturing microphone audio
     */
    fun startCapture(onAudioData: (ByteArray) -> Unit) {
        if (_isCapturing.value) {
            Log.w(tag, "Microphone capture already running")
            return
        }

        if (audioRecord == null && !initialize()) {
            Log.e(tag, "Cannot start capture - microphone not initialized")
            return
        }

        onAudioDataCaptured = onAudioData

        captureJob = captureScope.launch {
            try {
                audioRecord?.startRecording()
                _isCapturing.value = true

                val buffer = ByteArray(bufferSize)
                Log.d(tag, "Started microphone capture")

                while (isActive && _isCapturing.value) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    if (bytesRead > 0) {
                        // Apply microphone gain
                        val processedAudio = applyMicrophoneGain(buffer, bytesRead)

                        // Calculate audio level with smoothing
                        val rawAudioLevel = calculateAudioLevel(processedAudio, bytesRead)
                        smoothedAudioLevel = (audioLevelSmoothingFactor * rawAudioLevel) +
                                ((1f - audioLevelSmoothingFactor) * smoothedAudioLevel)
                        _audioLevel.value = smoothedAudioLevel

                        // Send processed audio
                        onAudioDataCaptured?.invoke(processedAudio.copyOf(bytesRead))

                        delay(10) // Small delay to prevent overwhelming
                    } else {
                        delay(50) // Longer delay on no data
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error during microphone capture", e)
            } finally {
                audioRecord?.stop()
                _isCapturing.value = false
                Log.d(tag, "Microphone capture stopped")
            }
        }
    }

    /**
     * Stop microphone capture
     */
    fun stopCapture() {
        Log.d(tag, "Stopping microphone capture")
        _isCapturing.value = false
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord", e)
        }

        _audioLevel.value = 0f
        smoothedAudioLevel = 0f
        onAudioDataCaptured = null
    }

    /**
     * Set microphone gain/volume (0.0 to 2.0)
     */
    fun setMicrophoneGain(gain: Float) {
        _microphoneGain.value = gain.coerceIn(0f, 2f)
    }

    /**
     * Apply gain to microphone audio
     */
    private fun applyMicrophoneGain(buffer: ByteArray, length: Int): ByteArray {
        val gain = _microphoneGain.value
        if (gain == 1.0f) return buffer // No gain adjustment needed

        val result = ByteArray(length)
        var i = 0
        while (i < length - 1) {
            // Convert bytes to 16-bit sample
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()

            // Apply gain and clamp to prevent distortion
            val gained = (shortSample * gain).toInt().coerceIn(-32768, 32767)

            // Convert back to bytes
            result[i] = (gained and 0xFF).toByte()
            result[i + 1] = ((gained shr 8) and 0xFF).toByte()

            i += 2
        }
        return result
    }

    /**
     * Calculate audio level for visualization with enhanced sensitivity
     */
    private fun calculateAudioLevel(buffer: ByteArray, length: Int): Float {
        if (length <= 0) return 0f

        var sum = 0L
        var i = 0
        while (i < length - 1) {
            // Convert to 16-bit signed sample
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val signedSample = if (sample > 32767) sample - 65536 else sample
            sum += (signedSample * signedSample).toLong()
            i += 2
        }

        val rms = sqrt(sum.toDouble() / (length / 2))
        // Make it more sensitive by amplifying and using a lower divisor
        val normalizedLevel = (rms / 16384.0).coerceIn(0.0, 1.0) // Changed from 32768 to 16384

        // Apply additional sensitivity boost for quiet sounds
        val sensitiveLevel = normalizedLevel.pow(0.6) // Power curve for better visualization

        return sensitiveLevel.toFloat()
    }

    /**
     * Check if microphone is available
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun isMicrophoneAvailable(): Boolean {
        return try {
            val testRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            )
            val available = testRecord.state == AudioRecord.STATE_INITIALIZED
            testRecord.release()
            available
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get microphone configuration
     */
    fun getMicrophoneConfig(): AudioConfig {
        return AudioConfig(
            sampleRate = SAMPLE_RATE,
            channelCount = 1,
            bitsPerSample = 16,
            bufferSize = bufferSize
        )
    }

    /**
     * Clean up resources
     */
    private fun cleanup() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error cleaning up microphone", e)
        }
        audioRecord = null
    }

    /**
     * Release all resources
     */
    fun release() {
        Log.d(tag, "Releasing MicrophoneAudioManager")
        stopCapture()
        cleanup()
        captureScope.cancel()
    }
}
