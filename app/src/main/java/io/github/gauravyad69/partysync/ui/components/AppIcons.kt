package io.github.gauravyad69.partysync.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.gauravyad69.partysync.network.ConnectionType

object AppIcons {
    // Connection Type Icons
    val bluetooth: ImageVector = Icons.Default.Bluetooth
    val wifi: ImageVector = Icons.Default.Wifi
    val hotspot: ImageVector = Icons.Default.Wifi
    val devices: ImageVector = Icons.Default.Devices
    
    // Navigation Icons
    val back: ImageVector = Icons.Default.ArrowBack
    val home: ImageVector = Icons.Default.Home
    val settings: ImageVector = Icons.Default.Settings
    
    // Action Icons
    val play: ImageVector = Icons.Default.PlayArrow
    val pause: ImageVector = Icons.Default.Pause
    val stop: ImageVector = Icons.Default.Stop
    val refresh: ImageVector = Icons.Default.Refresh
    val search: ImageVector = Icons.Default.Search
    val share: ImageVector = Icons.Default.Share
    val join: ImageVector = Icons.Default.PersonAdd
    
    // Status Icons
    val connected: ImageVector = Icons.Default.CheckCircle
    val disconnected: ImageVector = Icons.Default.Cancel
    val error: ImageVector = Icons.Default.Error
    val warning: ImageVector = Icons.Default.Warning
    val info: ImageVector = Icons.Default.Info
    
    // Content Icons
    val music: ImageVector = Icons.Default.MusicNote
    val groups: ImageVector = Icons.Default.Groups
    val person: ImageVector = Icons.Default.Person
    val volume: ImageVector = Icons.Default.VolumeUp
    
    // Utility function to get icon for connection type
    @Composable
    fun getConnectionTypeIcon(type: ConnectionType): ImageVector {
        return when (type) {
            ConnectionType.Bluetooth -> bluetooth
            ConnectionType.WiFiDirect -> wifi
            ConnectionType.LocalHotspot -> hotspot
        }
    }
}
