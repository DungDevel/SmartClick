package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.contract.AbstractDriverContractTest
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.tvconnection.cast.LocalMediaServer
import com.example.smartclick.app.tvconnection.cast.google.GoogleCastController
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.collect
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.UUID

class AndroidTvDriverTest : AbstractDriverContractTest() {

    @Before
    fun setup() {
        mockkObject(AndroidTvCertificateManager)
        every { AndroidTvCertificateManager.getOrGenerateClientKeyAndCertificate() } returns Pair(
            mockk<PrivateKey>(relaxed = true),
            mockk<X509Certificate>(relaxed = true)
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    override fun createDriver(): IProtocolDriver = AndroidTvDriver()

    override fun createDummyDevice(): TvDevice = TvDevice(
        id = UUID.randomUUID().toString(),
        name = "Android TV",
        ipAddress = "192.168.1.100",
        macAddress = "",
        protocol = TvProtocol.ANDROID_TV_V2,
        isPaired = true,
        capabilities = emptySet()
    )

    @Test
    fun testCastCapabilitiesPresent() {
        val driver = AndroidTvDriver()
        assertTrue(driver.capabilities.contains(TvCapability.CAST_VIDEO))
        assertTrue(driver.capabilities.contains(TvCapability.CAST_AUDIO))
        assertTrue(driver.capabilities.contains(TvCapability.CAST_IMAGE))
        assertTrue(driver.capabilities.contains(TvCapability.CAST_PLAYBACK))
        assertTrue(driver.capabilities.contains(TvCapability.CAST_SEEK))
    }

    @Test
    fun `pair flow handles connection failure without flow context violation`() = runTest {
        val driver = AndroidTvDriver()
        val device = createDummyDevice().copy(ipAddress = "127.0.0.1")
        val steps = mutableListOf<com.example.smartclick.app.domain.connection.PairingStep>()
        try {
            driver.pair(device).collect { step ->
                steps.add(step)
            }
        } catch (e: Exception) {
            println("Caught exception in test: ${e.javaClass.name}: ${e.message}")
            throw e
        }
        assertTrue(steps.any { it is com.example.smartclick.app.domain.connection.PairingStep.Failed })
    }

    @Test
    fun testCreateCastControllerWithoutMediaServerReturnsNull() {
        val driver = AndroidTvDriver(localMediaServer = null)
        val device = createDummyDevice()
        val castController = driver.createCastController(device, null)
        assertNull(castController)
    }

    @Test
    fun testCreateCastControllerWithMediaServerReturnsController() {
        val mockMediaServer = mockk<LocalMediaServer>(relaxed = true)
        val driver = AndroidTvDriver(localMediaServer = mockMediaServer)
        val device = createDummyDevice()
        val castController = driver.createCastController(device, null)
        assertNotNull(castController)
        assertTrue(castController is GoogleCastController)
    }

    @Test
    fun testCreateCastControllerWithCredentialsCertSha256() {
        val mockMediaServer = mockk<LocalMediaServer>(relaxed = true)
        val driver = AndroidTvDriver(localMediaServer = mockMediaServer)
        val device = createDummyDevice()
        val credentials = Credentials(mapOf("cert_sha256" to "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
        val castController = driver.createCastController(device, credentials)
        assertNotNull(castController)
        assertTrue(castController is GoogleCastController)
    }
}
