package com.example.smartclick.app.tvconnection.discovery

import android.content.Context
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.smartclick.app.domain.connection.IDiscoveryProbe
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FW-3: mDNS / DNS-SD probe qua `NsdManager` — phát [RawCandidate].
 *
 * `serviceType` ví dụ: `_androidtvremote2._tcp`.
 * Lưu ý: NsdManager quét tất cả giao diện, Android 12+ hỗ trợ NsdManager.resolve trên
 * Network nhất định, nhưng để tương thích với các bản Android cũ nên chỉ kiểm tra IP trả về
 * có nằm trong LAN hay không.
 */
class MdnsProbe(
    override val protocol: TvProtocol,
    private val context: Context,
    private val serviceType: String
) : IDiscoveryProbe {

    override fun discover(network: Network): Flow<RawCandidate> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsdManager == null) {
            Log.e(TAG, "NsdManager not available")
            close()
            return@callbackFlow
        }

        val isClosed = AtomicBoolean(false)

        val discoverListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS discovery started for $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (isClosed.get()) return
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")

                // NsdManager mandates resolving one at a time via listener callbacks.
                // We resolve it right away (may queue internally or fail if saturated on old OSes).
                try {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            if (isClosed.get()) return
                            val ip = resolvedInfo.host?.hostAddress

                            if (ip != null) {
                                // SSRF / WAN leak guard.
                                if (!DiscoveryUtils.isLanIp(ip)) {
                                    Log.w(TAG, "Blocked non-LAN mDNS response from $ip")
                                    return
                                }

                                val txtMap = mutableMapOf<String, String>()
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    resolvedInfo.attributes.forEach { (k, v) ->
                                        txtMap[k] = String(v, Charsets.UTF_8)
                                    }
                                }

                                trySend(
                                    RawCandidate(
                                        ipAddress = ip,
                                        protocolHint = protocol,
                                        serviceName = resolvedInfo.serviceName,
                                        openPorts = setOf(resolvedInfo.port),
                                        attributes = txtMap
                                    )
                                )
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to call resolveService", e)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "mDNS discovery stopped for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
                close(Exception("Start mDNS failed: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoverListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed calling discoverServices", e)
            close(e)
        }

        awaitClose {
            isClosed.set(true)
            try {
                nsdManager.stopServiceDiscovery(discoverListener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping mDNS discovery", e)
            }
        }
    }

    companion object {
        private const val TAG = "MdnsProbe"
    }
}
