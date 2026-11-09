package com.example.smartclick.app.features.remote

import androidx.lifecycle.SavedStateHandle
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.IWifiNetworkProvider
import com.example.smartclick.app.domain.connection.NetworkState
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.connection.WakeOnLan
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemotePowerTest {
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
            mockk(relaxed = true),
            SavedStateHandle()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `valid MAC and successful wake emit sent event without sending TV command`() = runTest {
        val device = setDevice(mac = "aa:bb:cc:dd:ee:ff")
        val events = collectEvents()
        mockkObject(WakeOnLan)
        try {
            coEvery { WakeOnLan.wake(device.macAddress) } returns true

            viewModel.sendCommand(RemoteCommand.Power)
            advanceUntilIdle()

            assertTrue(events.values.contains(RemoteUiEvent.WolSent))
            assertFalse(events.values.any { it::class.simpleName == "WakeOnLanFailed" })
            coVerify(exactly = 1) { WakeOnLan.wake(device.macAddress) }
            coVerify(exactly = 0) { sendCommandUseCase(RemoteCommand.Power) }
        } finally {
            unmockkObject(WakeOnLan)
            events.cancel()
        }
    }

    @Test
    fun `valid MAC and failed wake emit classified retry event without success`() = runTest {
        val device = setDevice(mac = "aa:bb:cc:dd:ee:ff")
        val events = collectEvents()
        mockkObject(WakeOnLan)
        try {
            coEvery { WakeOnLan.wake(device.macAddress) } returns false

            viewModel.sendCommand(RemoteCommand.Power)
            advanceUntilIdle()

            assertEquals("WakeOnLanFailed", events.values.single().let { it::class.simpleName })
            assertFalse(events.values.contains(RemoteUiEvent.WolSent))
            coVerify(exactly = 1) { WakeOnLan.wake(device.macAddress) }
            coVerify(exactly = 0) { sendCommandUseCase(RemoteCommand.Power) }
        } finally {
            unmockkObject(WakeOnLan)
            events.cancel()
        }
    }

    @Test
    fun `empty MAC does not call wake or send command and emits unavailable event`() = runTest {
        val device = setDevice(mac = " ")
        val events = collectEvents()
        mockkObject(WakeOnLan)
        try {
            viewModel.sendCommand(RemoteCommand.Power)
            advanceUntilIdle()

            assertEquals("WakeOnLanUnavailable", events.values.single().let { it::class.simpleName })
            coVerify(exactly = 0) { WakeOnLan.wake(any()) }
            coVerify(exactly = 0) { sendCommandUseCase(RemoteCommand.Power) }
        } finally {
            unmockkObject(WakeOnLan)
            events.cancel()
        }
    }

    @Test
    fun `malformed MAC does not call wake or send command and emits unavailable event`() = runTest {
        val device = setDevice(mac = "not-a-mac")
        val events = collectEvents()
        mockkObject(WakeOnLan)
        try {
            viewModel.sendCommand(RemoteCommand.Power)
            advanceUntilIdle()

            assertEquals("WakeOnLanUnavailable", events.values.single().let { it::class.simpleName })
            coVerify(exactly = 0) { WakeOnLan.wake(any()) }
            coVerify(exactly = 0) { sendCommandUseCase(RemoteCommand.Power) }
        } finally {
            unmockkObject(WakeOnLan)
            events.cancel()
        }
    }

    @Test
    fun `connected Power command does not call Wake-on-LAN`() = runTest {
        val device = setDevice(mac = "aa:bb:cc:dd:ee:ff")
        connectionState.value = TvConnectionState.Connected(device)
        coEvery { sendCommandUseCase(RemoteCommand.Power) } returns SendCommandResult.SUCCESS
        mockkObject(WakeOnLan)
        try {
            viewModel.sendCommand(RemoteCommand.Power)
            advanceUntilIdle()

            coVerify(exactly = 0) { WakeOnLan.wake(any()) }
            coVerify(exactly = 1) { sendCommandUseCase(RemoteCommand.Power) }
        } finally {
            unmockkObject(WakeOnLan)
        }
    }

    private fun setDevice(mac: String): TvDevice {
        val device = TvDevice("tv-1", "Test TV", "192.168.1.10", mac)
        val controller = mockk<ITvController>(relaxed = true)
        every { controller.device } returns device
        currentController.value = controller
        return device
    }

    private data class CollectedEvents(
        val values: MutableList<RemoteUiEvent>,
        private val jobs: List<Job>
    ) {
        fun cancel() = jobs.forEach(Job::cancel)
    }

    private fun kotlinx.coroutines.test.TestScope.collectEvents(): CollectedEvents {
        val values = mutableListOf<RemoteUiEvent>()
        val activeDeviceJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.activeDevice.collect {} }
        val eventJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvents.collect(values::add) }
        return CollectedEvents(values, listOf(activeDeviceJob, eventJob))
    }
}
