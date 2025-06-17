package io.github.gauravyad69.speakershare.sync

import android.util.Log
import io.github.gauravyad69.speakershare.audio.AudioStreamer
import io.github.gauravyad69.speakershare.audio.SyncedPlayback
import io.github.gauravyad69.speakershare.network.NetworkConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SyncCommand(
    val type: String,
    val trackId: String = "",
    val position: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

class SyncManager(
    private val networkConnection: NetworkConnection,
    private val audioStreamer: AudioStreamer,
    private val isHost: Boolean,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    
    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_TOLERANCE_MS = 100L
        
        const val CMD_PLAY = "PLAY"
        const val CMD_PAUSE = "PAUSE"
        const val CMD_SEEK = "SEEK"
        const val CMD_SYNC = "SYNC"
    }
    
    private var lastSyncTime = 0L
    
    init {
        if (isHost) {
            startHostSyncBroadcast()
        } else {
            startClientSyncListener()
        }
    }
    
    suspend fun sendPlayCommand(position: Long = 0L) {
        if (isHost) {
            val command = SyncCommand(
                type = CMD_PLAY,
                position = position,
                timestamp = System.currentTimeMillis()
            )
            sendCommand(command)
            audioStreamer.play(position)
        }
    }
    
    suspend fun sendPauseCommand() {
        if (isHost) {
            val command = SyncCommand(
                type = CMD_PAUSE,
                timestamp = System.currentTimeMillis()
            )
            sendCommand(command)
            audioStreamer.pause()
        }
    }
    
    suspend fun sendSeekCommand(position: Long) {
        if (isHost) {
            val command = SyncCommand(
                type = CMD_SEEK,
                position = position,
                timestamp = System.currentTimeMillis()
            )
            sendCommand(command)
            audioStreamer.seekTo(position)
        }
    }
    
    private fun startHostSyncBroadcast() {
        scope.launch {
            audioStreamer.getPlaybackState().collectLatest { playback ->
                if (System.currentTimeMillis() - lastSyncTime > 5000) { // Sync every 5 seconds
                    val syncCommand = SyncCommand(
                        type = CMD_SYNC,
                        trackId = playback.trackId,
                        position = playback.position,
                        timestamp = playback.timestamp
                    )
                    sendCommand(syncCommand)
                    lastSyncTime = System.currentTimeMillis()
                }
            }
        }
    }
    
    private fun startClientSyncListener() {
        scope.launch {
            networkConnection.receiveData().collectLatest { data ->
                try {
                    val commandJson = String(data)
                    val command = Json.decodeFromString<SyncCommand>(commandJson)
                    handleCommand(command)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse sync command", e)
                }
            }
        }
    }
    
    private suspend fun handleCommand(command: SyncCommand) {
        val networkDelay = System.currentTimeMillis() - command.timestamp
        val adjustedPosition = if (command.type == CMD_PLAY || command.type == CMD_SYNC) {
            command.position + networkDelay
        } else {
            command.position
        }
        
        when (command.type) {
            CMD_PLAY -> {
                Log.d(TAG, "Received play command, position: $adjustedPosition")
                audioStreamer.play(adjustedPosition)
            }
            
            CMD_PAUSE -> {
                Log.d(TAG, "Received pause command")
                audioStreamer.pause()
            }
            
            CMD_SEEK -> {
                Log.d(TAG, "Received seek command, position: $adjustedPosition")
                audioStreamer.seekTo(adjustedPosition)
            }
            
            CMD_SYNC -> {
                val currentPosition = audioStreamer.getCurrentPosition()
                val positionDiff = kotlin.math.abs(currentPosition - adjustedPosition)
                
                if (positionDiff > SYNC_TOLERANCE_MS) {
                    Log.d(TAG, "Syncing position from $currentPosition to $adjustedPosition")
                    audioStreamer.seekTo(adjustedPosition)
                }
            }
        }
    }
    
    private suspend fun sendCommand(command: SyncCommand) {
        try {
            val commandJson = Json.encodeToString(command)
            networkConnection.sendData(commandJson.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send sync command", e)
        }
    }
}