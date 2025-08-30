package io.github.gauravyad69.partysync.network.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import io.github.gauravyad69.partysync.network.NetworkDevice

class BluetoothDiscoveryReceiver(
    private val onDevicesFound: (List<NetworkDevice>) -> Unit
) : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BluetoothDiscovery"
    }
    
    private val discoveredDevices = mutableSetOf<NetworkDevice>()
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_FOUND -> {
                if (!checkBluetoothPermissions(context)) {
                    Log.w(TAG, "Bluetooth permissions not granted")
                    return
                }
                
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                
                device?.let { bluetoothDevice ->
                    try {
                        val deviceName = bluetoothDevice.name ?: "Unknown Device"
                        val deviceAddress = bluetoothDevice.address
                        
                        // Check if this device might be running PartySync
                        // In a real implementation, you might filter by service UUIDs or device name patterns
                        if (isPartySync Compatible(bluetoothDevice)) {
                            val networkDevice = NetworkDevice(
                                id = deviceAddress,
                                name = deviceName,
                                address = deviceAddress,
                                isHost = true // Assume discovered devices are potential hosts
                            )
                            
                            discoveredDevices.add(networkDevice)
                            Log.d(TAG, "Found PartySync-compatible device: $deviceName ($deviceAddress)")
                        }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Security exception accessing Bluetooth device info", e)
                    }
                }
            }
            
            BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                Log.d(TAG, "Bluetooth discovery started")
                discoveredDevices.clear()
            }
            
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                Log.d(TAG, "Bluetooth discovery finished, found ${discoveredDevices.size} devices")
                onDevicesFound(discoveredDevices.toList())
            }
        }
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun isPartySyncCompatible(device: BluetoothDevice): Boolean {
        return try {
            // Method 1: Check device name for PartySync indicators
            val deviceName = device.name?.lowercase() ?: ""
            if (deviceName.contains("partysync") || deviceName.contains("speaker")) {
                return true
            }
            
            // Method 2: Check if device supports SPP (Serial Port Profile)
            // Most Android devices support SPP, so this is a reasonable filter
            val deviceClass = device.bluetoothClass
            if (deviceClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.PHONE ||
                deviceClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.COMPUTER) {
                return true
            }
            
            // Method 3: Check bonded devices (previously paired)
            // If user has paired with this device before, it's likely compatible
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                return true
            }
            
            // For now, we'll be permissive and allow most devices
            // In production, you might want to be more restrictive
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot check device compatibility due to permissions", e)
            false
        }
    }
    
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        
        context.registerReceiver(this, filter)
        Log.d(TAG, "Bluetooth discovery receiver registered")
    }
    
    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
            Log.d(TAG, "Bluetooth discovery receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered", e)
        }
    }
    
    private fun checkBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 12 permissions
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }
}
