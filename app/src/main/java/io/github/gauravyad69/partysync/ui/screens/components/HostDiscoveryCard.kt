package io.github.gauravyad69.partysync.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.Octicons
import compose.icons.octicons.*
import io.github.gauravyad69.partysync.network.ConnectionState
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.network.NetworkDevice

@Composable
fun HostDiscoveryCard(
    isScanning: Boolean,
    availableHosts: List<NetworkDevice>,
    connectionState: ConnectionState,
    selectedConnectionType: ConnectionType,
    onStartScanning: (ConnectionType) -> Unit,
    onConnectToHost: (NetworkDevice) -> Unit,
    onRefreshHosts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Octicons.Search24,
                    contentDescription = "Search",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Available Parties",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                
                if (!isScanning) {
                    IconButton(
                        onClick = onRefreshHosts
                    ) {
                        Icon(
                            Octicons.Sync24,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Connection type selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    onClick = { onStartScanning(ConnectionType.WiFiDirect) },
                    label = { Text("WiFi Direct") },
                    selected = selectedConnectionType == ConnectionType.WiFiDirect,
                    enabled = !isScanning,
                    modifier = Modifier.weight(1f)
                )
                
                FilterChip(
                    onClick = { onStartScanning(ConnectionType.LocalHotspot) },
                    label = { Text("Local Hotspot") },
                    selected = selectedConnectionType == ConnectionType.LocalHotspot,
                    enabled = !isScanning,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            when {
                isScanning -> {
                    ScanningIndicator()
                }
                availableHosts.isEmpty() -> {
                    EmptyHostsMessage(onStartScanning, selectedConnectionType)
                }
                else -> {
                    HostList(
                        hosts = availableHosts,
                        connectionState = connectionState,
                        onConnectToHost = onConnectToHost
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanningIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Scanning for parties...",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EmptyHostsMessage(
    onStartScanning: (ConnectionType) -> Unit,
    selectedConnectionType: ConnectionType
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Octicons.Broadcast24,
            contentDescription = "No parties",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "No parties found",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "Make sure you're close to a host device",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = { onStartScanning(selectedConnectionType) }
        ) {
            Icon(
                Octicons.Search24,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Again")
        }
    }
}

@Composable
private fun HostList(
    hosts: List<NetworkDevice>,
    connectionState: ConnectionState,
    onConnectToHost: (NetworkDevice) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.heightIn(max = 200.dp)
    ) {
        items(hosts) { host ->
            HostItem(
                host = host,
                isConnecting = connectionState is ConnectionState.Connecting,
                onConnect = { onConnectToHost(host) }
            )
        }
    }
}

@Composable
private fun HostItem(
    host: NetworkDevice,
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
    Card(
        onClick = onConnect,
        enabled = !isConnecting,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Octicons.People24,
                contentDescription = "Party",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    host.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    host.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Octicons.ChevronRight24,
                    contentDescription = "Connect",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}