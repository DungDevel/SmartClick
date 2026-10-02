package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.connection.DriverRegistry
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PairTvUseCaseTest {

    private val driverRegistry = mockk<DriverRegistry>()
    private val useCase = PairTvUseCase(driverRegistry)

    @Test
    fun `invoke returns Unknown failure if no driver found`() = runTest {
        every { driverRegistry.getDriver(TvProtocol.ROKU_ECP) } returns null

        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "Test TV",
            ipAddress = "192.168.1.100",
            macAddress = "00:11:22:33:44:55",
            isPaired = false,
            protocol = TvProtocol.ROKU_ECP
        )

        val result = useCase(device).toList()
        assertEquals(1, result.size)
        assertTrue(result[0] is PairingStep.Failed)
    }

    @Test
    fun `invoke delegates to driver pair`() = runTest {
        val driver = mockk<IProtocolDriver>()
        every { driverRegistry.getDriver(TvProtocol.ROKU_ECP) } returns driver

        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "Test TV",
            ipAddress = "192.168.1.100",
            macAddress = "00:11:22:33:44:55",
            isPaired = false,
            protocol = TvProtocol.ROKU_ECP
        )

        every { driver.pair(device) } returns flowOf(PairingStep.NotRequired)

        val result = useCase(device).toList()
        assertEquals(1, result.size)
        assertEquals(PairingStep.NotRequired, result[0])
    }

    @Test
    fun `providePin delegates to driver providePin`() = runTest {
        val driver = mockk<IProtocolDriver>(relaxed = true)
        every { driverRegistry.getDriver(TvProtocol.ROKU_ECP) } returns driver

        useCase.providePin(TvProtocol.ROKU_ECP, "1234")

        coVerify { driver.providePin("1234") }
    }
}
