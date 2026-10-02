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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        activeTvSession = mockk(relaxed = true)
        every { activeTvSession.connectionState } returns connectionStateFlow

        wifiNetworkProvider = mockk(relaxed = true)
        every { wifiNetworkProvider.networkState } returns networkStateFlow
        every { wifiNetworkProvider.getCurrentState() } returns NetworkState.WifiConnected()

        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.settings } returns MutableStateFlow(
            AppSettings(ThemeMode.SYSTEM, true, null, true)
        )

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
    fun `initial active mode is DPAD`() {
        assertEquals(RemoteMode.DPAD, viewModel.activeMode.value)
    }

    @Test
    fun `setMode updates active mode`() {
        viewModel.setMode(RemoteMode.NUMPAD)
        assertEquals(RemoteMode.NUMPAD, viewModel.activeMode.value)
    }

    @Test
    fun `sendCommand calls usecase and handles fallback properly`() = runTest {
        val command = RemoteCommand.Ok
        coEvery { sendCommandUseCase(command) } returns SendCommandResult.SUCCESS

        viewModel.sendCommand(command)

        coVerify(exactly = 1) { sendCommandUseCase(command) }
    }
}
