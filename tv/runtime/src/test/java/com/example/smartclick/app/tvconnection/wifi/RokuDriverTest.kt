package com.example.smartclick.app.tvconnection.wifi

import com.example.smartclick.app.domain.connection.contract.AbstractDriverContractTest
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.connection.RawCandidate
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RokuDriverTest : AbstractDriverContractTest() {

    override fun createDriver(): IProtocolDriver {
        return RokuDriver(
            rokuControllerProvider = { mockk<RokuController>(relaxed = true) },
            httpClient = mockk<OkHttpClient>(relaxed = true)
        )
    }

    override fun createDummyDevice(): TvDevice = TvDevice(
        id = UUID.randomUUID().toString(),
        name = "Roku TV",
        ipAddress = "192.168.1.100",
        macAddress = "",
        protocol = TvProtocol.ROKU_ECP,
        isPaired = true,
        capabilities = RokuDriver.ROKU_CAPABILITIES
    )

    @Test
    fun `capabilities should include OPEN_SEARCH_UI`() {
        assertTrue(
            "Roku capability should contain OPEN_SEARCH_UI",
            RokuDriver.ROKU_CAPABILITIES.contains(TvCapability.OPEN_SEARCH_UI)
        )
    }

    @Test
    fun `fingerprint rejects non private destination before making a request`() = kotlinx.coroutines.test.runTest {
        val driver = RokuDriver(
            rokuControllerProvider = { mockk<RokuController>(relaxed = true) },
            httpClient = mockk<OkHttpClient>(relaxed = true)
        )
        assertTrue(driver.fingerprint(RawCandidate("203.0.113.20")) == null)
    }
}
