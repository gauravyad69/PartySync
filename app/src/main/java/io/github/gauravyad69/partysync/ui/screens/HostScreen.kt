package io.github.gauravyad69.partysync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gauravyad69.partysync.network.ConnectionState
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.ui.viewmodels.HostViewModel
import io.github.gauravyad69.partysync.ui.viewmodels.HostUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostScreen(
    onBack: () -> Unit,
    viewModel: HostViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Custom App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Host Audio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState.connectionState) {
                is ConnectionState.Disconnected -> {
                    ConnectionSetupScreen(
                        uiState = uiState,
                        onConnectionTypeSelected = viewModel::updateConnectionType,
                        onRoomNameChanged = viewModel::updateRoomName,
                        onStartHosting = { viewModel.startHosting() }
                    )
                }
                is ConnectionState.Connecting -> {
                    ConnectingScreen(
                        uiState = uiState,
                        onCancel = viewModel::stopHosting
                    )
                }
                is ConnectionState.Connected -> {
                    HostingScreen(
                        uiState = uiState,
                        onStopHosting = viewModel::stopHosting,
                        onPlayMusic = viewModel::playMusic,
                        onPauseMusic = viewModel::pauseMusic,
                        onSeek = viewModel::seekTo,
                        viewModel = viewModel
                    )
                }
                is ConnectionState.Error -> {
                    ErrorScreen(
                        uiState = uiState,
                        onRetry = { viewModel.startHosting() },
                        onTryDifferentConnection = viewModel::updateConnectionType,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionSetupScreen(
    uiState: HostUiState,
    onConnectionTypeSelected: (ConnectionType) -> Unit,
    onRoomNameChanged: (String) -> Unit,
    onStartHosting: () -> Unit
) {
    // Welcome Section
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Share Your Audio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Create a party and let others join to hear your music",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    // Room Name Input
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Party Name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.roomName,
                onValueChange = onRoomNameChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter a fun party name...") },
                singleLine = true
            )
        }
    }
    
    // Connection Type Selection
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Connection Method",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            ConnectionTypeCard(
                type = ConnectionType.Bluetooth,
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth",
                subtitle = "Reliable • 30m range • Works everywhere",
                description = "Best for small groups and outdoor events",
                isSelected = uiState.selectedConnectionType == ConnectionType.Bluetooth,
                isRecommended = true,
                onClick = { onConnectionTypeSelected(ConnectionType.Bluetooth) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ConnectionTypeCard(
                type = ConnectionType.WiFiDirect,
                icon = Icons.Default.Wifi,
                title = "WiFi Direct",
                subtitle = "High Quality • 100m range • Device-to-device",
                description = "Better audio quality, longer range",
                isSelected = uiState.selectedConnectionType == ConnectionType.WiFiDirect,
                onClick = { onConnectionTypeSelected(ConnectionType.WiFiDirect) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ConnectionTypeCard(
                type = ConnectionType.LocalHotspot,
                icon = Icons.Default.Wifi,
                title = "WiFi Hotspot",
                subtitle = "Network mode • Multiple devices",
                description = "For devices on same WiFi network",
                isSelected = uiState.selectedConnectionType == ConnectionType.LocalHotspot,
                onClick = { onConnectionTypeSelected(ConnectionType.LocalHotspot) }
            )
        }
    }
    
    // Device Status - simplified for now
    // TODO: Add device status checking back once DeviceStatus is properly integrated
    // if (!uiState.deviceStatus.isOptimal(uiState.selectedConnectionType)) {
    //     DeviceStatusWarning(
    //         connectionType = uiState.selectedConnectionType
    //     )
    // }
    
        // Start Button
    Button(
        onClick = onStartHosting,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = uiState.roomName.isNotBlank()
    ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Start Hosting",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ConnectionTypeCard(
    type: ConnectionType,
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    isRecommended: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) 
            null 
        else 
            CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) 
                            MaterialTheme.colorScheme.onPrimary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isRecommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiary
                        ) {
                            Text(
                                text = "RECOMMENDED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DeviceStatusWarning(
    connectionType: ConnectionType
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Device Setup Needed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (connectionType) {
                    ConnectionType.Bluetooth -> "Please enable Bluetooth in your device settings"
                    ConnectionType.WiFiDirect -> "WiFi should be enabled for best performance"
                    ConnectionType.LocalHotspot -> "Disconnect from WiFi networks for hotspot mode"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// We'll implement the other screen composables (ConnectingScreen, HostingScreen, ErrorScreen) 
// in the next steps to keep this manageable

@Composable
private fun ConnectingScreen(
    uiState: HostUiState,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Starting ${uiState.selectedConnectionType.displayName}...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Setting up your audio sharing session",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = onCancel
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun HostingScreen(
    uiState: HostUiState,
    onStopHosting: () -> Unit,
    onPlayMusic: () -> Unit,
    onPauseMusic: () -> Unit,
    onSeek: (Long) -> Unit,
    viewModel: HostViewModel = viewModel()
) {
    // Success state - show hosting controls
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "🎉 ${uiState.roomName}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your audio is now being shared!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Others can join using ${uiState.selectedConnectionType.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Audio Streaming Controls
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Streaming",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                // Audio capture toggle button
                Button(
                    onClick = {
                        if (uiState.isCapturingAudio) {
                            viewModel.stopAudioCapture()
                        } else {
                            viewModel.startAudioCapture()
                        }
                    },
                    colors = if (uiState.isCapturingAudio) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Icon(
                        imageVector = if (uiState.isCapturingAudio) Icons.Default.MusicNote else Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.isCapturingAudio) "Stop Audio" else "Start Audio")
                }
            }
            
            if (uiState.isCapturingAudio) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Audio level indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Audio Level:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp)
                    )
                    LinearProgressIndicator(
                        progress = { uiState.audioLevel },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = if (uiState.audioLevel > 0.1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = "${(uiState.audioLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            // Connected clients indicator
            if (uiState.streamingClients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${uiState.streamingClients.size} clients receiving audio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Stop Hosting Button
    Button(
        onClick = onStopHosting,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text("Stop Hosting", color = MaterialTheme.colorScheme.onError)
    }
}

@Composable
private fun ErrorScreen(
    uiState: HostUiState,
    onRetry: () -> Unit,
    onTryDifferentConnection: (ConnectionType) -> Unit,
    onBack: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Connection Failed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = (uiState.connectionState as ConnectionState.Error).message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Retry")
                }
            }
        }
    }
}
