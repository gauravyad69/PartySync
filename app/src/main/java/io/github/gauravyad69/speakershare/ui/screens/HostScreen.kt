package io.github.gauravyad69.speakershare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gauravyad69.speakershare.audio.AudioTrack
import io.github.gauravyad69.speakershare.network.ConnectionState
import io.github.gauravyad69.speakershare.network.ConnectionType
import io.github.gauravyad69.speakershare.ui.viewmodels.HostViewModel
import io.github.gauravyad69.speakershare.utils.PermissionHandler
import io.github.gauravyad69.speakershare.utils.RequestPermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostScreen(
    onBack: () -> Unit,
    viewModel: HostViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val permissionHandler = remember { PermissionHandler(context) }
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // Updated sample tracks using your test_music.mp3
    val sampleTracks = remember {
        listOf(
            AudioTrack(
                id = "test_music",
                title = "Test Music",
                artist = "Demo Artist",
                uri = "android.resource://io.github.gauravyad69.speakershare/raw/test_music",
                duration = 180000L
            )
        )
    }
    
    LaunchedEffect(Unit) {
        if (!uiState.hasPermissions) {
            showPermissionDialog = true
        }
    }
    
    if (showPermissionDialog) {
        RequestPermissions(
            permissionHandler = permissionHandler
        ) { granted ->
            viewModel.onPermissionsResult(granted)
            showPermissionDialog = false
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Host Party") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (uiState.isHosting) {
                    IconButton(onClick = { viewModel.stopHosting() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Stop Hosting")
                    }
                }
            }
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Debug info card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.hasPermissions) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Debug Info",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("Android: ${android.os.Build.VERSION.SDK_INT}")
                        Text("Has permissions: ${uiState.hasPermissions}")
                        Text("Connection state: ${uiState.connectionState}")
                        Text("Room name: '${uiState.roomName}'")
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { 
                                    showPermissionDialog = true 
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Request Permissions")
                            }
                            
                            OutlinedButton(
                                onClick = { 
                                    viewModel.refreshPermissions() 
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Refresh")
                            }
                        }
                    }
                }
            }
            
            if (!uiState.isHosting) {
                item {
                    OutlinedTextField(
                        value = uiState.roomName,
                        onValueChange = viewModel::updateRoomName,
                        label = { Text("Room Name") },
                        placeholder = { Text("Enter party name...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                item {
                    Text("Connection Type:", style = MaterialTheme.typography.titleMedium)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            onClick = { viewModel.updateConnectionType(ConnectionType.WiFiDirect) },
                            label = { Text("WiFi Direct") },
                            selected = uiState.selectedConnectionType == ConnectionType.WiFiDirect
                        )
                        
                        FilterChip(
                            onClick = { viewModel.updateConnectionType(ConnectionType.LocalHotspot) },
                            label = { Text("Local Hotspot") },
                            selected = uiState.selectedConnectionType == ConnectionType.LocalHotspot
                        )
                    }
                }
                
                item {
                    val buttonEnabled = uiState.hasPermissions && uiState.connectionState != ConnectionState.Connecting
                    
                    Button(
                        onClick = { viewModel.startHosting() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = buttonEnabled
                    ) {
                        when (uiState.connectionState) {
                            ConnectionState.Connecting -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting...")
                            }
                            else -> Text("Start Party")
                        }
                    }
                    
                    // Debug text for button state
                    Text(
                        text = "Button enabled: $buttonEnabled (permissions: ${uiState.hasPermissions}, state: ${uiState.connectionState})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Show what's preventing the button from being enabled
                    if (!buttonEnabled) {
                        Text(
                            text = when {
                                !uiState.hasPermissions -> "❌ Missing permissions"
                                uiState.connectionState == ConnectionState.Connecting -> "⏳ Currently connecting"
                                else -> "❓ Unknown reason"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                // Party is active - show hosting status
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "Party Status",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusColor = when (uiState.connectionState) {
                                    ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                                    ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.error
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(statusColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    when (uiState.connectionState) {
                                        ConnectionState.Connected -> "Connected"
                                        ConnectionState.Connecting -> "Connecting..."
                                        ConnectionState.Disconnected -> "Disconnected"
                                        is ConnectionState.Error -> "Error: ${(uiState.connectionState as ConnectionState.Error).message}"
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Room: ${uiState.roomName.ifEmpty { "Default Room" }}")
                            Text("Connection: ${uiState.selectedConnectionType.displayName}")
                            
                            if (uiState.connectedDevices.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Connected devices: ${uiState.connectedDevices.size}")
                            }
                        }
                    }
                }
                
                item {
                    Text("Music Library", style = MaterialTheme.typography.titleMedium)
                }
                
                items(sampleTracks) { track ->
                    Card(
                        onClick = { viewModel.loadTrack(track) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.titleSmall)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Duration: ${track.duration / 1000}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (uiState.currentTrack?.id == track.id) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Currently selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                if (uiState.currentTrack != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text("Now Playing", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(uiState.currentTrack!!.title)
                                Text(
                                    uiState.currentTrack!!.artist,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val isPlaying = uiState.playbackState?.isPlaying == true
                                    
                                    IconButton(
                                        onClick = {
                                            if (isPlaying) {
                                                viewModel.pauseMusic()
                                            } else {
                                                viewModel.playMusic()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Menu else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play"
                                        )
                                    }
                                }
                                
                                uiState.playbackState?.let { playback ->
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
                }
            }
        }
    }
}