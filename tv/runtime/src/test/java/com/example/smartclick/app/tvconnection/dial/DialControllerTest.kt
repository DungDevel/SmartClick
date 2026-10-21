package com.example.smartclick.app.tvconnection.dial

import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DialControllerTest {

    private val httpClient = OkHttpClient()
    private val controller = DialController(httpClient)

    @Test
    fun `connect rejects non-private LAN IP`() = runTest {
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
    fun `getApps returns standard DIAL applications`() = runTest {
        val result = controller.getApps()
        assertTrue(result.isSuccess)
        val apps = result.getOrNull()
        assertTrue(apps?.any { it.id == "YouTube" } == true)
        assertTrue(apps?.any { it.id == "Netflix" } == true)
    }

    @Test
    fun `launchApp fails when not connected`() = runTest {
        val result = controller.launchApp("YouTube")
        assertTrue(result.isFailure)
    }
}
