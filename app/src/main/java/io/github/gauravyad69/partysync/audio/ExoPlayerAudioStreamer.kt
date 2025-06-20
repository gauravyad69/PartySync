package io.github.gauravyad69.partysync.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

class ExoPlayerAudioStreamer(
    private val context: Context
) : AudioStreamer {
    
    companion object {
        private const val TAG = "ExoPlayerAudioStreamer"
    }
    
    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(playerListener)
        }
    }
    
    private val _playbackState = MutableStateFlow(
        SyncedPlayback(
            trackId = "",
            position = 0L,
            isPlaying = false,
            timestamp = System.currentTimeMillis()
        )
    )
    
    private var currentTrack: AudioTrack? = null
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            updatePlaybackState()
        }
        
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            updatePlaybackState()
        }
    }
    
    override suspend fun loadTrack(track: AudioTrack): Result<Unit> {
        return try {
            currentTrack = track
            val mediaItem = MediaItem.fromUri(track.uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            
            Log.d(TAG, "Track loaded: ${track.title}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load track", e)
            Result.failure(e)
        }
    }
    
    override suspend fun play(position: Long): Result<Unit> {
        return try {
            if (position > 0) {
                player.seekTo(position)
            }
            player.play()
            updatePlaybackState()
            
            Log.d(TAG, "Playback started at position: $position")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            Result.failure(e)
        }
    }
    
    override suspend fun pause(): Result<Unit> {
        return try {
            player.pause()
            updatePlaybackState()
            
            Log.d(TAG, "Playback paused")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause playback", e)
            Result.failure(e)
        }
    }
    
    override suspend fun seekTo(position: Long): Result<Unit> {
        return try {
            player.seekTo(position)
            updatePlaybackState()
            
            Log.d(TAG, "Seeked to position: $position")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek", e)
            Result.failure(e)
        }
    }
    
    override fun getPlaybackState(): Flow<SyncedPlayback> = _playbackState.asStateFlow()
    
    override fun getCurrentPosition(): Long = player.currentPosition
    
    override fun getDuration(): Long = player.duration
    
    private fun updatePlaybackState() {
        currentTrack?.let { track ->
            _playbackState.value = SyncedPlayback(
                trackId = track.id,
                position = player.currentPosition,
                isPlaying = player.isPlaying,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    fun release() {
        player.release()
    }
}