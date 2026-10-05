package com.example.smartclick.app.tvconnection.lg

import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Task 7: Unit test cho LgWebOsController — tập trung tái kết nối (reconnect) và xử lý lỗi:
 *  - connect(): thành công, idempotent khi đã kết nối, TV trả error, lỗi mạng, socket đóng.
 *  - disconnect(): đóng socket và reset trạng thái.
 *  - sendCommand(): khi chưa kết nối, lệnh thường, Mute, lệnh không hỗ trợ.
 *  - getApps(): khi chưa kết nối và khi server phản hồi.
 */
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

    /**
     * Mô phỏng một phiên kết nối thành công: bắt WebSocketListener, phát onOpen rồi phản hồi
     * "registered" để controller chuyển sang Connected. Trả về listener để test tiếp tục điều khiển.
     */
    private suspend fun kotlinx.coroutines.CoroutineScope.establishConnection(): WebSocketListener {
        var capturedListener: WebSocketListener? = null
        every { mockOkHttpClient.newWebSocket(any(), any()) } answers {
            capturedListener = secondArg()
            mockWebSocket
        }

        val connectJob = launch { controller.connect(testDevice) }
        delay(50)
        val listener = capturedListener ?: error("WebSocket listener was not captured")
        listener.onOpen(mockWebSocket, mockk(relaxed = true))
        listener.onMessage(mockWebSocket, """{"type":"registered","payload":{"client-key":"mock-client-key"}}""")
        connectJob.join()
        return listener
    }

    // ---------------------------------------------------------------------
    // connect() & disconnect()
    // ---------------------------------------------------------------------

    @Test
    fun `connect transitions to Connected and remembers device`() = runTest {
        establishConnection()

        val state = controller.connectionState.value
        assertTrue(state is TvConnectionState.Connected)
        assertEquals(testDevice, (state as TvConnectionState.Connected).device)
        assertEquals(testDevice, controller.device)
    }

    @Test
    fun `connect is idempotent when already connected`() = runTest {
        establishConnection()

        // Gọi lại connect khi đã Connected: trả thành công ngay, không mở socket mới.
        val result = controller.connect(testDevice)

        assertTrue(result.isSuccess)
        verify(exactly = 1) { mockOkHttpClient.newWebSocket(any(), any()) }
    }

    @Test
    fun `connect fails and sets Failed state when TV returns error`() = runTest {
        var capturedListener: WebSocketListener? = null
        every { mockOkHttpClient.newWebSocket(any(), any()) } answers {
            capturedListener = secondArg()
            mockWebSocket
        }

        val deferred = async { controller.connect(testDevice) }
        delay(50)
        capturedListener?.onOpen(mockWebSocket, mockk(relaxed = true))
        capturedListener?.onMessage(mockWebSocket, """{"type":"error","error":"403 access denied"}""")

        val result = deferred.await()
        assertTrue(result.isFailure)
        assertTrue(controller.connectionState.value is TvConnectionState.Failed)
    }

    @Test
    fun `connect fails and sets Failed state on network failure`() = runTest {
        var capturedListener: WebSocketListener? = null
        every { mockOkHttpClient.newWebSocket(any(), any()) } answers {
            capturedListener = secondArg()
            mockWebSocket
        }

        val deferred = async { controller.connect(testDevice) }
        delay(50)
        capturedListener?.onFailure(mockWebSocket, java.io.IOException("timeout"), null)

        val result = deferred.await()
        assertTrue(result.isFailure)
        assertTrue(controller.connectionState.value is TvConnectionState.Failed)
    }

    @Test
    fun `onClosed resets connection state to Idle`() = runTest {
        val listener = establishConnection()

        listener.onClosed(mockWebSocket, 1000, "server closed")

        assertEquals(TvConnectionState.Idle, controller.connectionState.value)
    }

    @Test
    fun `disconnect closes socket and resets state`() = runTest {
        establishConnection()

        controller.disconnect()

        verify { mockWebSocket.close(1000, any()) }
        assertEquals(TvConnectionState.Idle, controller.connectionState.value)
        assertNull(controller.device)
    }

    // ---------------------------------------------------------------------
    // sendCommand()
    // ---------------------------------------------------------------------

    @Test
    fun `sendCommand fails when not connected`() = runTest {
        val result = controller.sendCommand(RemoteCommand.VolumeUp)

        assertTrue(result.isFailure)
        assertEquals("Not connected", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sendCommand maps a standard command successfully`() = runTest {
        establishConnection()
        every { mockWebSocket.send(any<String>()) } returns true

        val result = controller.sendCommand(RemoteCommand.VolumeDown)

        assertTrue(result.isSuccess)
        verify { mockWebSocket.send(match<String> { it.contains("volumeDown") }) }
    }

    @Test
    fun `sendCommand sends Mute payload successfully`() = runTest {
        establishConnection()
        every { mockWebSocket.send(any<String>()) } returns true

        val result = controller.sendCommand(RemoteCommand.Mute)

        assertTrue(result.isSuccess)
        verify { mockWebSocket.send(match<String> { it.contains("audio/setMute") && it.contains("true") }) }
    }

    @Test
    fun `sendCommand fails for an unsupported command`() = runTest {
        establishConnection()

        val result = controller.sendCommand(RemoteCommand.Menu)

        assertTrue(result.isFailure)
        assertEquals("Unsupported command on LG WebOS", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sendCommand DPad fails when pointer socket is not ready`() = runTest {
        // establishConnection không phát "response" chứa socketPath, nên pointerSocket vẫn null.
        establishConnection()

        val result = controller.sendCommand(RemoteCommand.DPadUp)

        assertTrue(result.isFailure)
        assertEquals("Pointer socket not ready", result.exceptionOrNull()?.message)
    }

    // ---------------------------------------------------------------------
    // getApps()
    // ---------------------------------------------------------------------

    @Test
    fun `getApps fails when not connected`() = runTest {
        val result = controller.getApps()

        assertTrue(result.isFailure)
    }

    @Test
    fun `getApps resolves app list when server responds`() = runTest {
        val listener = establishConnection()
        every { mockWebSocket.send(any<String>()) } returns true

        val deferred = async { controller.getApps() }
        delay(50)

        // register/pointer dùng id cố định ("register_0"/"req_pointer"), nên getApps đầu tiên có id = "1".
        val appsJson =
            """{"type":"response","id":"1","payload":{"apps":[{"id":"netflix","title":"Netflix","icon":"http://x"}]}}"""
        listener.onMessage(mockWebSocket, appsJson)

        val result = deferred.await()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("Netflix", result.getOrNull()?.first()?.name)
    }
}
