package io.github.gauravyad69.partysync.audio

import android.content.Context
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * Unified manager that coordinates between different audio streaming modes:
 * - Microphone: Live audio from microphone
 * - System Audio: Stream device's audio output
 * - Custom Player: Synchronized music playback
 */
class AudioStreamingManager(private val context: Context) {
    private val tag = "AudioStreamingManager"
    
    // Mode-specific managers
    private val microphoneManager = MicrophoneAudioManager()
    private val systemAudioManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        SystemAudioManager()
    } else null
    private val customPlayerManager = CustomPlayerManager(context)
    
    // Network streaming
    private val audioStreamProtocol = AudioStreamProtocol()
    
    // State management
    private val _currentMode = MutableStateFlow(AudioStreamingMode.MICROPHONE)
    val currentMode: StateFlow<AudioStreamingMode> = _currentMode
    
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming
    
    private val _streamingConfig = MutableStateFlow<AudioStreamingConfig?>(null)
    val streamingConfig: StateFlow<AudioStreamingConfig?> = _streamingConfig
    
    private val _connectedClients = MutableStateFlow<Set<String>>(emptySet())
    val connectedClients: StateFlow<Set<String>> = _connectedClients
    
    // Combined audio level from active mode
    val audioLevel = combine(
        microphoneManager.audioLevel,
        systemAudioManager?.audioLevel ?: MutableStateFlow(0f),
        _currentMode
    ) { micLevel, sysLevel, mode ->
        when (mode) {
            AudioStreamingMode.MICROPHONE -> micLevel
            AudioStreamingMode.SYSTEM_AUDIO -> sysLevel
            AudioStreamingMode.CUSTOM_PLAYER -> 0f // No level for player mode
        }
    }
    
    // Combined playback state
    val playbackState = customPlayerManager.getPlaybackState()
    
    init {
        setupNetworkCallbacks()
    }
    
    /**
     * Set the current streaming mode
     */
    fun setStreamingMode(mode: AudioStreamingMode) {
        if (_isStreaming.value) {
            Log.w(tag, "Cannot change mode while streaming. Stop streaming first.")
            return
        }
        _currentMode.value = mode
        Log.d(tag, "Changed streaming mode to: ${mode.displayName}")
    }
    
    /**
     * Start streaming as host
     */
    fun startHostStreaming(config: AudioStreamingConfig, mediaProjection: MediaProjection? = null): Boolean {
        if (_isStreaming.value) {
            Log.w(tag, "Already streaming")
            return true
        }
        
        _streamingConfig.value = config
        _currentMode.value = config.mode
        
        return when (config.mode) {
            AudioStreamingMode.MICROPHONE -> startMicrophoneStreaming()
            AudioStreamingMode.SYSTEM_AUDIO -> startSystemAudioStreaming(mediaProjection)
            AudioStreamingMode.CUSTOM_PLAYER -> startCustomPlayerStreaming()
        }
    }
    
    /**
     * Start streaming as client (receive-only)
     */
    fun startClientStreaming(config: AudioStreamingConfig): Boolean {
        if (_isStreaming.value) {
            Log.w(tag, "Already streaming")
            return true
        }
        
        _streamingConfig.value = config
        _currentMode.value = config.mode
        
        return when (config.mode) {
            AudioStreamingMode.MICROPHONE,
            AudioStreamingMode.SYSTEM_AUDIO -> {
                // Start as audio stream client (receive audio packets)
                startAudioStreamClient()
            }
            AudioStreamingMode.CUSTOM_PLAYER -> {
                // Start as sync client (receive play/pause commands)
                startCustomPlayerClient()
            }
        }
    }
    
    /**
     * Stop all streaming
     */
    fun stopStreaming() {
        Log.d(tag, "Stopping streaming")
        
        // Stop all managers
        microphoneManager.stopCapture()
        systemAudioManager?.stopCapture()
        audioStreamProtocol.stop()
        
        _isStreaming.value = false
        _connectedClients.value = emptySet()
        _streamingConfig.value = null
        
        Log.d(tag, "Streaming stopped")
    }
    
    // Private streaming methods
    
    private fun startMicrophoneStreaming(): Boolean {
        Log.d(tag, "Starting microphone streaming")
        
        if (!microphoneManager.isMicrophoneAvailable()) {
            Log.e(tag, "Microphone not available")
            return false
        }
        
        if (!microphoneManager.initialize()) {
            Log.e(tag, "Failed to initialize microphone")
            return false
        }
        
        if (!audioStreamProtocol.startAsServer()) {
            Log.e(tag, "Failed to start audio stream server")
            return false
        }
        
        microphoneManager.startCapture { audioData ->
            audioStreamProtocol.broadcastAudioPacket(audioData)
        }
        
        _isStreaming.value = true
        Log.d(tag, "Microphone streaming started")
        return true
    }
    
    private fun startSystemAudioStreaming(mediaProjection: MediaProjection?): Boolean {
        Log.d(tag, "Starting system audio streaming")
        
        if (systemAudioManager == null) {
            Log.e(tag, "System audio not supported on this device")
            return false
        }
        
        if (!systemAudioManager.isSystemAudioSupported()) {
            Log.e(tag, "System audio requires Android 10+")
            return false
        }
        
        if (mediaProjection == null) {
            Log.e(tag, "MediaProjection required for system audio")
            return false
        }
        
        if (!systemAudioManager.initialize(mediaProjection)) {
            Log.e(tag, "Failed to initialize system audio")
            return false
        }
        
        if (!audioStreamProtocol.startAsServer()) {
            Log.e(tag, "Failed to start audio stream server")
            return false
        }
        
        systemAudioManager.startCapture { audioData ->
            audioStreamProtocol.broadcastAudioPacket(audioData)
        }
        
        _isStreaming.value = true
        Log.d(tag, "System audio streaming started")
        return true
    }
    
    private fun startCustomPlayerStreaming(): Boolean {
        Log.d(tag, "Starting custom player streaming")
        
        // Custom player doesn't need audio streaming server, just sync commands
        setupCustomPlayerNetworkCallbacks()
        
        _isStreaming.value = true
        Log.d(tag, "Custom player streaming started")
        return true
    }
    
    private fun startAudioStreamClient(): Boolean {
        Log.d(tag, "Starting audio stream client")
        
        if (!audioStreamProtocol.startAsClient()) {
            Log.e(tag, "Failed to start audio stream client")
            return false
        }
        
        _isStreaming.value = true
        Log.d(tag, "Audio stream client started")
        return true
    }
    
    private fun startCustomPlayerClient(): Boolean {
        Log.d(tag, "Starting custom player client")
        
        // Client just receives sync commands, no audio streaming needed
        _isStreaming.value = true
        Log.d(tag, "Custom player client started")
        return true
    }
    
    // Network callback setup
    
    private fun setupNetworkCallbacks() {
        // Audio stream protocol callbacks
        audioStreamProtocol.setOnClientConnected { clientId ->
            val current = _connectedClients.value.toMutableSet()
            current.add(clientId)
            _connectedClients.value = current
            Log.d(tag, "Client connected: $clientId")
        }
        
        audioStreamProtocol.setOnClientDisconnected { clientId ->
            val current = _connectedClients.value.toMutableSet()
            current.remove(clientId)
            _connectedClients.value = current
            Log.d(tag, "Client disconnected: $clientId")
        }
    }
    
    private fun setupCustomPlayerNetworkCallbacks() {
        customPlayerManager.setOnPlayCommand { position ->
            // Broadcast play command to all clients
            // This would be handled by the network layer
            Log.d(tag, "Broadcasting play command: $position")
        }
        
        customPlayerManager.setOnPauseCommand {
            // Broadcast pause command to all clients
            Log.d(tag, "Broadcasting pause command")
        }
        
        customPlayerManager.setOnSeekCommand { position ->
            // Broadcast seek command to all clients
            Log.d(tag, "Broadcasting seek command: $position")
        }
        
        customPlayerManager.setOnTrackChangeCommand { track, position ->
            // Broadcast track change to all clients
            Log.d(tag, "Broadcasting track change: ${track.title}")
        }
    }
    
    // Public control methods
    
    /**
     * Set microphone gain (0.0 to 2.0)
     */
    fun setMicrophoneGain(gain: Float) {
        microphoneManager.setMicrophoneGain(gain)
    }
    
    /**
     * Set system audio volume (0.0 to 2.0)
     */
    fun setSystemVolume(volume: Float) {
        systemAudioManager?.setSystemVolume(volume)
    }
    
    /**
     * Load music playlist for custom player
     */
    fun loadPlaylist(tracks: List<MusicTrack>) {
        customPlayerManager.loadPlaylist(tracks)
    }
    
    /**
     * Control custom player playback
     */
    fun playMusic() = customPlayerManager.play()
    fun pauseMusic() = customPlayerManager.pause()
    fun seekTo(position: Long) = customPlayerManager.seekTo(position)
    fun playNext() = customPlayerManager.playNextTrack()
    fun playPrevious() = customPlayerManager.playPreviousTrack()
    
    /**
     * Get current streaming statistics
     */
    fun getStreamingStats() = audioStreamProtocol.getNetworkStats()
    
    /**
     * Check if a specific mode is supported on this device
     */
    fun isModeSupported(mode: AudioStreamingMode): Boolean {
        return when (mode) {
            AudioStreamingMode.MICROPHONE -> microphoneManager.isMicrophoneAvailable()
            AudioStreamingMode.SYSTEM_AUDIO -> systemAudioManager?.isSystemAudioSupported() ?: false
            AudioStreamingMode.CUSTOM_PLAYER -> true // Always supported
        }
    }
    
    /**
     * Get mode-specific managers (for advanced control)
     */
    fun getMicrophoneManager() = microphoneManager
    fun getSystemAudioManager() = systemAudioManager
    fun getCustomPlayerManager() = customPlayerManager
    
    /**
     * Release all resources
     */
    fun release() {
        Log.d(tag, "Releasing AudioStreamingManager")
        stopStreaming()
        
        microphoneManager.release()
        systemAudioManager?.release()
        customPlayerManager.release()
        audioStreamProtocol.stop()
    }
}
