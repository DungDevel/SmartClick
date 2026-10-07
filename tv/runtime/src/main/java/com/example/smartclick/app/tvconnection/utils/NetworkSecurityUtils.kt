package com.example.smartclick.app.tvconnection.utils

import java.net.InetAddress
import java.net.UnknownHostException

object NetworkSecurityUtils {
    fun isPrivateLanAddress(ipOrHost: String): Boolean {
        try {
            val address = InetAddress.getByName(ipOrHost)
            return address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress
        } catch (e: UnknownHostException) {
            return false
        }
    }
}
