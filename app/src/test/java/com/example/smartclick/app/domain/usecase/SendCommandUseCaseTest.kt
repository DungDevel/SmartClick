package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.tvconnection.ActiveTvSession
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.tvconnection.ReconnectWatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

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
        
        every { activeTvSession.currentController.value } returns mockController
        
        // mock controller.sendCommand to return success
        coEvery { mockController.sendCommand(any()) } returns Result.success(Unit)

        useCase = SendCommandUseCase(activeTvSession, mockReconnectWatcher)
    }

    @Test
    fun `send command should be sent when state is Connected`() = runTest {
        // Arrange
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val command = RemoteCommand.VolumeUp

        // Act
        useCase(command)

        // Assert
        coVerify { mockController.sendCommand(command) }
        coVerify { mockReconnectWatcher.onCommandResult(true) }
    }

    @Test
    fun `send command should NOT be sent when state is Idle`() = runTest {
        // Arrange
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
        every { mockController.connectionState } returns connectState

        val command = RemoteCommand.VolumeUp

        // Act
        useCase(command)

        // Assert
        coVerify(exactly = 0) { mockController.sendCommand(any()) }
        coVerify(exactly = 0) { mockReconnectWatcher.onCommandResult(any()) }
    }
}
