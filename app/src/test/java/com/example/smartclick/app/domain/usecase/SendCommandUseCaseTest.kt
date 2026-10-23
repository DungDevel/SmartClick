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

    @Test
    fun `PowerOff should return NOT_SUPPORTED if only POWER_TOGGLE supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.POWER_TOGGLE)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.PowerOff)

        assertEquals(SendCommandResult.NOT_SUPPORTED, result)
        coVerify(exactly = 0) { mockController.sendCommand(any()) }
    }

    @Test
    fun `MuteOn should return NOT_SUPPORTED if only VOLUME supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.VOLUME)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.MuteOn)

        assertEquals(SendCommandResult.NOT_SUPPORTED, result)
        coVerify(exactly = 0) { mockController.sendCommand(any()) }
    }

    @Test
    fun `MuteOn should send MuteOn if MUTE_ON_OFF supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.MUTE_ON_OFF)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.MuteOn)

        assertEquals(SendCommandResult.SUCCESS, result)
        coVerify { mockController.sendCommand(RemoteCommand.MuteOn) }
    }

    @Test
    fun `MuteOff should send MuteOff if MUTE_ON_OFF supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.MUTE_ON_OFF)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.MuteOff)

        assertEquals(SendCommandResult.SUCCESS, result)
        coVerify { mockController.sendCommand(RemoteCommand.MuteOff) }
    }

    @Test
    fun `MuteOff should return NOT_SUPPORTED if only VOLUME supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.VOLUME)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.MuteOff)

        assertEquals(SendCommandResult.NOT_SUPPORTED, result)
        coVerify(exactly = 0) { mockController.sendCommand(any()) }
    }

    @Test
    fun `Mute should send Mute if VOLUME supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.VOLUME)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.Mute)

        assertEquals(SendCommandResult.SUCCESS, result)
        coVerify { mockController.sendCommand(RemoteCommand.Mute) }
    }

    @Test
    fun `PowerOff should send PowerOff if POWER_OFF supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.POWER_OFF)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.PowerOff)

        assertEquals(SendCommandResult.SUCCESS, result)
        coVerify { mockController.sendCommand(RemoteCommand.PowerOff) }
    }

    @Test
    fun `PowerOn should send PowerOn if POWER_ON supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.POWER_ON)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.PowerOn)

        assertEquals(SendCommandResult.SUCCESS, result)
        coVerify { mockController.sendCommand(RemoteCommand.PowerOn) }
    }

    @Test
    fun `PowerOn should return NOT_SUPPORTED if only POWER_TOGGLE supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.POWER_TOGGLE)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.PowerOn)

        assertEquals(SendCommandResult.NOT_SUPPORTED, result)
        coVerify(exactly = 0) { mockController.sendCommand(any()) }
    }

    @Test
    fun `Power should fallback to PowerOff if only POWER_OFF supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.POWER_OFF)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.Power)

        assertEquals(SendCommandResult.SUCCESS, result)
        coVerify { mockController.sendCommand(RemoteCommand.PowerOff) }
    }

    @Test
    fun `Power should send Power if POWER_TOGGLE supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.POWER_TOGGLE)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.Power)

        assertEquals(SendCommandResult.SUCCESS, result)
        coVerify { mockController.sendCommand(RemoteCommand.Power) }
    }

    @Test
    fun `Backspace is sent if BACKSPACE is supported`() = runTest {
        every { mockController.capabilities } returns setOf(TvCapability.BACKSPACE)
        val connectState = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(TvDevice("1", "T", "ip", "mac")))
        every { mockController.connectionState } returns connectState

        val result = useCase(RemoteCommand.Backspace)

        assertEquals(SendCommandResult.SUCCESS, result)
        coVerify { mockController.sendCommand(RemoteCommand.Backspace) }
    }
}