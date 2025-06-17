package io.github.gauravyad69.speakershare.audio

import kotlinx.coroutines.flow.Flow

data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val uri: String,
    val duration: Long
)

data class SyncedPlayback(
    val trackId: String,
    val position: Long,
    val isPlaying: Boolean,
    val timestamp: Long
)

interface AudioStreamer {
    suspend fun loadTrack(track: AudioTrack): Result<Unit>
    suspend fun play(position: Long = 0): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun seekTo(position: Long): Result<Unit>
    
    fun getPlaybackState(): Flow<SyncedPlayback>
    fun getCurrentPosition(): Long
    fun getDuration(): Long
}