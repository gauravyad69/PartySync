package io.github.gauravyad69.partysync.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

class PermissionHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "PermissionHandler"
    }
    
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        
        // Handle storage permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - Use granular media permissions
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            // Android 12 and below - Use legacy storage permission
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        // For Android 13+, we need NEARBY_WIFI_DEVICES for WiFi Direct
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        Log.d(TAG, "Required permissions for Android ${Build.VERSION.SDK_INT}: $permissions")
        return permissions.toTypedArray()
    }
    
    fun hasAllPermissions(): Boolean {
        val required = getRequiredPermissions()
        val hasAll = required.all { permission ->
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $permission: $granted")
            granted
        }
        Log.d(TAG, "Has all permissions: $hasAll")
        return hasAll
    }
    
    fun getMissingPermissions(): List<String> {
        val missing = getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "Missing permissions: $missing")
        return missing
    }
}

@Composable
fun RequestPermissions(
    permissionHandler: PermissionHandler,
    onPermissionsResult: (Boolean) -> Unit
) {
    var permissionsRequested by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("RequestPermissions", "Permission results: $permissions")
        val allGranted = permissions.values.all { it }
        Log.d("RequestPermissions", "All granted: $allGranted")
        onPermissionsResult(allGranted)
    }
    
    LaunchedEffect(Unit) {
        if (!permissionsRequested) {
            val hasAll = permissionHandler.hasAllPermissions()
            Log.d("RequestPermissions", "Initial permission check: $hasAll")
            
            if (!hasAll) {
                val requiredPermissions = permissionHandler.getRequiredPermissions()
                Log.d("RequestPermissions", "Requesting permissions: ${requiredPermissions.toList()}")
                permissionLauncher.launch(requiredPermissions)
            } else {
                onPermissionsResult(true)
            }
            permissionsRequested = true
        }
    }
}