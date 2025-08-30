package io.github.gauravyad69.partysync.audio

/**
 * Different audio streaming modes for PartySync
 */
enum class AudioStreamingMode(
    val displayName: String,
    val description: String,
    val icon: String
) {
    MICROPHONE(
        displayName = "Microphone",
        description = "Stream live audio from your microphone (karaoke, commentary, live DJ)",
        icon = "mic"
    ),
    
    SYSTEM_AUDIO(
        displayName = "System Audio", 
        description = "Stream what's playing on your device (Spotify, YouTube, etc.)",
        icon = "phone_android"
    ),
    
    CUSTOM_PLAYER(
        displayName = "Music Player",
        description = "Play music files synchronized across all devices",
        icon = "library_music"
    );
    
    /**
     * Check if this mode requires audio capture from device
     */
    fun requiresAudioCapture(): Boolean {
        return this == MICROPHONE || this == SYSTEM_AUDIO
    }
    
    /**
     * Check if this mode requires file management
     */
    fun requiresFileManagement(): Boolean {
        return this == CUSTOM_PLAYER
    }
    
    /**
     * Check if this mode supports real-time streaming
     */
    fun isRealTimeStreaming(): Boolean {
        return this == MICROPHONE || this == SYSTEM_AUDIO
    }
    
    /**
     * Get recommended connection types for this mode
     */
    fun getRecommendedConnections(): List<io.github.gauravyad69.partysync.network.ConnectionType> {
        return when (this) {
            MICROPHONE -> listOf(
                io.github.gauravyad69.partysync.network.ConnectionType.Bluetooth,
                io.github.gauravyad69.partysync.network.ConnectionType.WiFiDirect
            )
            SYSTEM_AUDIO -> listOf(
                io.github.gauravyad69.partysync.network.ConnectionType.WiFiDirect,
                io.github.gauravyad69.partysync.network.ConnectionType.LocalHotspot
            )
            CUSTOM_PLAYER -> listOf(
                io.github.gauravyad69.partysync.network.ConnectionType.WiFiDirect,
                io.github.gauravyad69.partysync.network.ConnectionType.LocalHotspot,
                io.github.gauravyad69.partysync.network.ConnectionType.Bluetooth
            )
        }
    }
}

/**
 * Configuration for audio streaming session
 */
data class AudioStreamingConfig(
    val mode: AudioStreamingMode,
    val connectionType: io.github.gauravyad69.partysync.network.ConnectionType,
    val roomName: String,
    val audioQuality: AudioQuality = AudioQuality.STANDARD,
    val maxClients: Int = 8
)

/**
 * Audio quality settings
 */
enum class AudioQuality(
    val displayName: String,
    val bitrate: Int, // kbps
    val sampleRate: Int,
    val bufferSizeMs: Int
) {
    LOW(
        displayName = "Low (Battery Saver)",
        bitrate = 64,
        sampleRate = 22050,
        bufferSizeMs = 200
    ),
    
    STANDARD(
        displayName = "Standard",
        bitrate = 128,
        sampleRate = 44100,
        bufferSizeMs = 100
    ),
    
    HIGH(
        displayName = "High Quality",
        bitrate = 192,
        sampleRate = 44100,
        bufferSizeMs = 80
    );
    
    fun getBytesPerSecond(): Int {
        return sampleRate * 2 // 16-bit mono
    }
}
