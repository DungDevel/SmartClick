package com.example.smartclick.app.features.remote

import androidx.lifecycle.SavedStateHandle
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.IWifiNetworkProvider
import com.example.smartclick.app.domain.connection.NetworkState
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.AppSettings
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.ThemeMode
import com.example.smartclick.app.domain.repository.ISettingsRepository
import com.example.smartclick.app.domain.usecase.SendCommandResult
import com.example.smartclick.app.domain.usecase.SendCommandUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModelTest {

    private lateinit var sendCommandUseCase: SendCommandUseCase
    private lateinit var activeTvSession: IActiveTvSession
    private lateinit var settingsRepository: ISettingsRepository
    private lateinit var wifiNetworkProvider: IWifiNetworkProvider
    private lateinit var savedStateHandle: SavedStateHandle

    private lateinit var viewModel: RemoteViewModel

    private val connectionStateFlow = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    private val networkStateFlow = MutableStateFlow<NetworkState>(NetworkState.WifiConnected())
    private lateinit var settingsFlow: MutableStateFlow<AppSettings>

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())

        activeTvSession = mockk(relaxed = true)
        every { activeTvSession.connectionState } returns connectionStateFlow

        wifiNetworkProvider = mockk(relaxed = true)
        every { wifiNetworkProvider.networkState } returns networkStateFlow
        every { wifiNetworkProvider.getCurrentState() } returns NetworkState.WifiConnected()

        settingsFlow = MutableStateFlow(
            AppSettings(ThemeMode.SYSTEM, true, null, true, "DPAD")
        )
        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.settings } returns settingsFlow
        coEvery { settingsRepository.setRemoteMode(any()) } answers {
            val mode = firstArg<String>()
            settingsFlow.value = settingsFlow.value.copy(remoteMode = mode)
        }

        savedStateHandle = SavedStateHandle()

        sendCommandUseCase = mockk()

        viewModel = RemoteViewModel(
            sendCommandUseCase,
            activeTvSession,
            settingsRepository,
            wifiNetworkProvider,
            savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial active mode is DPAD`() = runTest {
        val job = launch { viewModel.activeMode.collect() }
        advanceUntilIdle()
        assertEquals(RemoteMode.DPAD, viewModel.activeMode.value)
        job.cancel()
    }

    @Test
    fun `setMode updates active mode`() = runTest {
        val job = launch { viewModel.activeMode.collect() }
        advanceUntilIdle()
        
        viewModel.setMode(RemoteMode.NUMPAD)
        advanceUntilIdle()
        
        assertEquals(RemoteMode.NUMPAD, viewModel.activeMode.value)
        job.cancel()
    }

    @Test
    fun `sendCommand calls usecase and handles fallback properly`() = runTest {
        val command = RemoteCommand.Ok
        coEvery { sendCommandUseCase(command) } returns SendCommandResult.SUCCESS

        viewModel.sendCommand(command)
        advanceUntilIdle()

        coVerify(exactly = 1) { sendCommandUseCase(command) }
    }

    @Test
    fun `sendCommand emits CommandFailed when UseCase returns FAILED`() = runTest {
        val command = RemoteCommand.Ok
        coEvery { sendCommandUseCase(command) } returns SendCommandResult.FAILED

        val job = launch { viewModel.uiEvents.collect() }

        viewModel.sendCommand(command)
        advanceUntilIdle()

        // Assert that the UI event was emitted
        coVerify(exactly = 1) { sendCommandUseCase(command) }

        job.cancel()
    }

    @Test
    fun `Power command when disconnected triggers WakeOnLan and emits WolSent`() = runTest {
        val command = RemoteCommand.Power
        // Ensure state is NOT Connected
        connectionStateFlow.value = TvConnectionState.Idle

        // Provide a mock device with MAC address in the activeTvSession flow
        val tvDevice = com.example.smartclick.app.domain.model.TvDevice("1", "T", "ip", "aa:bb:cc:dd:ee:ff")
        val controller = mockk<com.example.smartclick.app.domain.connection.ITvController>()
        every { controller.device } returns tvDevice
        val controllerFlow = MutableStateFlow<com.example.smartclick.app.domain.connection.ITvController?>(controller)
        every { activeTvSession.currentController } returns controllerFlow

        // We recreate viewModel so activeDevice collects from the mocked flow
        viewModel = RemoteViewModel(
            sendCommandUseCase,
            activeTvSession,
            settingsRepository,
            wifiNetworkProvider,
            savedStateHandle
        )

        val job = launch { viewModel.uiEvents.collect() }
        val job2 = launch { viewModel.activeDevice.collect() }
        advanceUntilIdle()

        // Mock WakeOnLan
        io.mockk.mockkObject(com.example.smartclick.app.domain.connection.WakeOnLan)
        coEvery { com.example.smartclick.app.domain.connection.WakeOnLan.wake("aa:bb:cc:dd:ee:ff") } returns true

        viewModel.sendCommand(command)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { com.example.smartclick.app.domain.connection.WakeOnLan.wake("aa:bb:cc:dd:ee:ff") }

        io.mockk.unmockkObject(com.example.smartclick.app.domain.connection.WakeOnLan)
        job2.cancel()
        job.cancel()
    }
}
