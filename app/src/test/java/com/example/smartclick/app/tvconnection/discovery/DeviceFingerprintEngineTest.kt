package com.example.smartclick.app.tvconnection.discovery

import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceFingerprintEngineTest {

    private lateinit var driverRegistry: DriverRegistry
    private lateinit var engine: DeviceFingerprintEngine

    @Before
    fun setup() {
        driverRegistry = mockk(relaxed = true)
        engine = DeviceFingerprintEngine(driverRegistry)
    }

    @Test
    fun `identify returns TvDevice with UNKNOWN protocol when no drivers exist`() = runTest {
        val candidate = RawCandidate("192.168.1.10", TvProtocol.ROKU_ECP)
        every { driverRegistry.getAllDrivers() } returns emptySet()

        val device = engine.identify(candidate)
        assertNotNull(device)
        assertEquals(TvProtocol.UNKNOWN, device?.protocol)
        assertEquals("unknown_192.168.1.10", device?.id)
        assertTrue(device!!.name.contains("192.168.1.10"))
    }

    @Test
    fun `identify returns TvDevice with UNKNOWN protocol when all drivers return null`() = runTest {
        val candidate = RawCandidate("192.168.1.10", TvProtocol.ROKU_ECP)
        val mockDriver1 = mockk<IProtocolDriver>(relaxed = true)
        val mockDriver2 = mockk<IProtocolDriver>(relaxed = true)

        every { driverRegistry.getAllDrivers() } returns setOf(mockDriver1, mockDriver2)
        coEvery { mockDriver1.fingerprint(candidate) } returns null
        coEvery { mockDriver2.fingerprint(candidate) } returns null

        val device = engine.identify(candidate)
        assertNotNull(device)
        assertEquals(TvProtocol.UNKNOWN, device?.protocol)
    }

    @Test
    fun `identify runs parallel and selects highest confidence fingerprint`() = runTest {
        val candidate = RawCandidate("192.168.1.10", TvProtocol.UNKNOWN)

        val driverLow = mockk<IProtocolDriver>(relaxed = true)
        val driverHigh = mockk<IProtocolDriver>(relaxed = true)

        val fpLow = Fingerprint(TvProtocol.LG_NETCAST, "id-low", "Low TV", Fingerprint.Confidence.LOW)
        val fpHigh = Fingerprint(TvProtocol.SAMSUNG_TIZEN, "id-high", "High TV", Fingerprint.Confidence.HIGH)

        every { driverRegistry.getAllDrivers() } returns setOf(driverLow, driverHigh)
        coEvery { driverLow.fingerprint(candidate) } coAnswers {
            delay(100)
            fpLow
        }
        coEvery { driverHigh.fingerprint(candidate) } coAnswers {
            delay(50)
            fpHigh
        }
        every { driverHigh.capabilities } returns setOf(TvCapability.VOLUME)

        val device = engine.identify(candidate, "Samsung")
        requireNotNull(device)

        assertEquals("id-high", device.id)
        assertEquals("High TV", device.name)
        assertEquals(TvProtocol.SAMSUNG_TIZEN, device.protocol)
        assertEquals("Samsung", device.brand)
    }

    @Test
    fun `identify ignores timed out drivers but uses successful ones`() = runTest {
        val candidate = RawCandidate("192.168.1.10", TvProtocol.UNKNOWN)

        val driverSlow = mockk<IProtocolDriver>(relaxed = true)
        val driverFast = mockk<IProtocolDriver>(relaxed = true)

        val fpFast = Fingerprint(TvProtocol.ROKU_ECP, "id-fast", "Fast TV", Fingerprint.Confidence.MEDIUM)

        every { driverRegistry.getAllDrivers() } returns setOf(driverSlow, driverFast)
        coEvery { driverSlow.fingerprint(candidate) } coAnswers {
            delay(5000) // This should timeout (limit is 2000)
            Fingerprint(TvProtocol.LG_WEBOS, "id-slow", "Slow", Fingerprint.Confidence.HIGH)
        }
        coEvery { driverFast.fingerprint(candidate) } coAnswers {
            delay(10)
            fpFast
        }
        every { driverFast.capabilities } returns setOf(TvCapability.VOLUME)

        val device = engine.identify(candidate)
        requireNotNull(device)

        assertEquals("id-fast", device.id)
    }

    @Test
    fun `identify prioritizes hint when confidence is equal`() = runTest {
        // M-SEARCH có thể gửi ST=roku, trả về protocolHint=ROKU_ECP
        val candidate = RawCandidate("192.168.1.10", protocolHint = TvProtocol.ROKU_ECP)

        val driver1 = mockk<IProtocolDriver>(relaxed = true)
        val driver2 = mockk<IProtocolDriver>(relaxed = true)

        val fp1 = Fingerprint(TvProtocol.SAMSUNG_TIZEN, "id-1", "Samsung", Fingerprint.Confidence.MEDIUM)
        val fp2 = Fingerprint(TvProtocol.ROKU_ECP, "id-2", "Roku", Fingerprint.Confidence.MEDIUM)

        every { driver1.protocol } returns TvProtocol.SAMSUNG_TIZEN
        every { driver2.protocol } returns TvProtocol.ROKU_ECP

        every { driverRegistry.getAllDrivers() } returns setOf(driver1, driver2)
        coEvery { driver1.fingerprint(candidate) } returns fp1
        coEvery { driver2.fingerprint(candidate) } returns fp2

        val device = engine.identify(candidate)
        requireNotNull(device)

        // Phải chọn Roku vì protocolHint là Roku (khi confidence bằng nhau)
        assertEquals("id-2", device.id)
        assertEquals(TvProtocol.ROKU_ECP, device.protocol)
    }
}
