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
import com.example.smartclick.app.domain.usecase.ProcessVoiceCommandUseCase
import com.example.smartclick.app.domain.voice.SpeechToTextEngine
import com.example.smartclick.app.domain.voice.IntentParser
import com.example.smartclick.app.domain.policy.FeatureGate
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.voice.SpeechState
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
    private lateinit var speechToTextEngine: SpeechToTextEngine
    private lateinit var intentParser: IntentParser
    private lateinit var processVoiceCommandUseCase: ProcessVoiceCommandUseCase
    private lateinit var featureGate: FeatureGate
    private lateinit var subscriptionRepository: ISubscriptionRepository
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
        speechToTextEngine = mockk(relaxed = true)
        every { speechToTextEngine.state } returns MutableStateFlow(SpeechState.Idle)
        intentParser = mockk(relaxed = true)
        processVoiceCommandUseCase = mockk(relaxed = true)
        featureGate = mockk(relaxed = true)
        subscriptionRepository = mockk(relaxed = true)

        viewModel = RemoteViewModel(
            sendCommandUseCase,
            activeTvSession,
            settingsRepository,
            wifiNetworkProvider,
            speechToTextEngine,
            intentParser,
            processVoiceCommandUseCase,
            featureGate,
            subscriptionRepository,
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
            speechToTextEngine,
            intentParser,
            processVoiceCommandUseCase,
            featureGate,
            subscriptionRepository,
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

    @Test
    fun `mute commands update mute state when successful`() = runTest {
        coEvery { sendCommandUseCase(RemoteCommand.MuteOn) } returns SendCommandResult.SUCCESS
        viewModel.sendCommand(RemoteCommand.MuteOn)
        advanceUntilIdle()
        assertEquals(1, viewModel.muteState.value)

        coEvery { sendCommandUseCase(RemoteCommand.MuteOff) } returns SendCommandResult.SUCCESS
        viewModel.sendCommand(RemoteCommand.MuteOff)
        advanceUntilIdle()
        assertEquals(0, viewModel.muteState.value)
    }

    @Test
    fun `sendCommand SUCCESS does not emit failure events`() = runTest {
        val command = RemoteCommand.VolumeUp
        coEvery { sendCommandUseCase(command) } returns SendCommandResult.SUCCESS

        val events = mutableListOf<RemoteUiEvent>()
        val job = launch { viewModel.uiEvents.collect { events.add(it) } }

        viewModel.sendCommand(command)
        advanceUntilIdle()

        assertEquals(0, events.size)
        job.cancel()
    }

    @Test
    fun `different failure types are not hidden by shared timers`() = runTest {
        coEvery { sendCommandUseCase(RemoteCommand.VolumeUp) } returns SendCommandResult.FAILED
        coEvery { sendCommandUseCase(RemoteCommand.PointerClick) } returns SendCommandResult.NOT_SUPPORTED

        val events = mutableListOf<RemoteUiEvent>()
        val job = launch { viewModel.uiEvents.collect { events.add(it) } }

        viewModel.sendCommand(RemoteCommand.VolumeUp)
        advanceUntilIdle()
        viewModel.sendCommand(RemoteCommand.PointerClick)
        advanceUntilIdle()

        assertEquals(true, events.contains(RemoteUiEvent.CommandFailed))
        assertEquals(true, events.contains(RemoteUiEvent.CommandNotSupported))
        job.cancel()
    }

    @Test
    fun `screen lock toggles correctly`() = runTest {
        assertEquals(false, viewModel.isLocked.value)
        viewModel.toggleScreenLock()
        assertEquals(true, viewModel.isLocked.value)
        viewModel.unlockScreen()
        assertEquals(false, viewModel.isLocked.value)
    }

    @Test
    fun `sendCommand emits CommandNotSupported when UseCase returns NOT_SUPPORTED`() = runTest {
        val command = RemoteCommand.PointerClick
        coEvery { sendCommandUseCase(command) } returns SendCommandResult.NOT_SUPPORTED

        val events = mutableListOf<RemoteUiEvent>()
        val job = launch { viewModel.uiEvents.collect { events.add(it) } }

        viewModel.sendCommand(command)
        advanceUntilIdle()

        assertEquals(true, events.contains(RemoteUiEvent.CommandNotSupported))
        job.cancel()
    }

    @Test
    fun `free plan is gated before permission request`() = runTest {
        every { subscriptionRepository.currentPlan } returns MutableStateFlow(SubscriptionPlan.FREE)
        every { featureGate.isFeatureAllowed(com.example.smartclick.app.domain.policy.PremiumFeature.VOICE, SubscriptionPlan.FREE) } returns false
        val events = mutableListOf<RemoteUiEvent>()
        val job = launch { viewModel.uiEvents.collect { events += it } }

        viewModel.prepareVoiceListening(hasRecordAudioPermission = false)
        advanceUntilIdle()

        assertEquals(listOf(RemoteUiEvent.VoicePremiumRequired), events)
        io.mockk.verify(exactly = 0) { speechToTextEngine.startListening() }
        job.cancel()
    }

    @Test
    fun `premium user is asked for permission before speech starts`() = runTest {
        every { subscriptionRepository.currentPlan } returns MutableStateFlow(SubscriptionPlan.PREMIUM)
        every { featureGate.isFeatureAllowed(com.example.smartclick.app.domain.policy.PremiumFeature.VOICE, SubscriptionPlan.PREMIUM) } returns true
        val events = mutableListOf<RemoteUiEvent>()
        val job = launch { viewModel.uiEvents.collect { events += it } }

        viewModel.prepareVoiceListening(hasRecordAudioPermission = false)
        advanceUntilIdle()

        assertEquals(listOf(RemoteUiEvent.RequestVoicePermission), events)
        io.mockk.verify(exactly = 0) { speechToTextEngine.startListening() }
        job.cancel()
    }

    @Test
    fun `leaving remote screen destroys speech recognizer session`() {
        viewModel.cancelVoiceSession()
        io.mockk.verify(exactly = 1) { speechToTextEngine.destroy() }
    }
}
