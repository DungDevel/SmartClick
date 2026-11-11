package com.example.smartclick.app.tvconnection.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import android.util.Log
import com.example.smartclick.app.domain.connection.IWifiNetworkProvider
import com.example.smartclick.app.domain.connection.NetworkState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes WiFi connectivity changes using ConnectivityManager.NetworkCallback.
 * Emits [NetworkState] updates as a Flow so ViewModels can react to WiFi on/off/drop.
 *
 * Key behaviors:
 *  - WifiConnected: device is on a WiFi network (LAN scan can proceed)
 *  - ConnectedNoWifi: device has internet but via cellular/ethernet (LAN scan blocked)
 *  - Disconnected: no connectivity at all
 */
@Singleton
class
WifiNetworkProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : IWifiNetworkProvider {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Returns a cold Flow that emits the current [NetworkState] immediately,
     * then emits updates whenever connectivity changes.
     *
     * The flow automatically unregisters the callback when the collector is cancelled.
     */
    override val networkState: Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(evaluateCurrentState())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(evaluateCurrentState())
            }

            override fun onLost(network: Network) {
                trySend(evaluateCurrentState())
            }
        }

        // Request to monitor all network types
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state immediately
        trySend(evaluateCurrentState())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
            Log.d(TAG, "Network callback unregistered")
        }
    }.distinctUntilChanged()

    /**
     * Snapshot check — evaluates the current network state synchronously.
     * Useful for one-shot checks before starting a scan.
     */
    override fun getCurrentState(): NetworkState = evaluateCurrentState()

    override fun getActiveWifiNetwork(): Network? {
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return activeNetwork
        }
        return null
    }

    private fun evaluateCurrentState(): NetworkState {
        val activeNetwork = connectivityManager.activeNetwork
            ?: return NetworkState.Disconnected

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkState.Disconnected

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        return when {
            hasWifi -> {
                val ssid = extractSsid(capabilities)
                NetworkState.WifiConnected(ssid)
            }
            hasInternet -> NetworkState.ConnectedNoWifi
            else -> NetworkState.Disconnected
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSsid(capabilities: NetworkCapabilities): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // On Android 10+, SSID is in TransportInfo as WifiInfo
                val transportInfo = capabilities.transportInfo
                if (transportInfo is WifiInfo) {
                    val ssid = transportInfo.ssid
                    // WifiInfo returns "<unknown ssid>" when location permission is missing
                    if (ssid != null && ssid != "<unknown ssid>") {
                        ssid.removeSurrounding("\"")
                    } else null
                } else null
            } else {
                // Pre-Android 10: use deprecated WifiManager
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val info = wifiManager?.connectionInfo
                val ssid = info?.ssid
                if (ssid != null && ssid != "<unknown ssid>") {
                    ssid.removeSurrounding("\"")
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract SSID", e)
            null
        }
    }

    companion object {
        private const val TAG = "WifiNetworkProvider"
    }
}


