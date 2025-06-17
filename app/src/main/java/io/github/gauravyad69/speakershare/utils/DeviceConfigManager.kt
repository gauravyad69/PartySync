package io.github.gauravyad69.speakershare.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
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
                Log.d(TAG, "Device is connected to WiFi, disconnecting...")
                
                // Disconnect from current WiFi network
                val disconnected = disconnectFromWiFi()
                if (!disconnected) {
                    return Result.failure(Exception("Failed to disconnect from WiFi. Please manually disconnect from WiFi networks in Settings."))
                }
                
                // Wait for disconnection to complete
                delay(2000)
            }
            
            // Step 2: For hotspot, we DON'T need WiFi to be enabled
            // The hotspot creates its own access point independently
            Log.d(TAG, "WiFi state for hotspot - WiFi enabled: ${wifiManager.isWifiEnabled}")
            
            // Step 3: Verify final state - just ensure we're not connected to any network
            val isReady = !isConnectedToWiFiNetwork()
            Log.d(TAG, "Device preparation complete. Ready: $isReady")
            
            Result.success(isReady)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing device for hotspot", e)
            Result.failure(e)
        }
    }
    
    /**
     * Prepares the device for WiFi Direct
     */
    suspend fun prepareForWiFiDirect(): Result<Boolean> {
        return try {
            Log.d(TAG, "Preparing device for WiFi Direct...")
            
            // WiFi Direct works better when not connected to other networks
            if (isConnectedToWiFiNetwork()) {
                Log.d(TAG, "Disconnecting from WiFi for better WiFi Direct performance...")
                disconnectFromWiFi()
                delay(2000)
            }
            
            // Ensure WiFi is enabled
            if (!wifiManager.isWifiEnabled) {
                Log.d(TAG, "Enabling WiFi for WiFi Direct...")
                @Suppress("DEPRECATION")
                wifiManager.setWifiEnabled(true)
                delay(3000)
            }
            
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing device for WiFi Direct", e)
            Result.failure(e)
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
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
    
    private fun disconnectFromWiFi(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // On Android 10+, we can't directly disconnect
                // The system handles this automatically when creating hotspot
                Log.d(TAG, "Android 10+ - system will handle WiFi disconnection")
                true
            } else {
                // On older versions, try to disconnect
                @Suppress("DEPRECATION")
                wifiManager.disconnect()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from WiFi", e)
            false
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
}

data class DeviceStatus(
    val wifiEnabled: Boolean,
    val connectedToWiFi: Boolean,
    val connectedNetworkName: String?,
    val canModifyWiFi: Boolean,
    val hotspotSupported: Boolean,
    val wifiDirectSupported: Boolean
)