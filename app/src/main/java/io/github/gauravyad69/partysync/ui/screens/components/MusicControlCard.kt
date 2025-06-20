package io.github.gauravyad69.partysync.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.Octicons
import compose.icons.octicons.*
import io.github.gauravyad69.partysync.audio.AudioTrack
import io.github.gauravyad69.partysync.audio.SyncedPlayback

@Composable
fun MusicControlCard(
    currentTrack: AudioTrack,
    playbackState: SyncedPlayback?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Octicons.Play24,
                    contentDescription = "Now Playing",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Now Playing", style = MaterialTheme.typography.titleMedium)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(currentTrack.title)
            Text(
                currentTrack.artist,
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                val isPlaying = playbackState?.isPlaying == true
                
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            onPause()
                        } else {
                            onPlay()
                        }
                    }
                ) {
                    Icon(
                        if (isPlaying) Octicons.Stop24 else Octicons.Play24,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            playbackState?.let { playback ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Position: ${playback.position / 1000}s",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Playing: ${playback.isPlaying}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Track ID: ${playback.trackId}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}