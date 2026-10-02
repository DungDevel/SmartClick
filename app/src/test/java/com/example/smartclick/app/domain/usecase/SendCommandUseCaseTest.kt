package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.tvconnection.ActiveTvSession
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.connection.ReconnectWatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

import com.example.smartclick.app.domain.model.TvCapability

class SendCommandUseCaseTest {
    private lateinit var activeTvSession: ActiveTvSession
    private lateinit var useCase: SendCommandUseCase
    private lateinit var mockController: ITvController
    private lateinit var mockReconnectWatcher: ReconnectWatcher

    @Before
    fun setup() {
        activeTvSession = mockk(relaxed = true)
        mockController = mockk(relaxed = true)
        mockReconnectWatcher = mockk(relaxed = true)

        val controllerFlow = MutableStateFlow<ITvController?>(mockController)
        every { activeTvSession.currentController } returns controllerFlow
        every { mockController.capabilities } returns setOf(TvCapability.VOLUME)

        // mock controller.sendCommand to return success
        coEvery { mockController.sendCommand(any()) } returns Result.success(Unit)

        useCase = SendCommandUseCase(activeTvSession, mockReconnectWatcher)
    }

    @Test
    fun `send command should be sent when state is Connected and supported`() = runTest {
        // Arrange
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val command = RemoteCommand.VolumeUp

        // Act
        val result = useCase(command)

        // Assert
        assertEquals(SendCommandResult.SUCCESS, result)
        coVerify { mockController.sendCommand(command) }
        coVerify { mockReconnectWatcher.onCommandResult(true) }
    }

    @Test
    fun `send command should report NOT_SUPPORTED and NOT trigger watcher if lacking capability`() = runTest {
        // Arrange
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState
        // Command needing POINTER which is not in capabilities
        val command = RemoteCommand.PointerClick

        // Act
        val result = useCase(command)

        // Assert
        assertEquals(SendCommandResult.NOT_SUPPORTED, result)
        coVerify(exactly = 0) { mockController.sendCommand(command) }
        coVerify(exactly = 0) { mockReconnectWatcher.onCommandResult(any()) }
    }

    @Test
    fun `send command should NOT be sent and report NOT_CONNECTED when state is Idle`() = runTest {
        // Arrange
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
        every { mockController.connectionState } returns connectState

        val command = RemoteCommand.VolumeUp

        // Act
        val result = useCase(command)

        // Assert
        assertEquals(SendCommandResult.NOT_CONNECTED, result)
        coVerify(exactly = 0) { mockController.sendCommand(any()) }
        coVerify(exactly = 0) { mockReconnectWatcher.onCommandResult(any()) }
    }

    @Test
    fun `send command that fails returns FAILED and informs watcher`() = runTest {
        // Arrange
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState
        coEvery { mockController.sendCommand(any()) } returns Result.failure(Exception("Network Error"))

        val command = RemoteCommand.VolumeUp

        // Act
        val result = useCase(command)

        // Assert
        assertEquals(SendCommandResult.FAILED, result)
        coVerify { mockController.sendCommand(command) }
        coVerify { mockReconnectWatcher.onCommandResult(false) }
    }
}
