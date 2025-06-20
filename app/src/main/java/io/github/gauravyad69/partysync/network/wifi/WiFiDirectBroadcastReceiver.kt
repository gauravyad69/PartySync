package io.github.gauravyad69.partysync.network.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import io.github.gauravyad69.partysync.network.NetworkDevice

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val onPeersChanged: (List<NetworkDevice>) -> Unit,
    private val onConnectionChanged: (WifiP2pInfo?) -> Unit,
    private val onThisDeviceChanged: (WifiP2pDevice?) -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "WiFiDirectReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                Log.d(TAG, "WiFi P2P state changed: $state")
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Log.d(TAG, "Peers changed")
                manager.requestPeers(channel) { peers ->
                    val deviceList = peers.deviceList.map { device ->
                        NetworkDevice(
                            id = device.deviceAddress,
                            name = device.deviceName ?: "Unknown Device",
                            address = device.deviceAddress,
                            isHost = false
                        )
                    }
                    onPeersChanged(deviceList)
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                Log.d(TAG, "Connection changed")
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                
                if (networkInfo?.isConnected == true) {
                    manager.requestConnectionInfo(channel) { info ->
                        onConnectionChanged(info)
                    }
                } else {
                    onConnectionChanged(null)
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                Log.d(TAG, "This device changed")
                val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                onThisDeviceChanged(device)
            }
        }
    }
}