package io.github.gauravyad69.partysync.audio

/**
 * Music track data class
 */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val filePath: String
)

/**
 * Playback synchronization state
 */
data class PlaybackSyncState(
    val isPlaying: Boolean,
    val position: Long,
    val duration: Long,
    val track: MusicTrack?,
    val volume: Float,
    val syncTimestamp: Long
)
