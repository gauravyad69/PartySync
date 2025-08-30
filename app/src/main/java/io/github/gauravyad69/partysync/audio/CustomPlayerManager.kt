package io.github.gauravyad69.partysync.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Manages synchronized music playback across multiple devices
 * Perfect for playing music files where everyone has the same song
 * Uses synchronized commands rather than audio streaming
 */
class CustomPlayerManager(private val context: Context) {
    private val tag = "CustomPlayerManager"
    
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State management
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration
    
    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack
    
    private val _volume = MutableStateFlow(0.8f)
    val volume: StateFlow<Float> = _volume
    
    private val _playlist = MutableStateFlow<List<MusicTrack>>(emptyList())
    val playlist: StateFlow<List<MusicTrack>> = _playlist
    
    private val _currentTrackIndex = MutableStateFlow(-1)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex
    
    // Callbacks for network sync
    private var onPlayCommand: ((Long) -> Unit)? = null
    private var onPauseCommand: (() -> Unit)? = null
    private var onSeekCommand: ((Long) -> Unit)? = null
    private var onTrackChangeCommand: ((MusicTrack, Long) -> Unit)? = null
    
    /**
     * Load a music track for playback
     */
    fun loadTrack(track: MusicTrack): Boolean {
        return try {
            releaseMediaPlayer()
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(File(track.filePath)))
                prepare()
                setVolume(_volume.value, _volume.value)
                
                setOnCompletionListener {
                    // Auto advance to next track
                    playNextTrack()
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer error: what=$what, extra=$extra")
                    false
                }
            }
            
            _currentTrack.value = track
            _duration.value = mediaPlayer?.duration?.toLong() ?: 0L
            _currentPosition.value = 0L
            
            Log.d(tag, "Loaded track: ${track.title}")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error loading track: ${track.title}", e)
            false
        }
    }
    
    /**
     * Start playing (and sync with other devices if host)
     */
    fun play() {
        try {
            mediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    _isPlaying.value = true
                    startProgressTracking()
                    
                    // Notify network if this is the host
                    onPlayCommand?.invoke(_currentPosition.value)
                    
                    Log.d(tag, "Started playing")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting playback", e)
        }
    }
    
    /**
     * Pause playing (and sync with other devices if host)
     */
    fun pause() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    _isPlaying.value = false
                    stopProgressTracking()
                    
                    // Notify network if this is the host
                    onPauseCommand?.invoke()
                    
                    Log.d(tag, "Paused playing")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error pausing playback", e)
        }
    }
    
    /**
     * Seek to position (and sync with other devices if host)
     */
    fun seekTo(position: Long) {
        try {
            mediaPlayer?.seekTo(position.toInt())
            _currentPosition.value = position
            
            // Notify network if this is the host
            onSeekCommand?.invoke(position)
            
            Log.d(tag, "Seeked to position: $position")
        } catch (e: Exception) {
            Log.e(tag, "Error seeking to position: $position", e)
        }
    }
    
    /**
     * Set volume (0.0 to 1.0)
     */
    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        _volume.value = clampedVolume
        mediaPlayer?.setVolume(clampedVolume, clampedVolume)
    }
    
    /**
     * Load playlist of music tracks
     */
    fun loadPlaylist(tracks: List<MusicTrack>) {
        _playlist.value = tracks
        if (tracks.isNotEmpty()) {
            _currentTrackIndex.value = 0
            loadTrack(tracks[0])
        }
    }
    
    /**
     * Play next track in playlist
     */
    fun playNextTrack() {
        val playlist = _playlist.value
        val currentIndex = _currentTrackIndex.value
        
        if (playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
            val nextIndex = currentIndex + 1
            _currentTrackIndex.value = nextIndex
            val nextTrack = playlist[nextIndex]
            
            if (loadTrack(nextTrack)) {
                play()
                onTrackChangeCommand?.invoke(nextTrack, 0L)
            }
        } else {
            // End of playlist
            _isPlaying.value = false
            stopProgressTracking()
        }
    }
    
    /**
     * Play previous track in playlist
     */
    fun playPreviousTrack() {
        val playlist = _playlist.value
        val currentIndex = _currentTrackIndex.value
        
        if (playlist.isNotEmpty() && currentIndex > 0) {
            val prevIndex = currentIndex - 1
            _currentTrackIndex.value = prevIndex
            val prevTrack = playlist[prevIndex]
            
            if (loadTrack(prevTrack)) {
                play()
                onTrackChangeCommand?.invoke(prevTrack, 0L)
            }
        }
    }
    
    /**
     * Start tracking playback progress
     */
    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = playerScope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPosition.value = player.currentPosition.toLong()
                    }
                }
                delay(1000) // Update every second
            }
        }
    }
    
    /**
     * Stop tracking playback progress
     */
    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
    
    // Network sync methods for clients
    
    /**
     * Receive play command from host
     */
    fun receivePlayCommand(position: Long) {
        try {
            if (position != _currentPosition.value) {
                mediaPlayer?.seekTo(position.toInt())
            }
            mediaPlayer?.start()
            _isPlaying.value = true
            _currentPosition.value = position
            startProgressTracking()
            
            Log.d(tag, "Received play command at position: $position")
        } catch (e: Exception) {
            Log.e(tag, "Error executing play command", e)
        }
    }
    
    /**
     * Receive pause command from host
     */
    fun receivePauseCommand() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
            stopProgressTracking()
            
            Log.d(tag, "Received pause command")
        } catch (e: Exception) {
            Log.e(tag, "Error executing pause command", e)
        }
    }
    
    /**
     * Receive seek command from host
     */
    fun receiveSeekCommand(position: Long) {
        try {
            mediaPlayer?.seekTo(position.toInt())
            _currentPosition.value = position
            
            Log.d(tag, "Received seek command to position: $position")
        } catch (e: Exception) {
            Log.e(tag, "Error executing seek command", e)
        }
    }
    
    /**
     * Receive track change command from host
     */
    fun receiveTrackChangeCommand(track: MusicTrack, position: Long) {
        try {
            if (loadTrack(track)) {
                if (position > 0) {
                    seekTo(position)
                }
                Log.d(tag, "Received track change command: ${track.title}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error executing track change command", e)
        }
    }
    
    // Network callback setters
    
    fun setOnPlayCommand(callback: (Long) -> Unit) {
        onPlayCommand = callback
    }
    
    fun setOnPauseCommand(callback: () -> Unit) {
        onPauseCommand = callback
    }
    
    fun setOnSeekCommand(callback: (Long) -> Unit) {
        onSeekCommand = callback
    }
    
    fun setOnTrackChangeCommand(callback: (MusicTrack, Long) -> Unit) {
        onTrackChangeCommand = callback
    }
    
    /**
     * Get current playback state for sync
     */
    fun getPlaybackState(): PlaybackSyncState {
        return PlaybackSyncState(
            isPlaying = _isPlaying.value,
            position = _currentPosition.value,
            duration = _duration.value,
            track = _currentTrack.value,
            volume = _volume.value
        )
    }
    
    /**
     * Clean up MediaPlayer
     */
    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
    }
    
    /**
     * Release all resources
     */
    fun release() {
        Log.d(tag, "Releasing CustomPlayerManager")
        stopProgressTracking()
        releaseMediaPlayer()
        playerScope.cancel()
    }
}

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
    val volume: Float
)
