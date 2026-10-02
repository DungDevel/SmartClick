package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class RemoveDeviceUseCaseTest {

    private val deviceRepository = mockk<ITvDeviceRepository>(relaxed = true)
    private val activeSession = mockk<IActiveTvSession>(relaxed = true)
    private val credentialStore = mockk<ITvCredentialStore>(relaxed = true)

    private val useCase = RemoveDeviceUseCase(deviceRepository, activeSession, credentialStore)

    @Test
    fun `invoke removes device and clears credentials if active`() = runTest {
        val deviceId = UUID.randomUUID().toString()
        val device = TvDevice(deviceId, "LG TV", "1.1.1.1", "mac", brand = "LG", isPaired = true, protocol = TvProtocol.LG_WEBOS, credentialsRef = "ref123")

        val currentController = mockk<ITvController>(relaxed = true)
        every { currentController.connectionState } returns MutableStateFlow(TvConnectionState.Connected(device))
        every { activeSession.currentController } returns MutableStateFlow(currentController)
        every { activeSession.setActiveController(null) } returns Unit

        coEvery { deviceRepository.getDeviceById(deviceId) } returns device

        useCase(deviceId)

        coVerify { currentController.disconnect() }
        coVerify { activeSession.setActiveController(null) }
        coVerify { credentialStore.clearCredentials(deviceId, "ref123") }
        coVerify { deviceRepository.removeDevice(deviceId) }
    }

    @Test
    fun `invoke just removes device if not active and no credentials`() = runTest {
        val deviceId = UUID.randomUUID().toString()
        val device = TvDevice(deviceId, "Roku TV", "1.1.1.1", "mac", brand = "Roku", isPaired = true, protocol = TvProtocol.ROKU_ECP, credentialsRef = null)

        every { activeSession.currentController } returns MutableStateFlow(null)
        coEvery { deviceRepository.getDeviceById(deviceId) } returns device

        useCase(deviceId)

        coVerify(exactly = 0) { credentialStore.clearCredentials(any(), any()) }
        coVerify { deviceRepository.removeDevice(deviceId) }
    }
}
