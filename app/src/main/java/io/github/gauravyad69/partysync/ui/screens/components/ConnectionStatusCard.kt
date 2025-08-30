package io.github.gauravyad69.partysync.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.Octicons
import compose.icons.octicons.*
import io.github.gauravyad69.partysync.audio.SyncedPlayback
import io.github.gauravyad69.partysync.network.ConnectionState

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    connectedHostName: String,
    connectionState: ConnectionState,
    playbackState: SyncedPlayback?,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (isConnected) Octicons.Link24 else Octicons.LinkExternal24,
                    contentDescription = "Connection",
                    modifier = Modifier.size(20.dp),
                    tint = if (isConnected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isConnected) "Connected to Party" else "Connection Status",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                
                if (isConnected) {
                    IconButton(
                        onClick = onDisconnect
                    ) {
                        Icon(
                            Octicons.X24,
                            contentDescription = "Disconnect",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isConnected) {
                ConnectedContent(
                    hostName = connectedHostName,
                    playbackState = playbackState
                )
            } else {
                DisconnectedContent(connectionState)
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    hostName: String,
    playbackState: SyncedPlayback?
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Octicons.CheckCircle24,
                contentDescription = "Connected",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Host: $hostName",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        playbackState?.let { playback ->
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (playback.isPlaying) Octicons.Play24 else Octicons.Stop24,
                    contentDescription = "Playback",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (playback.isPlaying) 
                        "Playing: ${playback.position / 1000}s" 
                    else 
                        "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (playback.trackId.isNotEmpty()) {
                Text(
                    "Track: ${playback.trackId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DisconnectedContent(connectionState: ConnectionState) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, color, text) = when (connectionState) {
            ConnectionState.Connecting -> Triple(
                Octicons.Clock24,
                MaterialTheme.colorScheme.secondary,
                "Connecting..."
            )
            is ConnectionState.Error -> Triple(
                Octicons.Alert24,
                MaterialTheme.colorScheme.error,
                "Error: ${connectionState.message}"
            )
            else -> Triple(
                Octicons.XCircle24,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Not connected to any party"
            )
        }
        
        Icon(
            icon,
            contentDescription = "Status",
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}