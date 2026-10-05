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
}
