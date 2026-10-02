package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.connection.IDiscoveryOrchestrator
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class ScanTvDevicesUseCaseTest {

    private val orchestrator = mockk<IDiscoveryOrchestrator>()
    private val useCase = ScanTvDevicesUseCase(orchestrator)

    @Test
    fun `invoke calls orchestrator scanForDevices`() = runTest {
        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "Test TV",
            ipAddress = "192.168.1.100",
            macAddress = "00:11:22:33:44:55",
            isPaired = false,
            protocol = TvProtocol.ROKU_ECP,
            brand = "Roku"
        )
        every { orchestrator.scanForDevices("Roku") } returns flowOf(listOf(device))

        val result = useCase("Roku").toList()

        assertEquals(1, result.size)
        assertEquals(1, result[0].size)
        assertEquals("192.168.1.100", result[0][0].ipAddress)
    }

    @Test
    fun `identifyManualIp calls orchestrator identifyManualIp`() = runTest {
        val device = TvDevice(
            id = UUID.randomUUID().toString(),
            name = "Test TV",
            ipAddress = "192.168.1.200",
            macAddress = "00:11:22:33:44:55",
            isPaired = false,
            protocol = TvProtocol.ROKU_ECP,
            brand = "Roku"
        )
        coEvery { orchestrator.identifyManualIp("192.168.1.200") } returns device

        val result = useCase.identifyManualIp("192.168.1.200")

        assertEquals("192.168.1.200", result?.ipAddress)
    }
}
