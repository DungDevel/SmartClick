package com.example.smartclick.app.tvconnection.dial

import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class DialControllerIntegrationTest {

    private lateinit var controller: DialController

    @Before
    fun setup() {
        controller = DialController()
    }

    @Test
    fun `connect succeeds with private LAN IP including a port`() = runTest {
        val device = device("192.168.1.150:56700")
        val result = controller.connect(device)
        assertTrue(result.isSuccess)
        assertTrue(controller.connectionState.first() is TvConnectionState.Connected)
    }

    @Test
    fun `connect rejects public IP address`() = runTest {
        val result = controller.connect(device("8.8.8.8"))
        assertTrue(result.isFailure)
        assertTrue(controller.connectionState.first() is TvConnectionState.Failed)
    }

    @Test
    fun `app listing and launch are explicitly unsupported`() = runTest {
        assertTrue(controller.getApps().exceptionOrNull() is UnsupportedOperationException)
        assertTrue(controller.launchApp("YouTube").exceptionOrNull() is UnsupportedOperationException)
    }

    private fun device(ip: String) = TvDevice(
        id = UUID.randomUUID().toString(),
        name = "DIAL TV",
        ipAddress = ip,
        macAddress = "",
        protocol = TvProtocol.DIAL_ONLY
    )
}
