package io.github.gauravyad69.partysync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.icons.Octicons
import compose.icons.octicons.*
import io.github.gauravyad69.partysync.audio.AudioTrack
import io.github.gauravyad69.partysync.network.ConnectionState
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.ui.screens.components.*
import io.github.gauravyad69.partysync.ui.viewmodels.HostViewModel
import io.github.gauravyad69.partysync.utils.PermissionHandler
import io.github.gauravyad69.partysync.utils.RequestPermissions

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
    
    val sampleTracks = remember {
        listOf(
            AudioTrack(
                id = "test_music",
                title = "Test Music",
                artist = "Demo Artist",
                uri = "android.resource://io.github.gauravyad69.partysync/raw/test_music",
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
                        Icon(Octicons.X24, contentDescription = "Stop Hosting")
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
                DebugInfoCard(
                    uiState = uiState,
                    onRequestPermissions = { showPermissionDialog = true },
                    onRefreshPermissions = { viewModel.refreshPermissions() }
                )
            }
            
            // Device status card
            item {
                val deviceStatus = viewModel.getDeviceStatus()
                DeviceStatusCard(
                    deviceStatus = deviceStatus,
                    selectedConnectionType = uiState.selectedConnectionType,
                    connectionState = uiState.connectionState,
                    onPrepareDevice = { viewModel.prepareDevice() }
                )
            }
            
            if (!uiState.isHosting) {
                // Room configuration
                item {
                    RoomConfigurationSection(
                        roomName = uiState.roomName,
                        onRoomNameChange = viewModel::updateRoomName,
                        selectedConnectionType = uiState.selectedConnectionType,
                        onConnectionTypeChange = viewModel::updateConnectionType
                    )
                }
                
                // Start party button
                item {
                    StartPartySection(
                        uiState = uiState,
                        onStartHosting = { viewModel.startHosting() },
                        onRetry = { viewModel.startHosting() },
                        onResetWiFi = { viewModel.resetWiFiDirectState() },
                        onSwitchToHotspot = { viewModel.switchToLocalHotspot() },
                        onSwitchToWiFiDirect = { viewModel.updateConnectionType(ConnectionType.WiFiDirect) },
                        onRetryWithReset = { viewModel.retryWithWiFiReset() }
                    )
                }
            } else {
                // Party status and music controls
                item {
                    PartyStatusCard(uiState = uiState)
                }
                
                item {
                    MusicLibraryHeader()
                }
                
                items(sampleTracks) { track ->
                    TrackItem(
                        track = track,
                        isSelected = uiState.currentTrack?.id == track.id,
                        onTrackSelect = { viewModel.loadTrack(track) }
                    )
                }
                
                uiState.currentTrack?.let { track ->
                    item {
                        MusicControlCard(
                            currentTrack = track,
                            playbackState = uiState.playbackState,
                            onPlay = { viewModel.playMusic() },
                            onPause = { viewModel.pauseMusic() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomConfigurationSection(
    roomName: String,
    onRoomNameChange: (String) -> Unit,
    selectedConnectionType: ConnectionType,
    onConnectionTypeChange: (ConnectionType) -> Unit
) {
    Column {
        OutlinedTextField(
            value = roomName,
            onValueChange = onRoomNameChange,
            label = { Text("Room Name") },
            placeholder = { Text("Enter party name...") },
            leadingIcon = {
                Icon(
                    Octicons.Home24,
                    contentDescription = "Room",
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Connection Type:", style = MaterialTheme.typography.titleMedium)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                onClick = { onConnectionTypeChange(ConnectionType.Bluetooth) },
                label = { Text("Bluetooth") },
                selected = selectedConnectionType == ConnectionType.Bluetooth,
                leadingIcon = if (selectedConnectionType == ConnectionType.Bluetooth) {
                    {
                        Icon(
                            Octicons.DeviceMobile24,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null
            )
            
            FilterChip(
                onClick = { onConnectionTypeChange(ConnectionType.WiFiDirect) },
                label = { Text("WiFi Direct") },
                selected = selectedConnectionType == ConnectionType.WiFiDirect,
                leadingIcon = if (selectedConnectionType == ConnectionType.WiFiDirect) {
                    {
                        Icon(
                            Octicons.Broadcast24,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null
            )
            
            FilterChip(
                onClick = { onConnectionTypeChange(ConnectionType.LocalHotspot) },
                label = { Text("Local Hotspot") },
                selected = selectedConnectionType == ConnectionType.LocalHotspot,
                leadingIcon = if (selectedConnectionType == ConnectionType.LocalHotspot) {
                    {
                        Icon(
                            Octicons.Server24,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
private fun StartPartySection(
    uiState: io.github.gauravyad69.partysync.ui.viewmodels.HostUiState,
    onStartHosting: () -> Unit,
    onRetry: () -> Unit,
    onResetWiFi: () -> Unit,
    onSwitchToHotspot: () -> Unit,
    onSwitchToWiFiDirect: () -> Unit,
    onRetryWithReset: () -> Unit
) {
    val buttonEnabled = uiState.hasPermissions && uiState.connectionState != ConnectionState.Connecting
    
    Button(
        onClick = onStartHosting,
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
            else -> {
                Icon(
                    Octicons.Play24,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Party")
            }
        }
    }
    
    // Show connection error
    when (val state = uiState.connectionState) {
        is ConnectionState.Error -> {
            Spacer(modifier = Modifier.height(8.dp))
            ConnectionErrorCard(
                error = state,
                selectedConnectionType = uiState.selectedConnectionType,
                onRetry = onRetry,
                onResetWiFi = onResetWiFi,
                onSwitchToHotspot = onSwitchToHotspot,
                onSwitchToWiFiDirect = onSwitchToWiFiDirect,
                onRetryWithReset = onRetryWithReset
            )
        }
        else -> {
            if (!buttonEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                ButtonStatusIndicator(uiState)
            }
        }
    }
}

@Composable
private fun ButtonStatusIndicator(uiState: io.github.gauravyad69.partysync.ui.viewmodels.HostUiState) {
    val buttonEnabled = uiState.hasPermissions && uiState.connectionState != ConnectionState.Connecting
    
    Text(
        text = "Button enabled: $buttonEnabled (permissions: ${uiState.hasPermissions}, state: ${uiState.connectionState})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    if (!buttonEnabled) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when {
                    !uiState.hasPermissions -> Octicons.Shield24
                    uiState.connectionState == ConnectionState.Connecting -> Octicons.Clock24
                    else -> Octicons.Question24
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !uiState.hasPermissions -> "Missing permissions"
                    uiState.connectionState == ConnectionState.Connecting -> "Currently connecting"
                    else -> "Unknown reason"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PartyStatusCard(uiState: io.github.gauravyad69.partysync.ui.viewmodels.HostUiState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Octicons.People24,
                    contentDescription = "Party",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Party Status",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (statusIcon, statusColor) = when (uiState.connectionState) {
                    ConnectionState.Connected -> Octicons.CheckCircle24 to MaterialTheme.colorScheme.primary
                    ConnectionState.Connecting -> Octicons.Clock24 to MaterialTheme.colorScheme.secondary
                    else -> Octicons.XCircle24 to MaterialTheme.colorScheme.error
                }
                
                Icon(
                    statusIcon,
                    contentDescription = "Status",
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Octicons.DeviceMobile24,
                        contentDescription = "Devices",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connected devices: ${uiState.connectedDevices.size}")
                }
            }
        }
    }
}

@Composable
private fun MusicLibraryHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Octicons.Note24,
            contentDescription = "Music",
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Music Library", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TrackItem(
    track: AudioTrack,
    isSelected: Boolean,
    onTrackSelect: () -> Unit
) {
    Card(
        onClick = onTrackSelect,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Octicons.FileMedia24,
                contentDescription = "Track",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall)
                Text(track.artist, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Duration: ${track.duration / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    Octicons.Check24,
                    contentDescription = "Currently selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}