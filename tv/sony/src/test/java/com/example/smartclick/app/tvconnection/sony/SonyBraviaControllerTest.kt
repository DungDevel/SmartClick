package com.example.smartclick.app.tvconnection.sony

import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

class SonyBraviaControllerTest {
    private lateinit var serverSocket: ServerSocket
    private var port: Int = 0
    private val device = TvDevice(
        id = "sony-test", name = "Sony Test", ipAddress = "127.0.0.1",
        macAddress = "", protocol = TvProtocol.SONY_BRAVIA
    )

    @Before
    fun setUp() {
        serverSocket = ServerSocket(0)
        port = serverSocket.localPort
    }

    @After
    fun tearDown() {
        if (!serverSocket.isClosed) serverSocket.close()
    }

    @Test
    fun `SonyKeyMapper maps power and volume correctly`() {
        val powerSoap = SonyKeyMapper.mapCommandToIrcc(RemoteCommand.Power)
        assertNotNull(powerSoap)
        assertTrue(powerSoap!!.contains("X_SendIRCC"))

        val volUpSoap = SonyKeyMapper.mapCommandToIrcc(RemoteCommand.VolumeUp)
        assertNotNull(volUpSoap)
        assertTrue(volUpSoap!!.contains("AAAAAQAAAAEAAAASAw=="))
    }

    @Test
    fun `connect succeeds when server socket is open`() = runTest {
        val controller = SonyBraviaController("127.0.0.1", null, OkHttpClient(), probePort = port)
        val result = controller.connect(device)
        assertTrue(result.isSuccess)
        assertTrue(controller.connectionState.value is TvConnectionState.Connected)
        assertEquals(device, controller.device)
    }

    @Test
    fun `connect fails when port is closed`() = runTest {
        val closedPort = serverSocket.localPort
        serverSocket.close()

        val controller = SonyBraviaController("127.0.0.1", null, OkHttpClient(), probePort = closedPort)
        val result = controller.connect(device)
        assertFalse(result.isSuccess)
        assertTrue(controller.connectionState.value is TvConnectionState.Failed)
        assertFalse(controller.connectionState.value is TvConnectionState.Connected)
        assertNull(controller.device)
    }

    @Test
    fun `disconnect sets state to Idle and device to null`() = runTest {
        val controller = SonyBraviaController("127.0.0.1", null, OkHttpClient(), probePort = port)
        controller.connect(device)
        controller.disconnect()
        assertTrue(controller.connectionState.value is TvConnectionState.Idle)
        assertNull(controller.device)
    }
}
