package io.github.gauravyad69.speakershare.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import io.github.gauravyad69.speakershare.audio.AudioTrack
import io.github.gauravyad69.speakershare.network.ConnectionState
import io.github.gauravyad69.speakershare.network.ConnectionType
import io.github.gauravyad69.speakershare.ui.viewmodels.HostViewModel
import io.github.gauravyad69.speakershare.utils.DeviceStatus
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Octicons.Bug24,
                                contentDescription = "Debug",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Debug Info",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Android: ${android.os.Build.VERSION.SDK_INT}")
                        Text("Has permissions: ${uiState.hasPermissions}")
                        Text("Connection state: ${uiState.connectionState}")
                        Text("Room name: '${uiState.roomName}'")
                        
                        // Add WiFi Direct support check
                        val hasWiFiDirect = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
                        Text("WiFi Direct supported: $hasWiFiDirect")
                        
                        // Show current connection type
                        Text("Connection type: ${uiState.selectedConnectionType.displayName}")
                        
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
                                Icon(
                                    Octicons.Shield24,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Permissions")
                            }
                            
                            OutlinedButton(
                                onClick = { 
                                    viewModel.refreshPermissions() 
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Octicons.Sync24,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refresh")
                            }
                        }
                    }
                }
            }
            
            // Device status card
            item {
                val deviceStatus = viewModel.getDeviceStatus()
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (deviceStatus.isOptimal(uiState.selectedConnectionType)) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Octicons.DeviceMobile24,
                                contentDescription = "Device",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Device Status",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // WiFi Status - different messaging for different connection types
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (deviceStatus.wifiEnabled) Octicons.Broadcast24 else Octicons.Stop24,
                                contentDescription = "WiFi",
                                tint = when {
                                    uiState.selectedConnectionType == ConnectionType.WiFiDirect && !deviceStatus.wifiEnabled -> MaterialTheme.colorScheme.error
                                    uiState.selectedConnectionType == ConnectionType.LocalHotspot -> MaterialTheme.colorScheme.onSurfaceVariant // Neutral for hotspot
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("WiFi: ${if (deviceStatus.wifiEnabled) "Enabled" else "Disabled"}")
                            
                            // Add explanation for hotspot mode
                            if (uiState.selectedConnectionType == ConnectionType.LocalHotspot) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "(not required for hotspot)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Connection Status
                        if (deviceStatus.connectedToWiFi) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Octicons.Link24,
                                    contentDescription = "Connected",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connected to: ${deviceStatus.connectedNetworkName ?: "Unknown"}")
                            }
                        }
                        
                        // Feature Support
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Octicons.Checklist24,
                                contentDescription = "Features",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "WiFi Direct: ${if (deviceStatus.wifiDirectSupported) "✓" else "✗"} | Hotspot: ${if (deviceStatus.hotspotSupported) "✓" else "✗"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Show optimization status
                        val isOptimal = deviceStatus.isOptimal(uiState.selectedConnectionType)
                        if (!isOptimal) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val recommendation = when (uiState.selectedConnectionType) {
                                ConnectionType.LocalHotspot -> {
                                    when {
                                        !deviceStatus.hotspotSupported -> "Local Hotspot is not supported on this device"
                                        deviceStatus.connectedToWiFi -> "For hotspot mode, disconnect from WiFi networks"
                                        else -> "Device is ready for Local Hotspot"
                                    }
                                }
                                ConnectionType.WiFiDirect -> {
                                    when {
                                        !deviceStatus.wifiDirectSupported -> "WiFi Direct is not supported on this device"
                                        !deviceStatus.wifiEnabled -> "WiFi Direct requires WiFi to be enabled"
                                        deviceStatus.connectedToWiFi -> "For better WiFi Direct performance, disconnect from WiFi networks"
                                        else -> "Device is ready for WiFi Direct"
                                    }
                                }
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Octicons.LightBulb24,
                                    contentDescription = "Tip",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    recommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Only show optimize button if there's something we can fix
                            if (deviceStatus.connectedToWiFi || 
                                (uiState.selectedConnectionType == ConnectionType.WiFiDirect && !deviceStatus.wifiEnabled)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.prepareDevice() },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = uiState.connectionState != ConnectionState.Connecting
                                ) {
                                    if (uiState.connectionState == ConnectionState.Connecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Preparing...")
                                    } else {
                                        Icon(
                                            Octicons.Tools24,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Optimize Device Settings")
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Octicons.CheckCircle24,
                                    contentDescription = "Ready",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Device is optimally configured for ${uiState.selectedConnectionType.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                        leadingIcon = {
                            Icon(
                                Octicons.Home24,
                                contentDescription = "Room",
                                modifier = Modifier.size(20.dp)
                            )
                        },
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
                            selected = uiState.selectedConnectionType == ConnectionType.WiFiDirect,
                            leadingIcon = if (uiState.selectedConnectionType == ConnectionType.WiFiDirect) {
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
                            onClick = { viewModel.updateConnectionType(ConnectionType.LocalHotspot) },
                            label = { Text("Local Hotspot") },
                            selected = uiState.selectedConnectionType == ConnectionType.LocalHotspot,
                            leadingIcon = if (uiState.selectedConnectionType == ConnectionType.LocalHotspot) {
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
                    
                    // Show connection error and suggest fallback
                    when (val state = uiState.connectionState) {
                        is ConnectionState.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Octicons.Alert24,
                                            contentDescription = "Error",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Connection Error",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Show specific guidance based on error type
                                    when {
                                        state.message.contains("busy", ignoreCase = true) -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Octicons.Clock24,
                                                    contentDescription = "Busy",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "WiFi Direct Busy:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                            Text(
                                                "WiFi Direct is being used by another app or process.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { 
                                                        viewModel.resetWiFiDirectState()
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        Octicons.Sync24,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Reset WiFi")
                                                }
                                                
                                                OutlinedButton(
                                                    onClick = { 
                                                        viewModel.switchToLocalHotspot()
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        Octicons.Server24,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Use Hotspot")
                                                }
                                            }
                                        }
                                        
                                        state.message.contains("incompatible mode", ignoreCase = true) -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Octicons.LightBulb24,
                                                    contentDescription = "Solution",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Solution:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                            Text(
                                                "1. Disconnect from WiFi networks\n2. Turn WiFi off and on\n3. Try again",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = { 
                                                    viewModel.startHosting()
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    Octicons.Sync24,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Try Again")
                                            }
                                        }
                                        
                                        state.message.contains("WiFi Direct", ignoreCase = true) && 
                                        uiState.selectedConnectionType == ConnectionType.WiFiDirect -> {
                                            
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Octicons.ArrowSwitch24,
                                                    contentDescription = "Alternative",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Alternative:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                            Text(
                                                "Try using Local Hotspot instead:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = { 
                                                    viewModel.switchToLocalHotspot()
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    Octicons.Server24,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Switch to Local Hotspot")
                                            }
                                        }
                                        
                                        state.message.contains("not supported", ignoreCase = true) -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Octicons.XCircle24,
                                                    contentDescription = "Not Supported",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "This feature is not supported on your device.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                        
                                        else -> {
                                            OutlinedButton(
                                                onClick = { 
                                                    viewModel.startHosting()
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    Octicons.Sync24,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Retry")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            // Debug text for button state
                            Text(
                                text = "Button enabled: $buttonEnabled (permissions: ${uiState.hasPermissions}, state: ${uiState.connectionState})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Show what's preventing the button from being enabled
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
                
                item {
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
                
                items(sampleTracks) { track ->
                    Card(
                        onClick = { viewModel.loadTrack(track) },
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
                            
                            if (uiState.currentTrack?.id == track.id) {
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
                
                if (uiState.currentTrack != null) {
                    item {
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
                                        Octicons.Play24,
                                        contentDescription = "Now Playing",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Now Playing", style = MaterialTheme.typography.titleMedium)
                                }
                                
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
                                            if (isPlaying) Octicons.ThreeBars16 else Octicons.Play24,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            modifier = Modifier.size(32.dp)
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

// Update the extension function for optimal device state
@Composable
private fun DeviceStatus.isOptimal(connectionType: ConnectionType): Boolean {
    return when (connectionType) {
        ConnectionType.LocalHotspot -> {
            // For hotspot: only need to not be connected to WiFi network and hotspot support
            // WiFi can be on or off - doesn't matter
            !connectedToWiFi && hotspotSupported
        }
        ConnectionType.WiFiDirect -> {
            // For WiFi Direct: need WiFi enabled and support
            wifiEnabled && wifiDirectSupported
        }
    }
}