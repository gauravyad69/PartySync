package io.github.gauravyad69.partysync.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gauravyad69.partysync.audio.AudioStreamingMode
import io.github.gauravyad69.partysync.network.ConnectionState
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.ui.viewmodels.HostUiState
import io.github.gauravyad69.partysync.ui.viewmodels.HostViewModel
import io.github.gauravyad69.partysync.utils.RequestPermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostScreen(
    onBack: () -> Unit,
    viewModel: HostViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Handle permission requests
    RequestPermissions(
        permissionHandler = viewModel.permissionHandler,
        onPermissionsResult = { granted -> viewModel.onPermissionsResult(granted) }
    )

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
                        onStreamingModeSelected = viewModel::updateStreamingMode,
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
    onStreamingModeSelected: (AudioStreamingMode) -> Unit,
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

    // Streaming Mode Selection
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Audio Source",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Choose what audio to share with your party",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            StreamingModeCard(
                mode = AudioStreamingMode.MICROPHONE,
                icon = Icons.Default.Mic,
                isSelected = uiState.selectedStreamingMode == AudioStreamingMode.MICROPHONE,
                isEnabled = uiState.canChangeMode,
                onClick = { onStreamingModeSelected(AudioStreamingMode.MICROPHONE) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            StreamingModeCard(
                mode = AudioStreamingMode.SYSTEM_AUDIO,
                icon = Icons.Default.PhoneAndroid,
                isSelected = uiState.selectedStreamingMode == AudioStreamingMode.SYSTEM_AUDIO,
                isEnabled = uiState.canChangeMode,
                onClick = { onStreamingModeSelected(AudioStreamingMode.SYSTEM_AUDIO) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            StreamingModeCard(
                mode = AudioStreamingMode.CUSTOM_PLAYER,
                icon = Icons.Default.LibraryMusic,
                isSelected = uiState.selectedStreamingMode == AudioStreamingMode.CUSTOM_PLAYER,
                isEnabled = uiState.canChangeMode,
                onClick = { onStreamingModeSelected(AudioStreamingMode.CUSTOM_PLAYER) }
            )

            if (!uiState.canChangeMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stop streaming to change audio source",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
            // Progress indicator based on connection state
            when (val state = uiState.connectionState) {
                is ConnectionState.Connecting -> {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress percentage
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Setting up ${uiState.selectedConnectionType.displayName} connection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                else -> {
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
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Show specific tips based on connection type and progress
            when (uiState.selectedConnectionType) {
                ConnectionType.WiFiDirect -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "WiFi Direct Setup",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "This may take 15-20 seconds as we disconnect from WiFi and create a direct connection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                else -> {}
            }

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
                Column {
                    Text(
                        text = "Audio Source",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (uiState.selectedStreamingMode) {
                                AudioStreamingMode.MICROPHONE -> Icons.Default.Mic
                                AudioStreamingMode.SYSTEM_AUDIO -> Icons.Default.PhoneAndroid
                                AudioStreamingMode.CUSTOM_PLAYER -> Icons.Default.LibraryMusic
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.selectedStreamingMode.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!uiState.canChangeMode) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

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

                when (uiState.selectedStreamingMode) {
                    AudioStreamingMode.MICROPHONE, AudioStreamingMode.SYSTEM_AUDIO -> {
                        // Audio level indicator for live audio
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

                    AudioStreamingMode.CUSTOM_PLAYER -> {
                        // Music player controls
                        Column {
                            Text(
                                text = "Music Player Controls",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { /* TODO: Open file picker */ },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LibraryMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Load Music")
                                }

                                IconButton(
                                    onClick = { viewModel.pausePlayback() }
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = "Pause")
                                }

                                IconButton(
                                    onClick = { viewModel.resumePlayback() }
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = "Play")
                                }
                            }
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamingModeCard(
    mode: AudioStreamingMode,
    icon: ImageVector,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = isEnabled,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
