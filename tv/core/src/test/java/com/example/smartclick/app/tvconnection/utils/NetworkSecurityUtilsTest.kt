package com.example.smartclick.app.tvconnection.utils

import org.junit.Assert.*
import org.junit.Test

class NetworkSecurityUtilsTest {

    @Test
    fun `isValidIpOrHost validates correct ips and hostnames`() {
        assertTrue(NetworkSecurityUtils.isValidIpOrHost("192.168.1.1"))
        assertTrue(NetworkSecurityUtils.isValidIpOrHost("10.0.0.5"))
        assertTrue(NetworkSecurityUtils.isValidIpOrHost("172.16.5.4"))
        assertTrue(NetworkSecurityUtils.isValidIpOrHost("localhost"))
        assertTrue(NetworkSecurityUtils.isValidIpOrHost("tv-device.local"))
        assertTrue(NetworkSecurityUtils.isValidIpOrHost("::1"))
    }

    @Test
    fun `isValidIpOrHost rejects malformed or malicious inputs`() {
        assertFalse(NetworkSecurityUtils.isValidIpOrHost(null))
        assertFalse(NetworkSecurityUtils.isValidIpOrHost(""))
        assertFalse(NetworkSecurityUtils.isValidIpOrHost("   "))
        assertFalse(NetworkSecurityUtils.isValidIpOrHost("192.168.1.1/path"))
        assertFalse(NetworkSecurityUtils.isValidIpOrHost("192.168..1"))
        assertFalse(NetworkSecurityUtils.isValidIpOrHost("a..b"))
        assertFalse(NetworkSecurityUtils.isValidIpOrHost("http://192.168.1.1"))
    }

    @Test
    fun `isPrivateLanAddress identifies private and local network addresses`() {
        assertTrue(NetworkSecurityUtils.isPrivateLanAddress("192.168.1.50"))
        assertTrue(NetworkSecurityUtils.isPrivateLanAddress("10.0.0.1"))
        assertTrue(NetworkSecurityUtils.isPrivateLanAddress("172.16.0.5"))
        assertTrue(NetworkSecurityUtils.isPrivateLanAddress("127.0.0.1"))
        assertTrue(NetworkSecurityUtils.isPrivateLanAddress("169.254.1.1"))
    }

    @Test
    fun `isPrivateLanAddress rejects public addresses or invalid inputs`() {
        assertFalse(NetworkSecurityUtils.isPrivateLanAddress("8.8.8.8"))
        assertFalse(NetworkSecurityUtils.isPrivateLanAddress("1.1.1.1"))
        assertFalse(NetworkSecurityUtils.isPrivateLanAddress("invalid-ip"))
        assertFalse(NetworkSecurityUtils.isPrivateLanAddress(null))
    }
}
