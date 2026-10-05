package com.example.smartclick.app.tvconnection.samsung

import android.util.Base64
import com.example.smartclick.app.domain.connection.contract.AbstractDriverContractTest
import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

class SamsungTizenDriverTest : AbstractDriverContractTest() {

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "U21hcnRDbGljaw=="
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    override fun createDriver(): IProtocolDriver = SamsungTizenDriver()

    override fun createDummyDevice(): TvDevice = TvDevice(
        id = UUID.randomUUID().toString(),
        name = "Samsung TV",
        ipAddress = "192.168.1.100",
        macAddress = "00:11:22:33:44:55",
        protocol = TvProtocol.SAMSUNG_TIZEN,
        isPaired = true,
        capabilities = setOf()
    )

    @Test
    fun testCreateController() {
        val driver = SamsungTizenDriver()
        val device = createDummyDevice()
        val credentials = Credentials(mapOf("token" to "12345678"))
        val controller = driver.createController(device, credentials)

        assertNotNull(controller)
        assertNotNull(controller as SamsungTizenController)
    }
}
