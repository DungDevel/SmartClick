package com.example.smartclick.app.domain.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Enumeration
import android.util.Log

object WakeOnLan {
    private const val TAG = "WakeOnLan"

    sealed class WakeResult {
        data class Success(val broadcastAddress: String) : WakeResult()
        data class InvalidMac(val reason: String) : WakeResult()
        data class NetworkError(val error: String) : WakeResult()
    }

    suspend fun wake(macStr: String?): Boolean = withContext(Dispatchers.IO) {
        when (wakeDetailed(macStr)) {
            is WakeResult.Success -> true
            else -> false
        }
    }

    suspend fun wakeDetailed(macStr: String?): WakeResult = withContext(Dispatchers.IO) {
        if (macStr.isNullOrBlank()) {
            Log.e(TAG, "MAC address is empty or blank")
            return@withContext WakeResult.InvalidMac("MAC address is empty or blank")
        }

        val macBytes = try {
            getMacBytes(macStr)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address format: ${e.message}")
            return@withContext WakeResult.InvalidMac(e.message ?: "Invalid MAC format")
        }

        try {
            val magicPacket = buildMagicPacket(macBytes)
            val broadcastAddresses = resolveBroadcastAddresses()

            if (broadcastAddresses.isEmpty()) {
                broadcastAddresses.add(InetAddress.getByName("255.255.255.255"))
            }

            var sentAny = false
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (address in broadcastAddresses) {
                    try {
                        val packet = DatagramPacket(magicPacket, magicPacket.size, address, 9)
                        socket.send(packet)
                        Log.d(TAG, "Magic packet sent to broadcast ${address.hostAddress}")
                        sentAny = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send to broadcast ${address.hostAddress}", e)
                    }
                }
            }

            if (sentAny) {
                WakeResult.Success(broadcastAddresses.firstOrNull()?.hostAddress ?: "255.255.255.255")
            } else {
                WakeResult.NetworkError("Failed to send packet to any broadcast address")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Wake-on-LAN execution", e)
            WakeResult.NetworkError(e.message ?: "Unknown network error")
        }
    }

    fun buildMagicPacket(macBytes: ByteArray): ByteArray {
        require(macBytes.size == 6) { "MAC address must be 6 bytes" }
        val bytes = ByteArray(6 + 16 * 6)
        for (i in 0..5) {
            bytes[i] = 0xff.toByte()
        }
        var i = 6
        while (i < bytes.size) {
            System.arraycopy(macBytes, 0, bytes, i, 6)
            i += 6
        }
        return bytes
    }

    fun getMacBytes(macStr: String): ByteArray {
        val sanitized = macStr.trim()
        val separators = listOf(':', '-')
        var hex: List<String> = emptyList()
        for (sep in separators) {
            if (sanitized.contains(sep)) {
                hex = sanitized.split(sep)
                break
            }
        }
        if (hex.isEmpty() && sanitized.length == 12) {
            hex = sanitized.chunked(2)
        }
        require(hex.size == 6) { "Invalid MAC address length or format: $macStr" }
        val bytes = ByteArray(6)
        for (i in 0..5) {
            val octet = hex[i]
            require(octet.length == 2) { "Invalid octet length in MAC: $octet" }
            bytes[i] = octet.toInt(16).toByte()
        }
        return bytes
    }

    fun resolveBroadcastAddresses(
        networkInterfaces: Enumeration<NetworkInterface>? = try { NetworkInterface.getNetworkInterfaces() } catch (e: Exception) { null }
    ): MutableList<InetAddress> {
        val addresses = mutableSetOf<InetAddress>()
        if (networkInterfaces == null) return mutableListOf()

        try {
            val interfaces = networkInterfaces.toList()
            for (intf in interfaces) {
                try {
                    if (!intf.isUp || intf.isLoopback) continue
                    for (interfaceAddress in intf.interfaceAddresses) {
                        val broadcast = interfaceAddress.broadcast
                        if (broadcast != null) {
                            addresses.add(broadcast)
                        } else {
                            val inetAddr = interfaceAddress.address
                            val prefixLength = interfaceAddress.networkPrefixLength
                            if (inetAddr is java.net.Inet4Address && prefixLength > 0 && prefixLength < 32) {
                                try {
                                    val computed = computeBroadcast(inetAddr, prefixLength.toInt())
                                    if (computed != null) {
                                        addresses.add(computed)
                                    }
                                } catch (e: Exception) {
                                    // ignore computation error
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore individual interface exception
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving network interfaces", e)
        }
        return addresses.toMutableList()
    }

    private fun computeBroadcast(inetAddr: java.net.Inet4Address, prefixLength: Int): InetAddress? {
        val addrBytes = inetAddr.address
        if (addrBytes.size != 4) return null
        var addrInt = 0
        for (b in addrBytes) {
            addrInt = (addrInt shl 8) or (b.toInt() and 0xFF)
        }
        val mask = if (prefixLength >= 32) 0xFFFFFFFF.toInt() else (0xFFFFFFFF.toInt() shl (32 - prefixLength))
        val broadcastInt = (addrInt and mask) or mask.inv()
        val broadcastBytes = ByteArray(4)
        for (i in 3 downTo 0) {
            broadcastBytes[i] = (broadcastInt shr (8 * (3 - i)) and 0xFF).toByte()
        }
        return InetAddress.getByAddress(broadcastBytes)
    }
}
