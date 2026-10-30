package com.example.smartclick.app.tvconnection.wifi

import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class RokuControllerAppsTest {
    private lateinit var server: MockWebServer
    private lateinit var controller: RokuController
    private val device = TvDevice(
        id = "roku-test", name = "Roku test", ipAddress = "127.0.0.1",
        macAddress = "", protocol = TvProtocol.ROKU_ECP
    )

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        controller = RokuController(OkHttpClient(), server.url("/").toString())
    }

    @After fun tearDown() = server.shutdown()

    private suspend fun connect() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<device-info/>"))
        assertTrue(controller.connect(device).isSuccess)
        assertEquals("/query/device-info", server.takeRequest().path)
    }

    @Test fun `getApps parses multiple numeric ids and names from XML`() = runTest {
        connect()
        server.enqueue(MockResponse().setBody("""
            <apps><app id="12">Netflix</app><app id="837">YouTube</app></apps>
        """.trimIndent()))

        val result = controller.getApps()

        assertEquals(listOf("12", "837"), result.getOrThrow().map { it.id })
        assertEquals(listOf("Netflix", "YouTube"), result.getOrThrow().map { it.name })
        assertEquals("/query/apps", server.takeRequest().path)
    }

    @Test fun `empty apps XML returns an empty list`() = runTest {
        connect()
        server.enqueue(MockResponse().setBody("<apps/>"))
        assertTrue(controller.getApps().getOrThrow().isEmpty())
    }

    @Test fun `malformed XML fails instead of looking like an empty list`() = runTest {
        connect()
        server.enqueue(MockResponse().setBody("<apps><app id=\"12\">Netflix"))
        assertTrue(controller.getApps().isFailure)
    }

    @Test fun `HTTP errors fail app listing`() = runTest {
        connect()
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(controller.getApps().isFailure)
    }

    @Test fun `request timeout fails app listing`() = runTest {
        connect()
        server.enqueue(MockResponse().setBody("<apps/>").setBodyDelay(2, TimeUnit.SECONDS))
        val result = controller.getApps()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SocketTimeoutException)
    }

    @Test fun `launch posts to the exact Roku app id endpoint`() = runTest {
        connect()
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(controller.launchApp("837").isSuccess)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/launch/837", request.path)
    }

    @Test fun `launch rejects non numeric app ids without sending a request`() = runTest {
        connect()
        assertFalse(controller.launchApp("https://example.invalid").isSuccess)
        assertEquals(0, server.requestCount - 1)
    }

    @Test fun `Roku advertises installed app capability`() {
        assertTrue(TvCapability.INSTALLED_APPS in RokuDriver.ROKU_CAPABILITIES)
    }

    @Test fun `not connected app listing is rejected`() = runTest {
        assertTrue(controller.connectionState.value is TvConnectionState.Idle)
        assertTrue(controller.getApps().isFailure)
    }
}
