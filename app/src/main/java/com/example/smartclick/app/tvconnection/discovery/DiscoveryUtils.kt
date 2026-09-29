package com.example.smartclick.app.tvconnection.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

object DiscoveryUtils {
    /**
     * FW-3 / FIX-12: Kiểm tra IP LAN (ngăn SSRF khi thiết bị giả mạo trả về LOCATION ngoài mạng).
     */
    fun isLanIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val p0 = parts[0].toIntOrNull() ?: return false
        val p1 = parts[1].toIntOrNull() ?: return false
        return p0 == 10 ||
                (p0 == 192 && p1 == 168) ||
                (p0 == 172 && p1 in 16..31)
    }

    /**
     * Lấy các IP trong subnet hiện tại của thiết bị (chỉ tính Wi-Fi).
     */
    @Suppress("DEPRECATION")
    fun getSubnetIps(context: Context): List<String> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return emptyList()
        val dhcp = wifiManager.dhcpInfo ?: return emptyList()
        val ipAddress = dhcp.ipAddress
        val netmask = dhcp.netmask

        if (ipAddress == 0 || netmask == 0) return emptyList()

        val network = ipAddress and netmask
        val broadcast = network or netmask.inv()

        // Lấy danh sách IP (Int format is little-endian via WifiManager)
        val ips = mutableListOf<String>()
        // network + 1 up to broadcast - 1 is only valid if netmask is standard (e.g. /24).
        // Standard Android implementation for little-endian:
        try {
            val start = Integer.reverseBytes(network) + 1
            val end = Integer.reverseBytes(broadcast)
            for (i in start until end) {
                // Ignore our own IP
                if (Integer.reverseBytes(i) == ipAddress) continue

                val host = "${(i ushr 24) and 0xFF}.${(i ushr 16) and 0xFF}.${(i ushr 8) and 0xFF}.${i and 0xFF}"
                if (isLanIp(host)) {
                    ips.add(host)
                }
            }
        } catch (e: Exception) {
            Log.e("DiscoveryUtils", "Error computing subnet IPs", e)
        }
        return ips
    }
}
