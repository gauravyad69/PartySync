package io.github.gauravyad69.partysync.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.Octicons
import compose.icons.octicons.*
import io.github.gauravyad69.partysync.network.ConnectionState
import io.github.gauravyad69.partysync.network.ConnectionType

@Composable
fun ConnectionErrorCard(
    error: ConnectionState.Error,
    selectedConnectionType: ConnectionType,
    onRetry: () -> Unit,
    onResetWiFi: () -> Unit,
    onSwitchToHotspot: () -> Unit,
    onSwitchToWiFiDirect: () -> Unit,
    onRetryWithReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            when {
                error.message.contains("busy", ignoreCase = true) -> {
                    BusyErrorSection(onResetWiFi, onSwitchToHotspot)
                }
                error.message.contains("generic hotspot error", ignoreCase = true) -> {
                    HotspotErrorSection(onRetryWithReset, onSwitchToWiFiDirect)
                }
                error.message.contains("incompatible mode", ignoreCase = true) -> {
                    IncompatibleModeSection(onRetry)
                }
                error.message.contains("WiFi Direct", ignoreCase = true) && 
                selectedConnectionType == ConnectionType.WiFiDirect -> {
                    WiFiDirectErrorSection(onSwitchToHotspot)
                }
                error.message.contains("not supported", ignoreCase = true) -> {
                    NotSupportedSection()
                }
                else -> {
                    GenericErrorSection(onRetry)
                }
            }
        }
    }
}

@Composable
private fun BusyErrorSection(
    onResetWiFi: () -> Unit,
    onSwitchToHotspot: () -> Unit
) {
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
            onClick = onResetWiFi,
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
            onClick = onSwitchToHotspot,
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

@Composable
private fun HotspotErrorSection(
    onRetryWithReset: () -> Unit,
    onSwitchToWiFiDirect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Octicons.Alert24,
            contentDescription = "Hotspot Error",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Hotspot Error:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
    Text(
        "The system couldn't create a hotspot. This usually happens when WiFi is still connected to a network.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onRetryWithReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Octicons.Sync24,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset WiFi & Retry")
        }
        
        OutlinedButton(
            onClick = onSwitchToWiFiDirect,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Octicons.Broadcast24,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try WiFi Direct Instead")
        }
    }
}

@Composable
private fun IncompatibleModeSection(onRetry: () -> Unit) {
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
        onClick = onRetry,
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

@Composable
private fun WiFiDirectErrorSection(onSwitchToHotspot: () -> Unit) {
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
        onClick = onSwitchToHotspot,
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

@Composable
private fun NotSupportedSection() {
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

@Composable
private fun GenericErrorSection(onRetry: () -> Unit) {
    OutlinedButton(
        onClick = onRetry,
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