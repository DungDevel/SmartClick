package com.example.smartclick.app.tvconnection.utils

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.regex.Pattern

object NetworkSecurityUtils {

    // Basic IP or hostname syntax validation pattern
    private val HOST_PATTERN = Pattern.compile(
        "^([a-zA-Z0-9][-a-zA-Z0-9]{0,62})(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})*$|^(([0-9]{1,3}\\.){3}[0-9]{1,3})$|^([0-9a-fA-F:]+)$"
    )

    fun isValidIpOrHost(ipOrHost: String?): Boolean {
        if (ipOrHost.isNullOrBlank() || ipOrHost.length > 253) return false
        val sanitized = ipOrHost.trim()
        // Prevent path traversal or command injection characters
        if (sanitized.contains("..") || sanitized.contains("/") || sanitized.contains("\\") || sanitized.contains("?")) {
            return false
        }
        return HOST_PATTERN.matcher(sanitized).matches()
    }

    fun isPrivateLanAddress(ipOrHost: String?): Boolean {
        if (!isValidIpOrHost(ipOrHost)) return false
        try {
            val address = InetAddress.getByName(ipOrHost!!)
            return address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress || isCustomPrivateRange(address)
        } catch (e: UnknownHostException) {
            return false
        } catch (e: SecurityException) {
            return false
        }
    }

    fun isAllowedLanDestination(ipOrHost: String?): Boolean {
        return isPrivateLanAddress(ipOrHost)
    }

    private fun isCustomPrivateRange(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            // 10.0.0.0/8
            if (b0 == 10) return true
            // 172.16.0.0/12
            if (b0 == 172 && b1 in 16..31) return true
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) return true
            // 169.254.0.0/16 (Link-local)
            if (b0 == 169 && b1 == 254) return true
        }
        return false
    }
}
