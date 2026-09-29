package com.example.smartclick.app.tvconnection.discovery

import android.content.Context
import android.net.Network
import android.util.Log
import com.example.smartclick.app.domain.connection.IDiscoveryProbe
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FW-3: TCP Port probe — quét các cổng TCP đặc thù (vd: Roku 8060, Samsung 8001/8002) trên các IP thuộc subnet hiện tại.
 */
class PortProbe(
    override val protocol: TvProtocol,
    private val context: Context,
    private val ports: List<Int>,
    private val timeoutMs: Int = 1000
) : IDiscoveryProbe {

    override fun discover(network: Network): Flow<RawCandidate> = callbackFlow {
        val isClosed = AtomicBoolean(false)
        val ipsToScan = DiscoveryUtils.getSubnetIps(context)

        if (ipsToScan.isEmpty()) {
            Log.w(TAG, "No LAN IPs found for PortProbe")
            close()
            return@callbackFlow
        }

        launch(Dispatchers.IO) {
            // Scan each IP concurrently (with bounded scope via async)
            ipsToScan.map { ip ->
                async {
                    if (isClosed.get()) return@async

                    val openPorts = mutableSetOf<Int>()
                    for (port in ports) {
                        if (isClosed.get()) return@async
                        var socket: Socket? = null
                        try {
                            socket = network.socketFactory.createSocket()
                            socket.connect(InetSocketAddress(ip, port), timeoutMs)
                            // If connect succeeds, port is open
                            openPorts.add(port)
                        } catch (e: Exception) {
                            // Connection failed, timeout, or closed
                        } finally {
                            try { socket?.close() } catch (ignored: Exception) {}
                        }
                    }

                    if (openPorts.isNotEmpty()) {
                        trySend(
                            RawCandidate(
                                ipAddress = ip,
                                protocolHint = protocol,
                                openPorts = openPorts
                            )
                        )
                    }
                }
            }.awaitAll()

            if (!isClosed.get()) {
                close()
            }
        }

        awaitClose {
            isClosed.set(true)
        }
    }

    companion object {
        private const val TAG = "PortProbe"
    }
}
