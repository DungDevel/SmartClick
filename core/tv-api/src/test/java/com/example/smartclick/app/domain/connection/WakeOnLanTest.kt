package com.example.smartclick.app.domain.connection

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Vector

class WakeOnLanTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `wake with null or blank mac returns false`() = runTest {
        assertFalse(WakeOnLan.wake(null))
        assertFalse(WakeOnLan.wake(""))
        assertFalse(WakeOnLan.wake("   "))

        assertTrue(WakeOnLan.wakeDetailed(null) is WakeOnLan.WakeResult.InvalidMac)
    }

    @Test
    fun `wake with invalid mac format returns invalid mac result`() = runTest {
        val invalidMacs = listOf("invalid-mac", "00:11:22:33:44", "00:11:22:33:44:55:66", "zz:zz:zz:zz:zz:zz")
        for (mac in invalidMacs) {
            assertFalse(WakeOnLan.wake(mac))
            assertTrue(WakeOnLan.wakeDetailed(mac) is WakeOnLan.WakeResult.InvalidMac)
        }
    }

    @Test
    fun `getMacBytes parses colon and hyphen separated macs correctly`() {
        val bytesColon = WakeOnLan.getMacBytes("00:11:22:33:44:55")
        assertEquals(6, bytesColon.size)
        assertEquals(0x00.toByte(), bytesColon[0])
        assertEquals(0x11.toByte(), bytesColon[1])
        assertEquals(0x55.toByte(), bytesColon[5])

        val bytesHyphen = WakeOnLan.getMacBytes("00-11-22-33-44-55")
        assertEquals(6, bytesHyphen.size)
        assertEquals(0x00.toByte(), bytesHyphen[0])
        assertEquals(0x55.toByte(), bytesHyphen[5])

        val bytesPlain = WakeOnLan.getMacBytes("001122334455")
        assertEquals(6, bytesPlain.size)
    }

    @Test
    fun `buildMagicPacket produces correct 102 byte structure`() {
        val macBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        val packet = WakeOnLan.buildMagicPacket(macBytes)

        assertEquals(102, packet.size)
        // First 6 bytes should be 0xFF
        for (i in 0..5) {
            assertEquals(0xFF.toByte(), packet[i])
        }
        // Check repetitions of MAC
        for (rep in 0..15) {
            val offset = 6 + rep * 6
            for (j in 0..5) {
                assertEquals(macBytes[j], packet[offset + j])
            }
        }
    }

    @Test
    fun `resolveBroadcastAddresses with empty interfaces returns empty list`() {
        val emptyVector = Vector<NetworkInterface>()
        val addresses = WakeOnLan.resolveBroadcastAddresses(emptyVector.elements())
        assertTrue(addresses.isEmpty())
    }
}
