package com.example.smartclick.app.tvconnection.dial

import com.example.smartclick.app.domain.connection.contract.AbstractDriverContractTest
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DialDriverTest : AbstractDriverContractTest() {

    override fun createDriver(): IProtocolDriver {
        return DialDriver(
            dialControllerProvider = { mockk<DialController>(relaxed = true) },
            httpClient = mockk<OkHttpClient>(relaxed = true)
        )
    }

    override fun createDummyDevice(): TvDevice = TvDevice(
        id = UUID.randomUUID().toString(),
        name = "DIAL TV",
        ipAddress = "192.168.1.150",
        macAddress = "",
        protocol = TvProtocol.DIAL_ONLY,
        isPaired = true,
        capabilities = setOf(TvCapability.INSTALLED_APPS)
    )

    @Test
    fun `capabilities include INSTALLED_APPS`() {
        val driver = createDriver()
        assertTrue(driver.capabilities.contains(TvCapability.INSTALLED_APPS))
    }
}
