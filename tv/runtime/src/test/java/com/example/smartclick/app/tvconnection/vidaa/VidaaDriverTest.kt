package com.example.smartclick.app.tvconnection.vidaa

import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class VidaaDriverTest {

    @Test
    fun `VidaaDriver pair emits failure and unsupported instruction`() = runTest {
        val driver = VidaaDriver()
        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "Hisense TV",
            ipAddress = "192.168.1.70",
            macAddress = "",
            protocol = TvProtocol.VIDAA_MQTT,
            isPaired = false
        )

        val steps = driver.pair(device).toList()
        assertTrue(steps.size >= 2)
        assertTrue(steps[0] is PairingStep.Instruction)
        assertTrue(steps[1] is PairingStep.Failed)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `VidaaDriver createController throws UnsupportedOperationException`() {
        val driver = VidaaDriver()
        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "Hisense TV",
            ipAddress = "192.168.1.70",
            macAddress = "",
            protocol = TvProtocol.VIDAA_MQTT,
            isPaired = false
        )
        driver.createController(device, null)
    }
}
