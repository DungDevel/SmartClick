package com.example.smartclick.app.tvconnection.dial

import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class DialControllerIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private lateinit var controller: DialController

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        httpClient = OkHttpClient()
        controller = DialController(httpClient)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `connect succeeds with private LAN IP including dynamic MockWebServer port`() = runTest {
        val baseUrl = mockWebServer.url("/")
        val ipWithPort = "${baseUrl.host}:${baseUrl.port}"

        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "DIAL Mock TV",
            ipAddress = ipWithPort,
            macAddress = "",
            protocol = TvProtocol.DIAL_ONLY
        )

        val result = controller.connect(device)
        assertTrue(result.isSuccess)
        assertTrue(controller.connectionState.first() is TvConnectionState.Connected)
    }

    @Test
    fun `connect rejects public IP address`() = runTest {
        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "Public TV",
            ipAddress = "8.8.8.8",
            macAddress = "",
            protocol = TvProtocol.DIAL_ONLY
        )

        val result = controller.connect(device)
        assertTrue(result.isFailure)
        assertTrue(controller.connectionState.first() is TvConnectionState.Failed)
    }

    @Test
    fun `launchApp sends POST request and succeeds on 201 Created`() = runTest {
        val baseUrl = mockWebServer.url("/")
        val ipWithPort = "${baseUrl.host}:${baseUrl.port}"

        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "DIAL Mock TV",
            ipAddress = ipWithPort,
            macAddress = "",
            protocol = TvProtocol.DIAL_ONLY
        )

        controller.connect(device)

        mockWebServer.enqueue(MockResponse().setResponseCode(201))

        val result = controller.launchApp("YouTube")
        assertTrue(result.isSuccess)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/apps/YouTube", recordedRequest.path)
    }

    @Test
    fun `launchApp handles special characters in appId with proper URL encoding`() = runTest {
        val baseUrl = mockWebServer.url("/")
        val ipWithPort = "${baseUrl.host}:${baseUrl.port}"

        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "DIAL Mock TV",
            ipAddress = ipWithPort,
            macAddress = "",
            protocol = TvProtocol.DIAL_ONLY
        )

        controller.connect(device)

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = controller.launchApp("Netflix & Chill!")
        assertTrue(result.isSuccess)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/apps/Netflix+%26+Chill%21", recordedRequest.path)
    }

    @Test
    fun `launchApp returns failure on HTTP error status like 404 Not Found`() = runTest {
        val baseUrl = mockWebServer.url("/")
        val ipWithPort = "${baseUrl.host}:${baseUrl.port}"

        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "DIAL Mock TV",
            ipAddress = ipWithPort,
            macAddress = "",
            protocol = TvProtocol.DIAL_ONLY
        )

        controller.connect(device)

        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val result = controller.launchApp("InvalidApp")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("404") == true)
    }

    @Test
    fun `launchApp returns failure on HTTP error status like 500 Internal Server Error`() = runTest {
        val baseUrl = mockWebServer.url("/")
        val ipWithPort = "${baseUrl.host}:${baseUrl.port}"

        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "DIAL Mock TV",
            ipAddress = ipWithPort,
            macAddress = "",
            protocol = TvProtocol.DIAL_ONLY
        )

        controller.connect(device)

        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = controller.launchApp("YouTube")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun `getApps returns standard DIAL application list`() = runTest {
        val result = controller.getApps()
        assertTrue(result.isSuccess)
        val apps = result.getOrNull()
        assertNotNull(apps)
        assertTrue(apps?.any { it.id == "YouTube" } == true)
        assertTrue(apps?.any { it.id == "Netflix" } == true)
    }

    @Test
    fun `launchApp does not follow redirects and target server receives zero requests`() = runTest {
        val server1 = MockWebServer()
        server1.start()
        val server2 = MockWebServer()
        server2.start()

        try {
            val baseUrl1 = server1.url("/")
            val ipWithPort1 = "${baseUrl1.host}:${baseUrl1.port}"

            val device = TvDevice(
                id = UUID.randomUUID().toString(),
                name = "DIAL Mock TV",
                ipAddress = ipWithPort1,
                macAddress = "",
                protocol = TvProtocol.DIAL_ONLY
            )

            controller.connect(device)

            val redirectUrl = server2.url("/apps/YouTube").toString()
            server1.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", redirectUrl)
            )

            val result = controller.launchApp("YouTube")
            assertTrue(result.isFailure)

            assertEquals(1, server1.requestCount)
            assertEquals(0, server2.requestCount)
        } finally {
            server1.shutdown()
            server2.shutdown()
        }
    }
}
