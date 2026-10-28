package com.example.smartclick.app.tvconnection.samsung

import android.content.Context
import android.util.Base64
import app.cash.turbine.test
import com.example.smartclick.app.domain.connection.contract.AbstractDriverContractTest
import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.tvconnection.cast.LocalMediaServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.UUID
import javax.net.ssl.SSLContext

class SamsungTizenDriverTest : AbstractDriverContractTest() {

    private lateinit var context: Context
    private lateinit var localMediaServer: LocalMediaServer

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        localMediaServer = mockk(relaxed = true)
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "U21hcnRDbGljaw=="
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    override fun createDriver(): IProtocolDriver = SamsungTizenDriver(context, localMediaServer)

    override fun createDummyDevice(): TvDevice = TvDevice(
        id = UUID.randomUUID().toString(),
        name = "Samsung TV",
        ipAddress = "192.168.1.100",
        macAddress = "00:11:22:33:44:55",
        protocol = TvProtocol.SAMSUNG_TIZEN,
        isPaired = true,
        capabilities = setOf()
    )

    @Test
    fun testCreateController() {
        val driver = SamsungTizenDriver(context, localMediaServer)
        val device = createDummyDevice()
        val credentials = Credentials(mapOf("token" to "12345678"))
        val controller = driver.createController(device, credentials)

        assertNotNull(controller)
        assertNotNull(controller as SamsungTizenController)
    }

    @Test
    fun `createController fails closed when pinned client SSL context creation fails`() {
        val driver = SamsungTizenDriver(context, localMediaServer)
        mockkStatic(SSLContext::class)
        every { SSLContext.getInstance("TLSv1.2") } throws IllegalStateException("forced pinned client failure")

        val error = assertThrows(IllegalStateException::class.java) {
            driver.createController(
                createDummyDevice(),
                Credentials(mapOf("token" to "12345678", "cert_sha256" to "a".repeat(64)))
            )
        }

        assertEquals("Failed to create certificate-pinned Samsung Tizen client", error.message)
        assertEquals("forced pinned client failure", error.cause?.message)
    }

    @Test
    fun testCreateCastController() {
        val driver = SamsungTizenDriver(context, localMediaServer)
        val device = createDummyDevice()
        val credentials = Credentials(mapOf("token" to "12345678"))
        val castController = driver.createCastController(device, credentials)

        assertNotNull(castController)
    }

    @Test
    fun `pairing flow cancellation closes websocket`() = runTest {
        val driver = SamsungTizenDriver(context, localMediaServer)
        val mockClient = mockk<OkHttpClient>()
        val mockWs = mockk<WebSocket>(relaxed = true)
        every { mockClient.newWebSocket(any(), any()) } returns mockWs
        driver.okHttpClient = mockClient

        driver.pair(createDummyDevice()).test {
            org.junit.Assert.assertEquals(com.example.smartclick.app.domain.connection.PairingStep.ConfirmOnTv(30_000L), awaitItem())
            cancel()
        }

        verify { mockWs.close(1000, any()) }
    }
}

