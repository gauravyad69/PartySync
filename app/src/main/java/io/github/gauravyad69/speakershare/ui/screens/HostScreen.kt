package io.github.gauravyad69.speakershare.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
    
    // Sample music tracks for demo
    val sampleTracks = remember {
        listOf(
            AudioTrack(
                id = "1",
                title = "Sample Song 1",
                artist = "Demo Artist",
                uri = "android.resource://io.github.gauravyad69.speakershare/raw/sample_song", // You'll need to add this
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
                        Icon(Icons.Default.Stop, contentDescription = "Stop Hosting")
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
            if (!uiState.isHosting) {
                item {
                    OutlinedTextField(
                        value = uiState.roomName,
                        onValueChange = viewModel::updateRoomName,
                        label = { Text("Room Name") },
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
                    Button(
                        onClick = { viewModel.startHosting() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.hasPermissions && uiState.connectionState != ConnectionState.Connecting
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
                }
            } else {
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
                                        is ConnectionState.Error -> "Error: ${uiState.connectionState.message}"
                                    }
                                )
                            }
                            
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
                                
                                Text(uiState.currentTrack.title)
                                Text(
                                    uiState.currentTrack.artist,
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
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}