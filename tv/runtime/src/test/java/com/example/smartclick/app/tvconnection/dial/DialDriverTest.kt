package com.example.smartclick.app.tvconnection.dial

import com.example.smartclick.app.domain.connection.contract.AbstractDriverContractTest
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DialDriverTest : AbstractDriverContractTest() {

    override fun createDriver(): IProtocolDriver {
        return DialDriver(
            dialControllerProvider = { mockk<DialController>(relaxed = true) },
            httpClient = mockk<OkHttpClient>(relaxed = true)
        )
    }

    override fun createDummyDevice(): TvDevice = TvDevice(
        id = UUID.randomUUID().toString(),
        name = "DIAL TV",
        ipAddress = "192.168.1.150",
        macAddress = "",
        protocol = TvProtocol.DIAL_ONLY,
        isPaired = true,
        capabilities = setOf(TvCapability.INSTALLED_APPS)
    )

    @Test
    fun `capabilities include INSTALLED_APPS`() {
        val driver = createDriver()
        assertTrue(driver.capabilities.contains(TvCapability.INSTALLED_APPS))
    }

    @Test
    fun `fingerprint does not follow redirects when fetching location and target server receives zero requests`() = runTest {
        val server1 = MockWebServer()
        server1.start()
        val server2 = MockWebServer()
        server2.start()

        try {
            val locationUrl = server1.url("/dd.xml").toString()
            val redirectUrl = server2.url("/target").toString()

            server1.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", redirectUrl)
            )

            val driver = DialDriver(
                dialControllerProvider = { mockk(relaxed = true) },
                httpClient = OkHttpClient()
            )

            val candidate = com.example.smartclick.app.domain.connection.RawCandidate(
                ipAddress = server1.url("/").host,
                protocolHint = TvProtocol.DIAL_ONLY,
                location = locationUrl
            )

            val fingerprint = driver.fingerprint(candidate)
            assertNotNull(fingerprint)

            assertEquals(1, server1.requestCount)
            assertEquals(0, server2.requestCount)
        } finally {
            server1.shutdown()
            server2.shutdown()
        }
    }
}
