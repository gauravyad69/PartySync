package io.github.gauravyad69.partysync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gauravyad69.partysync.network.ConnectionState
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.ui.viewmodels.JoinViewModel
import io.github.gauravyad69.partysync.utils.PermissionHandler
import io.github.gauravyad69.partysync.utils.RequestPermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(
    onBack: () -> Unit,
    viewModel: JoinViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val permissionHandler = remember { PermissionHandler(context) }
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    var selectedConnectionType by remember { mutableStateOf(ConnectionType.WiFiDirect) }
    
    LaunchedEffect(Unit) {
        if (!uiState.hasPermissions) {
            showPermissionDialog = true
        } else {
            viewModel.startScanning(selectedConnectionType)
        }
    }
    
    if (showPermissionDialog) {
        RequestPermissions(
            permissionHandler = permissionHandler
        ) { granted ->
            viewModel.onPermissionsResult(granted)
            showPermissionDialog = false
            if (granted) {
                viewModel.startScanning(selectedConnectionType)
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Join Party") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (!uiState.isConnected) {
                    IconButton(
                        onClick = { viewModel.refreshHosts() },
                        enabled = !uiState.isScanning
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            if (!uiState.isConnected) {
                item {
                    Text("Connection Type:", style = MaterialTheme.typography.titleMedium)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            onClick = { 
                                selectedConnectionType = ConnectionType.WiFiDirect
                                viewModel.startScanning(ConnectionType.WiFiDirect)
                            },
                            label = { Text("WiFi Direct") },
                            selected = selectedConnectionType == ConnectionType.WiFiDirect
                        )
                        
                        FilterChip(
                            onClick = { 
                                selectedConnectionType = ConnectionType.LocalHotspot
                                viewModel.startScanning(ConnectionType.LocalHotspot)
                            },
                            label = { Text("Local Hotspot") },
                            selected = selectedConnectionType == ConnectionType.LocalHotspot
                        )
                    }
                }
                
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Available Parties:", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
                
                if (uiState.availableHosts.isEmpty() && !uiState.isScanning) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "No parties found",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No parties found",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Make sure the host has started a party and you're within range",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                items(uiState.availableHosts) { host ->
                    Card(
                        onClick = { viewModel.joinHost(host) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(host.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${selectedConnectionType.displayName} • ${if (host.isHost) "Hosting" else "Available"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                            Text("Connected to", style = MaterialTheme.typography.titleMedium)
                            Text(
                                uiState.connectedHostName,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
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
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            OutlinedButton(
                                onClick = { viewModel.disconnect() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
                
                uiState.playbackState?.let { playback ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text("Now Playing", style = MaterialTheme.typography.titleMedium)
                                
                                if (playback.trackId.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Track ID: ${playback.trackId}")
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${playback.position / 1000}s",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        
                                        Icon(
                                            if (playback.isPlaying) Icons.Default.PlayArrow else Icons.Default.Menu,
                                            contentDescription = if (playback.isPlaying) "Playing" else "Paused",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Waiting for host to play music...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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