package com.example.smartclick.app.tvconnection.lg

import app.cash.turbine.test
import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.PairingFailure
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.UUID
import javax.net.ssl.SSLContext

/**
 * Task 7: Unit test cho LgWebOsDriver — ba nhóm kịch bản:
 *  - Nhận dạng thiết bị (fingerprint).
 *  - Ghép nối (pair): bước đầu ConfirmOnTv, TV chấp nhận -> Succeeded (kèm client-key + cert hash),
 *    TV từ chối -> Failed(RejectedOnTv), lỗi mạng -> Failed(Network).
 *  - Tạo controller / đường tái kết nối có ghim chứng chỉ (cert_sha256).
 *
 * `driver.pairingClient` được thay bằng OkHttpClient giả (test seam) để điều khiển WebSocketListener
 * mà không cần mạng thật.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LgWebOsDriverTest : com.example.smartclick.app.domain.connection.contract.AbstractDriverContractTest() {
    override fun createDriver(): com.example.smartclick.app.domain.connection.IProtocolDriver = LgWebOsDriver(mockk(relaxed = true))
    override fun createDummyDevice(): TvDevice = lgDevice()

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun lgDevice(ip: String = "192.168.1.100") = TvDevice(
        id = UUID.randomUUID().toString(),
        name = "LG TV",
        ipAddress = ip,
        macAddress = "00:11:22:33:44:55",
        protocol = TvProtocol.LG_WEBOS,
        isPaired = true,
        capabilities = setOf()
    )

    // ---------------------------------------------------------------------
    // createController (bao gồm đường tái kết nối có ghim chứng chỉ)
    // ---------------------------------------------------------------------

    @Test
    fun testCreateController() {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val controller = driver.createController(lgDevice(), Credentials(mapOf("client-key" to "abcdef")))

        assertNotNull(controller)
        assertNotNull(controller as LgWebOsController)
    }

    @Test
    fun `createController with cert_sha256 builds pinned controller`() {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val creds = Credentials(mapOf("client-key" to "abcdef", "cert_sha256" to "a".repeat(64)))
        val controller = driver.createController(lgDevice(), creds)

        assertNotNull(controller as LgWebOsController)
    }

    @Test
    fun `createController fails closed when pinned client SSL context creation fails`() {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        mockkStatic(SSLContext::class)
        every { SSLContext.getInstance("TLSv1.2") } throws IllegalStateException("forced pinned client failure")

        val error = assertThrows(IllegalStateException::class.java) {
            driver.createController(
                lgDevice(),
                Credentials(mapOf("client-key" to "abcdef", "cert_sha256" to "a".repeat(64)))
            )
        }

        assertEquals("Failed to create certificate-pinned LG WebOS client", error.message)
        assertEquals("forced pinned client failure", error.cause?.message)
    }

    @Test
    fun `createController with null credentials still returns controller`() {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val controller = driver.createController(lgDevice(), null)

        assertNotNull(controller as LgWebOsController)
    }

    // ---------------------------------------------------------------------
    // capabilities
    // ---------------------------------------------------------------------

    @Test
    fun `driver declares correct capabilities including BACKSPACE`() {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        assertTrue(driver.capabilities.contains(TvCapability.BACKSPACE))
        assertTrue(driver.capabilities.contains(TvCapability.VOLUME))
        assertTrue(driver.capabilities.contains(TvCapability.POINTER))
    }

    // ---------------------------------------------------------------------
    // fingerprint
    // ---------------------------------------------------------------------

    @Test
    fun `fingerprint returns null for a different protocol hint`() = runTest {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val candidate = RawCandidate(
            ipAddress = "192.168.1.50",
            protocolHint = TvProtocol.ROKU_ECP,
            openPorts = setOf(3001)
        )

        assertNull(driver.fingerprint(candidate))
    }

    @Test
    fun `fingerprint returns HIGH confidence when hint is LG WebOS`() = runTest {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val candidate = RawCandidate(
            ipAddress = "192.168.1.50",
            protocolHint = TvProtocol.LG_WEBOS,
            usn = "uuid-123",
            serviceName = "Living Room LG"
        )

        val fp = driver.fingerprint(candidate)

        assertNotNull(fp)
        assertEquals(TvProtocol.LG_WEBOS, fp!!.protocol)
        assertEquals(Fingerprint.Confidence.HIGH, fp.confidence)
        assertEquals("Living Room LG", fp.displayName)
        assertEquals("lg-uuid-123", fp.stableId)
    }

    @Test
    fun `fingerprint detects LG via open port 3001 with LOW confidence`() = runTest {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val candidate = RawCandidate(
            ipAddress = "192.168.1.50",
            protocolHint = TvProtocol.UNKNOWN,
            openPorts = setOf(3001)
        )

        val fp = driver.fingerprint(candidate)

        assertNotNull(fp)
        assertEquals(TvProtocol.LG_WEBOS, fp!!.protocol)
        assertEquals(Fingerprint.Confidence.LOW, fp.confidence)
        // serviceName null -> tên mặc định; không usn/location -> stableId theo IP
        assertEquals("LG WebOS TV", fp.displayName)
        assertEquals("lg-192.168.1.50", fp.stableId)
    }

    @Test
    fun `fingerprint returns null when no LG hint and no LG ports`() = runTest {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val candidate = RawCandidate(
            ipAddress = "192.168.1.50",
            protocolHint = TvProtocol.UNKNOWN,
            openPorts = setOf(8060) // cổng Roku ECP, không phải LG
        )

        assertNull(driver.fingerprint(candidate))
    }

    // ---------------------------------------------------------------------
    // pair()
    // ---------------------------------------------------------------------

    @Test
    fun `pair emits ConfirmOnTv as the first step`() = runTest {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val mockClient = mockk<OkHttpClient>()
        val mockWs = mockk<WebSocket>(relaxed = true)
        every { mockClient.newWebSocket(any(), any()) } returns mockWs
        driver.pairingClient = mockClient

        driver.pair(lgDevice()).test {
            assertEquals(PairingStep.ConfirmOnTv(30_000L), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pair succeeds and returns client-key when TV accepts`() = runTest {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val mockClient = mockk<OkHttpClient>()
        val mockWs = mockk<WebSocket>(relaxed = true)
        val listenerReady = CompletableDeferred<WebSocketListener>()
        every { mockClient.newWebSocket(any(), any()) } answers {
            listenerReady.complete(secondArg())
            mockWs
        }
        driver.pairingClient = mockClient

        driver.pair(lgDevice()).test {
            assertEquals(PairingStep.ConfirmOnTv(30_000L), awaitItem())

            val listener = listenerReady.await()
            listener.onOpen(mockWs, mockk(relaxed = true))
            listener.onMessage(
                mockWs,
                """{"type":"registered","id":"register_0","payload":{"client-key":"secret-key-xyz"}}"""
            )

            val step = awaitItem()
            assertTrue(step is PairingStep.Succeeded)
            assertEquals(
                "secret-key-xyz",
                (step as PairingStep.Succeeded).credentials?.protocolValues?.get("client-key")
            )
            awaitComplete()
        }

        // Đã gửi payload đăng ký (register) khi mở socket.
        verify { mockWs.send(match<String> { it.contains("register") }) }
    }

    @Test
    fun `pair captures server certificate SHA-256 on handshake`() = runTest {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val mockClient = mockk<OkHttpClient>()
        val mockWs = mockk<WebSocket>(relaxed = true)
        val listenerReady = CompletableDeferred<WebSocketListener>()
        every { mockClient.newWebSocket(any(), any()) } answers {
            listenerReady.complete(secondArg())
            mockWs
        }
        driver.pairingClient = mockClient

        val certBytes = byteArrayOf(10, 20, 30, 40, 50)
        val cert = mockk<Certificate>()
        every { cert.encoded } returns certBytes
        val handshake = mockk<Handshake>()
        every { handshake.peerCertificates } returns listOf(cert)
        val response = mockk<Response>(relaxed = true)
        every { response.handshake } returns handshake

        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(certBytes)
            .joinToString("") { "%02x".format(it) }

        driver.pair(lgDevice()).test {
            assertEquals(PairingStep.ConfirmOnTv(30_000L), awaitItem())

            val listener = listenerReady.await()
            listener.onOpen(mockWs, response)
            listener.onMessage(mockWs, """{"type":"registered","payload":{"client-key":"k"}}""")

            val step = awaitItem() as PairingStep.Succeeded
            assertEquals(expectedHash, step.credentials?.protocolValues?.get("cert_sha256"))
            awaitComplete()
        }
    }

    @Test
    fun `pair fails with RejectedOnTv when TV sends error`() = runTest {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val mockClient = mockk<OkHttpClient>()
        val mockWs = mockk<WebSocket>(relaxed = true)
        val listenerReady = CompletableDeferred<WebSocketListener>()
        every { mockClient.newWebSocket(any(), any()) } answers {
            listenerReady.complete(secondArg())
            mockWs
        }
        driver.pairingClient = mockClient

        driver.pair(lgDevice()).test {
            assertEquals(PairingStep.ConfirmOnTv(30_000L), awaitItem())

            val listener = listenerReady.await()
            listener.onMessage(mockWs, """{"type":"error","error":"403 denied"}""")

            val step = awaitItem()
            assertTrue(step is PairingStep.Failed)
            assertTrue((step as PairingStep.Failed).reason is PairingFailure.RejectedOnTv)
            awaitComplete()
        }
    }

    @Test
    fun `pair fails with Network when socket fails`() = runTest {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val mockClient = mockk<OkHttpClient>()
        val mockWs = mockk<WebSocket>(relaxed = true)
        val listenerReady = CompletableDeferred<WebSocketListener>()
        every { mockClient.newWebSocket(any(), any()) } answers {
            listenerReady.complete(secondArg())
            mockWs
        }
        driver.pairingClient = mockClient

        driver.pair(lgDevice()).test {
            assertEquals(PairingStep.ConfirmOnTv(30_000L), awaitItem())

            val listener = listenerReady.await()
            listener.onFailure(mockWs, java.io.IOException("conn refused"), null)

            val step = awaitItem()
            assertTrue(step is PairingStep.Failed)
            val reason = (step as PairingStep.Failed).reason
            assertTrue(reason is PairingFailure.Network)
            assertEquals("conn refused", (reason as PairingFailure.Network).message)
            awaitComplete()
        }
    }

    @Test
    fun `pairing flow cancellation closes websocket`() = runTest {
        val driver = LgWebOsDriver(mockk(relaxed = true))
        val mockClient = mockk<OkHttpClient>()
        val mockWs = mockk<WebSocket>(relaxed = true)
        every { mockClient.newWebSocket(any(), any()) } returns mockWs
        driver.pairingClient = mockClient

        driver.pair(lgDevice()).test {
            assertEquals(PairingStep.ConfirmOnTv(30_000L), awaitItem())
            cancel()
        }

        verify { mockWs.close(1000, any()) }
    }
}
