package com.example.smartclick.app.tvconnection.discovery

import android.content.Context
import android.net.Network
import android.util.Log
import com.example.smartclick.app.domain.connection.IDiscoveryProbe
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * FW-3: SSDP probe — gửi M-SEARCH và thu phản hồi, phát [RawCandidate].
 *
 * Mỗi instance nhắm một `searchTarget` (ST) cụ thể. `DiscoveryOrchestrator` tạo
 * instance thích hợp cho từng giao thức/brand trong `probePlan`.
 *
 * FIX-04 đã sửa:
 * - Gửi M-SEARCH 3 lần cách ~500 ms (thay vì 1 lần).
 * - MulticastLock acquire + release trong `awaitClose`.
 * - Kiểm tra LAN IP (SSRF guard).
 * - Không gọi HTTP GET device-info tại đây (việc đó thuộc FW-4 DeviceFingerprintEngine).
 * - Bind socket vào Wi-Fi network được truyền vào.
 */
class SsdpProbe(
    override val protocol: TvProtocol,
    private val context: Context,
    private val searchTarget: String,
    private val socketTimeoutMs: Int = SOCKET_TIMEOUT_MS
) : IDiscoveryProbe {

    override fun discover(network: Network): Flow<RawCandidate> = callbackFlow {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                soTimeout = socketTimeoutMs
                reuseAddress = true
            }
            try {
                network.bindSocket(socket)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to bind SSDP socket to Wi-Fi network", e)
            }

            val query = buildMSearchQuery(searchTarget)
            val sendData = query.toByteArray()
            val address = InetAddress.getByName(MULTICAST_ADDRESS)
            val packet = DatagramPacket(sendData, sendData.size, address, MULTICAST_PORT)

            // FIX-04: gửi M-SEARCH 3 lần cách ~500 ms.
            val s = socket
            launch(Dispatchers.IO) {
                try {
                    repeat(M_SEARCH_REPEATS) { attempt ->
                        if (s.isClosed) return@launch
                        s.send(packet)
                        Log.d(TAG, "Sent M-SEARCH ST=$searchTarget attempt=${attempt + 1}")
                        if (attempt < M_SEARCH_REPEATS - 1) delay(M_SEARCH_DELAY_MS)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending M-SEARCH", e)
                }
            }

            val buf = ByteArray(2048)
            launch(Dispatchers.IO) {
                val seenIps = mutableSetOf<String>()
                while (!isClosedForSend) {
                    try {
                        val recv = DatagramPacket(buf, buf.size)
                        s.receive(recv)

                        val text = String(recv.data, 0, recv.length)
                        val raw = parseSsdpResponse(text)
                        val packetIp = recv.address.hostAddress ?: continue

                        // SSRF guard: only accept private LAN IPs.
                        if (!DiscoveryUtils.isLanIp(packetIp)) {
                            Log.w(TAG, "Blocked non-LAN SSDP response from $packetIp")
                            continue
                        }

                        // Validate LOCATION host matches source IP (SSRF guard #2).
                        val locationHost = raw.location?.let { extractHostFromUrl(it) }
                        if (locationHost != null && locationHost != packetIp) {
                            Log.w(TAG, "LOCATION host $locationHost != source IP $packetIp — skipped")
                            continue
                        }

                        // Dedupe by IP within this probe run.
                        if (!seenIps.add(packetIp)) continue

                        trySend(
                            RawCandidate(
                                ipAddress = packetIp,
                                protocolHint = protocol,
                                location = raw.location,
                                server = raw.server,
                                usn = raw.usn,
                                attributes = buildMap {
                                    raw.st?.let { put("ST", it) }
                                }
                            )
                        )
                    } catch (_: SocketTimeoutException) {
                        Log.d(TAG, "SSDP timeout for ST=$searchTarget")
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Error receiving SSDP packet", e)
                        break
                    }
                }
                close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP probe failed for $searchTarget", e)
            close(e)
        }

        awaitClose {
            socket?.close()
        }
    }

    companion object {
        private const val TAG = "SsdpProbe"
        private const val MULTICAST_ADDRESS = "239.255.255.250"
        private const val MULTICAST_PORT = 1900
        private const val SOCKET_TIMEOUT_MS = 8_000
        private const val M_SEARCH_REPEATS = 3
        private const val M_SEARCH_DELAY_MS = 500L

        fun buildMSearchQuery(st: String): String =
            "M-SEARCH * HTTP/1.1\r\n" +
            "Host: $MULTICAST_ADDRESS:$MULTICAST_PORT\r\n" +
            "Man: \"ssdp:discover\"\r\n" +
            "ST: $st\r\n" +
            "MX: 3\r\n\r\n"

        /**
         * Parse SSDP response headers (case-insensitive keys).
         * Pure function — unit testable.
         */
        fun parseSsdpResponse(response: String): RawSsdpResponse {
            var location: String? = null
            var st: String? = null
            var server: String? = null
            var usn: String? = null

            for (line in response.lines()) {
                val trimmed = line.trim()
                val sep = trimmed.indexOf(':')
                if (sep >= 0) {
                    val key = trimmed.substring(0, sep).trim()
                    val value = trimmed.substring(sep + 1).trim()
                    when {
                        key.equals("LOCATION", ignoreCase = true) -> location = value
                        key.equals("ST", ignoreCase = true) -> st = value
                        key.equals("SERVER", ignoreCase = true) -> server = value
                        key.equals("USN", ignoreCase = true) -> usn = value
                    }
                }
            }
            return RawSsdpResponse(location, st, server, usn)
        }

        /** Extracts the host portion of an HTTP URL without needing java.net.URL for robustness. */
        internal fun extractHostFromUrl(url: String): String? {
            val afterScheme = url.removePrefix("http://").removePrefix("https://")
            val hostPort = afterScheme.substringBefore("/")
            return hostPort.substringBefore(":").ifBlank { null }
        }
    }
}

data class RawSsdpResponse(
    val location: String?,
    val st: String?,
    val server: String?,
    val usn: String?
)
