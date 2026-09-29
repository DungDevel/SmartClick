package com.example.smartclick.app.tvconnection.lg

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

class LgWebOsDriverTest {

    @Test
    fun testCreateController() {
        val driver = LgWebOsDriver()
        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "LG TV",
            ipAddress = "192.168.1.100",
            macAddress = "00:11:22:33:44:55",
            protocol = TvProtocol.LG_WEBOS,
            isPaired = true,
            capabilities = setOf()
        )
        val credentials = Credentials(mapOf("client-key" to "abcdef"))
        val controller = driver.createController(device, credentials)

        assertNotNull(controller)
        assertNotNull(controller as LgWebOsController)
    }
}
