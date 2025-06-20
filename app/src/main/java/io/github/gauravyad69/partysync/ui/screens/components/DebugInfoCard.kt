package io.github.gauravyad69.partysync.ui.screens.components

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import compose.icons.Octicons
import compose.icons.octicons.*
import io.github.gauravyad69.partysync.network.ConnectionType
import io.github.gauravyad69.partysync.ui.viewmodels.HostUiState

@Composable
fun DebugInfoCard(
    uiState: HostUiState,
    onRequestPermissions: () -> Unit,
    onRefreshPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
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
            
            val hasWiFiDirect = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
            Text("WiFi Direct supported: $hasWiFiDirect")
            Text("Connection type: ${uiState.selectedConnectionType.displayName}")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRequestPermissions,
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
                    onClick = onRefreshPermissions,
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