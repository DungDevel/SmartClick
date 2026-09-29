package com.example.smartclick.app.tvconnection.lg

import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LgWebOsControllerTest {

    private lateinit var mockOkHttpClient: OkHttpClient
    private lateinit var mockWebSocket: WebSocket
    private lateinit var controller: LgWebOsController
    private lateinit var testDevice: TvDevice

    @Before
    fun setup() {
        mockOkHttpClient = mockk()
        mockWebSocket = mockk(relaxed = true)

        testDevice = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "LG TV",
            ipAddress = "192.168.1.100",
            macAddress = "00:11:22:33:44:55",
            protocol = TvProtocol.LG_WEBOS,
            isPaired = true,
            capabilities = setOf()
        )

        controller = LgWebOsController("mock-client-key", mockOkHttpClient)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `test connect triggers websocket connection and registers`() = runTest {
        var capturedListener: WebSocketListener? = null
        every { mockOkHttpClient.newWebSocket(any(), any()) } answers {
            capturedListener = secondArg()
            mockWebSocket
        }

        val connectJob = launch {
            controller.connect(testDevice)
        }

        // Wait to allow newWebSocket to be called
        kotlinx.coroutines.delay(50)
        
        assertNotNull(capturedListener)

        // Trigger onOpen to simulate network connected
        capturedListener?.onOpen(mockWebSocket, mockk(relaxed = true))
        
        // Simulating TV sending registered response
        val registerResponse = """
            {
                "type": "registered",
                "id": "register_0",
                "payload": {
                    "client-key": "mock-client-key"
                }
            }
        """.trimIndent()
        
        capturedListener?.onMessage(mockWebSocket, registerResponse)
        
        connectJob.join()

        // Ensure state changed to Connected
        assertTrue(controller.connectionState.value is TvConnectionState.Connected)
    }
    
    @Test
    fun `test successful command sending maps correctly`() = runTest {
        // First mock a connected state
        var capturedListener: WebSocketListener? = null
        every { mockOkHttpClient.newWebSocket(any(), any()) } answers {
            capturedListener = secondArg()
            mockWebSocket
        }

        val connectJob = launch {
            controller.connect(testDevice)
        }
        kotlinx.coroutines.delay(50)
        capturedListener?.onOpen(mockWebSocket, mockk(relaxed = true))
        val registerResponse = """{"type": "registered", "payload": {"client-key": "mock-client-key"}}"""
        capturedListener?.onMessage(mockWebSocket, registerResponse)
        connectJob.join()

        // Now test sending command
        every { mockWebSocket.send(any<String>()) } returns true
        
        val result = controller.sendCommand(RemoteCommand.VolumeUp)
        
        assertTrue(result.isSuccess)
        verify { mockWebSocket.send(match<String> { it.contains("volumeUp") }) }
    }
}
