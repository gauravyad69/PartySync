package io.github.gauravyad69.partysync.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.delay

class DeviceConfigManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceConfigManager"
    }
    
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * Prepares the device for creating a Local Hotspot
     * Returns true if device is ready, false if manual intervention needed
     */
    suspend fun prepareForLocalHotspot(): Result<Boolean> {
        return try {
            Log.d(TAG, "Preparing device for Local Hotspot...")
            
            // Step 1: Check if WiFi is connected to any network
            if (isConnectedToWiFiNetwork()) {
                Log.d(TAG, "Device is connected to WiFi, attempting to disconnect...")
                
                // Try multiple methods to disconnect from WiFi
                val disconnected = forceDisconnectFromWiFi()
                if (!disconnected) {
                    return Result.failure(Exception("Please manually disconnect from WiFi networks in Settings and try again."))
                }
                
                // Wait longer for disconnection to complete
                delay(3000)
                
                // Verify disconnection worked
                if (isConnectedToWiFiNetwork()) {
                    Log.w(TAG, "Still connected to WiFi after disconnection attempt")
                    return Result.failure(Exception("Failed to disconnect from WiFi. Please manually turn off WiFi or disconnect from all networks."))
                }
            }
            
            // Step 2: Verify final state - ensure we're not connected to any network
            val isReady = !isConnectedToWiFiNetwork()
            Log.d(TAG, "Device preparation complete. Ready: $isReady")
            
            if (!isReady) {
                return Result.failure(Exception("Device is still connected to WiFi. Please disconnect manually."))
            }
            
            Result.success(isReady)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing device for hotspot", e)
            Result.failure(e)
        }
    }
    
    /**
     * Prepares the device for WiFi Direct with better state management
     */
    suspend fun prepareForWiFiDirect(): Result<Boolean> {
        return try {
            Log.d(TAG, "Preparing device for WiFi Direct...")
            
            // Step 1: Reset WiFi Direct state to clear any busy conditions
            resetWiFiDirectState()
            
            // Step 2: WiFi Direct works better when not connected to other networks
            if (isConnectedToWiFiNetwork()) {
                Log.d(TAG, "Disconnecting from WiFi for better WiFi Direct performance...")
                forceDisconnectFromWiFi()
                delay(2000)
            }
            
            // Step 3: Ensure WiFi is enabled
            if (!wifiManager.isWifiEnabled) {
                Log.d(TAG, "Enabling WiFi for WiFi Direct...")
                @Suppress("DEPRECATION")
                wifiManager.setWifiEnabled(true)
                delay(3000)
            }
            
            // Step 4: Additional delay to ensure WiFi Direct is ready
            delay(1000)
            
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing device for WiFi Direct", e)
            Result.failure(e)
        }
    }
    
    /**
     * Force disconnect from WiFi using multiple methods
     */
    private suspend fun forceDisconnectFromWiFi(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Android 10+ - attempting advanced WiFi disconnection...")
                
                // Method 1: Clear all network suggestions (if any)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        wifiManager.removeNetworkSuggestions(emptyList())
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "No network suggestions to remove")
                }
                
                // Method 2: Disable and re-enable WiFi to force disconnection
                Log.d(TAG, "Cycling WiFi to force disconnection...")
                @Suppress("DEPRECATION")
                wifiManager.setWifiEnabled(false)
                delay(2000)
                
                @Suppress("DEPRECATION") 
                wifiManager.setWifiEnabled(true)
                delay(3000)
                
                // Method 3: If still connected, try to disconnect from current network
                if (isConnectedToWiFiNetwork()) {
                    Log.d(TAG, "Still connected, attempting to disconnect current network...")
                    try {
                        wifiManager.disconnect()
                        delay(2000)
                    } catch (e: Exception) {
                        Log.w(TAG, "Direct disconnect failed", e)
                    }
                }
                
                true
            } else {
                // On older versions, direct disconnect should work
                Log.d(TAG, "Pre-Android 10 - using direct disconnect...")
                @Suppress("DEPRECATION")
                wifiManager.disconnect()
                delay(2000)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error force disconnecting from WiFi", e)
            false
        }
    }
    
    /**
     * Get device configuration status for diagnostics
     */
    fun getDeviceStatus(): DeviceStatus {
        return DeviceStatus(
            wifiEnabled = wifiManager.isWifiEnabled,
            connectedToWiFi = isConnectedToWiFiNetwork(),
            connectedNetworkName = getConnectedNetworkName(),
            canModifyWiFi = canModifyWiFiState(),
            hotspotSupported = isHotspotSupported(),
            wifiDirectSupported = isWiFiDirectSupported()
        )
    }
    
    private fun isConnectedToWiFiNetwork(): Boolean {
        return try {
            // Method 1: Check through ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isConnectedViaWiFi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            
            // Method 2: Check through WifiManager connection info
            val connectionInfo = wifiManager.connectionInfo
            val isConnectedToNetwork = connectionInfo?.networkId != -1 && connectionInfo?.ssid != null && connectionInfo.ssid != "<unknown ssid>"
            
            val result = isConnectedViaWiFi && isConnectedToNetwork
            Log.d(TAG, "WiFi connection check - ConnectivityManager: $isConnectedViaWiFi, WifiManager: $isConnectedToNetwork, Final: $result")
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi connection", e)
            false
        }
    }
    
    private fun getConnectedNetworkName(): String? {
        return try {
            if (isConnectedToWiFiNetwork()) {
                val connectionInfo = wifiManager.connectionInfo
                connectionInfo.ssid?.replace("\"", "")
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network name", e)
            null
        }
    }
    
    private fun canModifyWiFiState(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Check if app can modify WiFi state on Android 10+
                Settings.System.canWrite(context)
            } else {
                true // Older versions allow modification
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isHotspotSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
    
    private fun isWiFiDirectSupported(): Boolean {
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_DIRECT)
    }
    
    /**
     * Reset WiFi Direct state to clear busy conditions
     */
    private suspend fun resetWiFiDirectState() {
        try {
            Log.d(TAG, "Resetting WiFi Direct state...")
            
            // Turn WiFi off and on to reset WiFi Direct state
            if (wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                wifiManager.setWifiEnabled(false)
                delay(2000)
                
                @Suppress("DEPRECATION")
                wifiManager.setWifiEnabled(true)
                delay(3000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting WiFi Direct state", e)
        }
    }
}

data class DeviceStatus(
    val wifiEnabled: Boolean,
    val connectedToWiFi: Boolean,
    val connectedNetworkName: String?,
    val canModifyWiFi: Boolean,
    val hotspotSupported: Boolean,
    val wifiDirectSupported: Boolean
)