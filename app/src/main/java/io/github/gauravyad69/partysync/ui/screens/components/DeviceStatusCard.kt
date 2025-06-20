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
import io.github.gauravyad69.partysync.utils.DeviceStatus

@Composable
fun DeviceStatusCard(
    deviceStatus: DeviceStatus,
    selectedConnectionType: ConnectionType,
    connectionState: ConnectionState,
    onPrepareDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOptimal = deviceStatus.isOptimal(selectedConnectionType)
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOptimal) 
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
            
            DeviceStatusInfo(deviceStatus, selectedConnectionType)
            
            if (!isOptimal) {
                Spacer(modifier = Modifier.height(8.dp))
                DeviceOptimizationSection(
                    deviceStatus = deviceStatus,
                    selectedConnectionType = selectedConnectionType,
                    connectionState = connectionState,
                    onPrepareDevice = onPrepareDevice
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                OptimalStatusIndicator(selectedConnectionType)
            }
        }
    }
}

@Composable
private fun DeviceStatusInfo(
    deviceStatus: DeviceStatus,
    selectedConnectionType: ConnectionType
) {
    // WiFi Status
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (deviceStatus.wifiEnabled) Octicons.Broadcast24 else Octicons.Stop24,
            contentDescription = "WiFi",
            tint = when {
                selectedConnectionType == ConnectionType.WiFiDirect && !deviceStatus.wifiEnabled -> 
                    MaterialTheme.colorScheme.error
                selectedConnectionType == ConnectionType.LocalHotspot -> 
                    MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("WiFi: ${if (deviceStatus.wifiEnabled) "Enabled" else "Disabled"}")
        
        if (selectedConnectionType == ConnectionType.LocalHotspot) {
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
}

@Composable
private fun DeviceOptimizationSection(
    deviceStatus: DeviceStatus,
    selectedConnectionType: ConnectionType,
    connectionState: ConnectionState,
    onPrepareDevice: () -> Unit
) {
    val recommendation = when (selectedConnectionType) {
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
    
    if (deviceStatus.connectedToWiFi || 
        (selectedConnectionType == ConnectionType.WiFiDirect && !deviceStatus.wifiEnabled)) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPrepareDevice,
            modifier = Modifier.fillMaxWidth(),
            enabled = connectionState != ConnectionState.Connecting
        ) {
            if (connectionState == ConnectionState.Connecting) {
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
}

@Composable
private fun OptimalStatusIndicator(selectedConnectionType: ConnectionType) {
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
            "Device is optimally configured for ${selectedConnectionType.displayName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DeviceStatus.isOptimal(connectionType: ConnectionType): Boolean {
    return when (connectionType) {
        ConnectionType.LocalHotspot -> {
            !connectedToWiFi && hotspotSupported
        }
        ConnectionType.WiFiDirect -> {
            wifiEnabled && wifiDirectSupported
        }
    }
}