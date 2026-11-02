package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ConnectProgress
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import com.example.smartclick.app.domain.connection.ReconnectWatcher
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.UUID

class RemoveDeviceUseCaseTest {

    private val deviceRepository = mockk<ITvDeviceRepository>(relaxed = true)
    private val activeSession = mockk<IActiveTvSession>(relaxed = true)
    private val credentialStore = mockk<ITvCredentialStore>(relaxed = true)
    private val reconnectWatcher = mockk<ReconnectWatcher>(relaxed = true)

    private val useCase = RemoveDeviceUseCase(deviceRepository, activeSession, credentialStore, reconnectWatcher)

    @Test
    fun `invoke removes device and clears credentials if active`() = runTest {
        val deviceId = UUID.randomUUID().toString()
        val device = TvDevice(deviceId, "LG TV", "1.1.1.1", "mac", brand = "LG", isPaired = true, protocol = TvProtocol.LG_WEBOS, credentialsRef = "ref123")
        val callOrder = mutableListOf<String>()

        val currentController = mockk<ITvController>(relaxed = true)
        every { currentController.device } returns device
        every { currentController.connectionState } returns MutableStateFlow(TvConnectionState.Connected(device))
        every { activeSession.currentController } returns MutableStateFlow(currentController)
        every { reconnectWatcher.cancelReconnect() } answers { callOrder += "cancel" }
        every { activeSession.setActiveController(null) } answers { callOrder += "clear" }
        coEvery { currentController.disconnect() } coAnswers { callOrder += "disconnect" }
        every { reconnectWatcher.isReconnectingForDevice(deviceId) } returns true

        coEvery { deviceRepository.getDeviceById(deviceId) } returns device

        useCase(deviceId)

        assertEquals(listOf("cancel", "disconnect", "clear"), callOrder)
        coVerify { credentialStore.clearCredentials(deviceId, "ref123") }
        coVerify { deviceRepository.removeDevice(deviceId) }
    }

    @Test
    fun `invoke cancels reconnect target when its active controller is already null`() = runTest {
        val deviceId = UUID.randomUUID().toString()
        every { activeSession.currentController } returns MutableStateFlow(null)
        every { reconnectWatcher.isReconnectingForDevice(deviceId) } returns true
        coEvery { deviceRepository.getDeviceById(deviceId) } returns null

        useCase(deviceId)

        io.mockk.verify(exactly = 1) { reconnectWatcher.cancelReconnect() }
        coVerify(exactly = 0) { activeSession.setActiveController(null) }
        coVerify { deviceRepository.removeDevice(deviceId) }
    }

    @Test
    fun `invoke disconnects active controller while connection is still in progress`() = runTest {
        val deviceId = UUID.randomUUID().toString()
        val device = TvDevice(deviceId, "LG TV", "1.1.1.1", "mac", protocol = TvProtocol.LG_WEBOS)
        val callOrder = mutableListOf<String>()
        val connectingController = mockk<ITvController>(relaxed = true)
        every { connectingController.device } returns device
        every { connectingController.connectionState } returns MutableStateFlow(
            TvConnectionState.Connecting(ConnectProgress.CONNECTING_SOCKET)
        )
        every { activeSession.currentController } returns MutableStateFlow(connectingController)
        every { reconnectWatcher.isReconnectingForDevice(deviceId) } returns true
        every { reconnectWatcher.cancelReconnect() } answers { callOrder += "cancel" }
        coEvery { connectingController.disconnect() } coAnswers { callOrder += "disconnect" }
        every { activeSession.setActiveController(null) } answers { callOrder += "clear" }
        coEvery { deviceRepository.getDeviceById(deviceId) } returns device

        useCase(deviceId)

        assertEquals(listOf("cancel", "disconnect", "clear"), callOrder)
        coVerify(exactly = 1) { connectingController.disconnect() }
    }

    @Test
    fun `disconnect exception still clears controller and removes device and credentials`() = runTest {
        val deviceId = UUID.randomUUID().toString()
        val device = TvDevice(
            deviceId, "LG TV", "1.1.1.1", "mac", isPaired = true,
            protocol = TvProtocol.LG_WEBOS, credentialsRef = "ref123"
        )
        val callOrder = mutableListOf<String>()
        val controller = mockk<ITvController>(relaxed = true)
        every { controller.device } returns device
        every { activeSession.currentController } returns MutableStateFlow(controller)
        every { reconnectWatcher.isReconnectingForDevice(deviceId) } returns true
        every { reconnectWatcher.cancelReconnect() } answers { callOrder += "cancel" }
        coEvery { controller.disconnect() } coAnswers {
            callOrder += "disconnect"
            throw IllegalStateException("disconnect failed")
        }
        every { activeSession.setActiveController(null) } answers { callOrder += "clear" }
        coEvery { deviceRepository.getDeviceById(deviceId) } returns device
        coEvery { credentialStore.clearCredentials(deviceId, "ref123") } coAnswers {
            callOrder += "credentials"
        }
        coEvery { deviceRepository.removeDevice(deviceId) } coAnswers { callOrder += "remove" }

        useCase(deviceId)

        assertEquals(listOf("cancel", "disconnect", "clear", "credentials", "remove"), callOrder)
        coVerify(exactly = 1) { activeSession.setActiveController(null) }
        coVerify(exactly = 1) { credentialStore.clearCredentials(deviceId, "ref123") }
        coVerify(exactly = 1) { deviceRepository.removeDevice(deviceId) }
    }

    @Test
    fun `disconnect cancellation clears controller and propagates cancellation`() = runTest {
        val deviceId = UUID.randomUUID().toString()
        val device = TvDevice(deviceId, "LG TV", "1.1.1.1", "mac", protocol = TvProtocol.LG_WEBOS)
        val callOrder = mutableListOf<String>()
        val cancellation = CancellationException("disconnect cancelled")
        val controller = mockk<ITvController>(relaxed = true)
        every { controller.device } returns device
        every { activeSession.currentController } returns MutableStateFlow(controller)
        every { reconnectWatcher.isReconnectingForDevice(deviceId) } returns true
        every { reconnectWatcher.cancelReconnect() } answers { callOrder += "cancel" }
        coEvery { controller.disconnect() } coAnswers {
            callOrder += "disconnect"
            throw cancellation
        }
        every { activeSession.setActiveController(null) } answers { callOrder += "clear" }

        val thrown = try {
            useCase(deviceId)
            null
        } catch (e: CancellationException) {
            e
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf("cancel", "disconnect", "clear"), callOrder)
        coVerify(exactly = 0) { deviceRepository.removeDevice(deviceId) }
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
