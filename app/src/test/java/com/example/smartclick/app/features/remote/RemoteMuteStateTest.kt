package com.example.smartclick.app.features.remote

import androidx.lifecycle.SavedStateHandle
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.IWifiNetworkProvider
import com.example.smartclick.app.domain.connection.NetworkState
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.AppSettings
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.model.ThemeMode
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.policy.FeatureGate
import com.example.smartclick.app.domain.repository.ISettingsRepository
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import com.example.smartclick.app.domain.usecase.ProcessVoiceCommandUseCase
import com.example.smartclick.app.domain.usecase.SendCommandResult
import com.example.smartclick.app.domain.usecase.SendCommandUseCase
import com.example.smartclick.app.domain.voice.IntentParser
import com.example.smartclick.app.domain.voice.SpeechState
import com.example.smartclick.app.domain.voice.SpeechToTextEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteMuteStateTest {
    private val currentController = MutableStateFlow<ITvController?>(null)
    private val connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    private lateinit var sendCommandUseCase: SendCommandUseCase
    private lateinit var viewModel: RemoteViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val session = mockk<IActiveTvSession>(relaxed = true)
        every { session.currentController } returns currentController
        every { session.connectionState } returns connectionState

        val settings = mockk<ISettingsRepository>(relaxed = true)
        every { settings.settings } returns MutableStateFlow(AppSettings(ThemeMode.SYSTEM, true, null, true, "DPAD"))
        val wifi = mockk<IWifiNetworkProvider>(relaxed = true)
        every { wifi.networkState } returns MutableStateFlow(NetworkState.WifiConnected())
        every { wifi.getCurrentState() } returns NetworkState.WifiConnected()
        val speech = mockk<SpeechToTextEngine>(relaxed = true)
        every { speech.state } returns MutableStateFlow(SpeechState.Idle)
        every { speech.isAvailable } returns true
        sendCommandUseCase = mockk()
        viewModel = RemoteViewModel(
            sendCommandUseCase,
            session,
            settings,
            wifi,
            speech,
            mockk<IntentParser>(relaxed = true),
            mockk<ProcessVoiceCommandUseCase>(relaxed = true),
            mockk<FeatureGate>(relaxed = true),
            mockk<ISubscriptionRepository>(relaxed = true),
            SavedStateHandle()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `mute state is isolated by active device`() = runTest {
        val firstController = controller("tv-1", "One")
        val secondController = controller("tv-2", "Two")
        currentController.value = firstController
        coEvery { sendCommandUseCase(RemoteCommand.MuteOn) } returns SendCommandResult.SUCCESS

        viewModel.sendCommandForHold(RemoteCommand.MuteOn)
        advanceUntilIdle()
        assertEquals(1, viewModel.muteState.value)

        currentController.value = secondController
        advanceUntilIdle()
        assertEquals(-1, viewModel.muteState.value)

        viewModel.sendCommandForHold(RemoteCommand.MuteOn)
        advanceUntilIdle()
        assertEquals(1, viewModel.muteState.value)

        currentController.value = firstController
        advanceUntilIdle()
        assertEquals(1, viewModel.muteState.value)
    }

    @Test
    fun `successful mute command updates the device active when the command started`() = runTest {
        val firstController = controller("tv-1", "One")
        val secondController = controller("tv-2", "Two")
        val commandResult = CompletableDeferred<SendCommandResult>()
        currentController.value = firstController
        coEvery { sendCommandUseCase(RemoteCommand.MuteOn) } coAnswers { commandResult.await() }

        val commandJob = launch { viewModel.sendCommandForHold(RemoteCommand.MuteOn) }
        advanceUntilIdle()
        currentController.value = secondController
        advanceUntilIdle()
        commandResult.complete(SendCommandResult.SUCCESS)
        advanceUntilIdle()

        assertEquals(-1, viewModel.muteState.value)
        currentController.value = firstController
        advanceUntilIdle()
        assertEquals(1, viewModel.muteState.value)
        commandJob.join()
    }

    @Test
    fun `disconnect clears mute state for last connected device after active controller is cleared`() = runTest {
        val device = TvDevice("tv-1", "One", "192.168.1.10", "")
        val firstController = controller("tv-1", "One")
        currentController.value = firstController
        connectionState.value = TvConnectionState.Connected(device)
        advanceUntilIdle()
        coEvery { sendCommandUseCase(RemoteCommand.MuteOn) } returns SendCommandResult.SUCCESS

        viewModel.sendCommandForHold(RemoteCommand.MuteOn)
        advanceUntilIdle()
        assertEquals(1, viewModel.muteState.value)

        currentController.value = null
        connectionState.value = TvConnectionState.Idle
        advanceUntilIdle()
        currentController.value = firstController
        advanceUntilIdle()

        assertEquals(-1, viewModel.muteState.value)
    }

    private fun controller(id: String, name: String): ITvController = mockk(relaxed = true) {
        every { device } returns TvDevice(id, name, "192.168.1.10", "")
    }
}
