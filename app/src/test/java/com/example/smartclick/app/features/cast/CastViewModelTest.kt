package com.example.smartclick.app.features.cast

import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.model.SubscriptionPlan
import android.net.Uri
import com.example.smartclick.app.domain.repository.IMediaRepository
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import com.example.smartclick.app.domain.policy.FeatureGate
import com.example.smartclick.app.domain.policy.PremiumFeature
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.app.domain.usecase.CastMediaUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CastViewModelTest {

    private lateinit var mediaRepository: IMediaRepository
    private lateinit var activeTvSession: IActiveTvSession
    private lateinit var castMediaUseCase: CastMediaUseCase
    private lateinit var featureGate: FeatureGate
    private lateinit var subscriptionRepository: ISubscriptionRepository
    private lateinit var controller: ICastController
    private lateinit var tvController: ITvController
    private lateinit var tv: TvDevice
    private lateinit var activeControllerFlow: MutableStateFlow<ITvController?>
    private lateinit var castControllerFlow: MutableStateFlow<ICastController?>
    private val media = MediaItem("1", "Video 1", "content://picker/video", "video/mp4")

    private lateinit var viewModel: CastViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mediaRepository = mockk(relaxed = true)
        activeTvSession = mockk(relaxed = true)
        castMediaUseCase = mockk(relaxed = true)
        castControllerFlow = MutableStateFlow(null)
        every { castMediaUseCase.activeCastController } returns castControllerFlow
        featureGate = mockk(relaxed = true)
        subscriptionRepository = mockk(relaxed = true)
        controller = mockk(relaxed = true)
        tvController = mockk(relaxed = true)
        tv = TvDevice("tv", "TV", "192.168.1.2", "", protocol = TvProtocol.ROKU_ECP,
            capabilities = setOf(TvCapability.CAST_IMAGE, TvCapability.CAST_VIDEO))
        every { tvController.device } returns tv
        every { tvController.connectionState } returns MutableStateFlow(TvConnectionState.Connected(tv))
        activeControllerFlow = MutableStateFlow(tvController)
        every { activeTvSession.currentController } returns activeControllerFlow

        every { mediaRepository.getLocalMedia() } returns MutableStateFlow(
            listOf(media)
        )
        every { subscriptionRepository.currentPlan } returns MutableStateFlow(SubscriptionPlan.PREMIUM)
        every { featureGate.isFeatureAllowed(PremiumFeature.CAST, SubscriptionPlan.PREMIUM) } returns true
        coEvery { mediaRepository.verifyReadable(any()) } returns Result.success(Unit)
        viewModel = CastViewModel(mediaRepository, activeTvSession, castMediaUseCase, featureGate, subscriptionRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty selection and IDLE playback`() = runTest {
        val state = viewModel.uiState.value
        assertEquals(false, state.isCasting)
        assertEquals(0, state.selectedMediaIds.size)
        assertEquals(CastPlaybackState.IDLE, state.playbackState)
    }

    @Test
    fun `toggleSelection adds and removes media from selection`() = runTest {
        viewModel.toggleSelection("1")
        assertTrue(viewModel.uiState.value.selectedMediaIds.contains("1"))

        viewModel.toggleSelection("1")
        assertTrue(viewModel.uiState.value.selectedMediaIds.isEmpty())
    }

    @Test
    fun `Photo Picker result selects repository media into UI state`() = runTest {
        val selected = MediaItem("picked", "photo.jpg", "content://picker/photo", "image/jpeg")
        coEvery { mediaRepository.prepareMedia(any()) } returns Result.success(selected)
        coEvery { mediaRepository.selectMedia(any()) } returns Unit
        coEvery { mediaRepository.verifyReadable(any()) } returns Result.success(Unit)
        val uri = mockk<Uri>(relaxed = true)

        viewModel.onMediaPicked(uri)

        assertTrue(viewModel.uiState.value.selectedMediaIds.contains("picked"))
        coVerify { mediaRepository.prepareMedia(uri) }
        coVerify { mediaRepository.selectMedia(selected) }
    }

    @Test
    fun `startCasting invokes usecase successfully`() = runTest {
        coEvery { mediaRepository.verifyReadable(any()) } returns Result.success(Unit)
        coEvery { castMediaUseCase(any()) } returns Result.success(Unit)
        viewModel.toggleSelection("1")

        viewModel.startCasting()

        assertTrue(viewModel.uiState.value.isCasting)
        assertEquals(CastRequestState.SENT, viewModel.uiState.value.requestState)
        assertEquals(CastPlaybackState.IDLE, viewModel.uiState.value.playbackState)
        coVerify { castMediaUseCase(any()) }
    }

    @Test
    fun `cast state is Preparing while usecase is suspended`() = runTest {
        val pendingCast = CompletableDeferred<Result<Unit>>()
        coEvery { castMediaUseCase(any()) } coAnswers { pendingCast.await() }
        viewModel.toggleSelection(media.id)

        viewModel.startCasting()

        assertEquals(CastRequestState.PREPARING, viewModel.uiState.value.requestState)
        pendingCast.complete(Result.success(Unit))
        runCurrent()
        assertEquals(CastRequestState.SENT, viewModel.uiState.value.requestState)
    }

    @Test
    fun `Sent becomes Playing only after cast controller emits PLAYING`() = runTest {
        val playback = MutableStateFlow(CastPlaybackState.IDLE)
        val session = MutableStateFlow(com.example.smartclick.app.domain.cast.CastSessionInfo())
        val actualCastController = mockk<ICastController>(relaxed = true)
        every { actualCastController.castPlaybackState } returns playback
        every { actualCastController.castSessionInfo } returns session
        castControllerFlow.value = actualCastController
        coEvery { castMediaUseCase(any()) } coAnswers { Result.success(Unit) }
        viewModel.toggleSelection(media.id)

        viewModel.startCasting()

        assertEquals(CastRequestState.SENT, viewModel.uiState.value.requestState)
        assertEquals(CastPlaybackState.IDLE, viewModel.uiState.value.playbackState)
        listOf(CastPlaybackState.BUFFERING, CastPlaybackState.PAUSED, CastPlaybackState.IDLE).forEach { state ->
            playback.value = state
            runCurrent()
            assertTrue(viewModel.uiState.value.playbackState != CastPlaybackState.PLAYING)
        }
        playback.value = CastPlaybackState.PLAYING
        runCurrent()
        assertEquals(CastPlaybackState.PLAYING, viewModel.uiState.value.playbackState)
    }

    @Test
    fun `free user request routes to Premium without opening picker or casting`() = runTest {
        every { subscriptionRepository.currentPlan } returns MutableStateFlow(SubscriptionPlan.FREE)
        every { featureGate.isFeatureAllowed(PremiumFeature.CAST, SubscriptionPlan.FREE) } returns false
        val events = mutableListOf<CastUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvents.collect(events::add) }

        viewModel.requestMediaPicker()

        assertEquals(listOf(CastUiEvent.PremiumRequired), events)
        assertTrue(events.none { it == CastUiEvent.OpenPicker })
        coVerify(exactly = 0) { mediaRepository.prepareMedia(any()) }
        coVerify(exactly = 0) { castMediaUseCase(any()) }
        job.cancel()
    }

    @Test
    fun `stop tears down cast controller and clears selected picker media`() = runTest {
        viewModel.stopCasting()

        coVerify { castMediaUseCase.teardown() }
        coVerify { mediaRepository.clearSelectedMedia() }
    }

    @Test
    fun `onCleared stops cast and releases selected media`() = runTest {
        val cleanup = CountDownLatch(2)
        coEvery { castMediaUseCase.teardown() } coAnswers { cleanup.countDown(); Result.success(Unit) }
        coEvery { mediaRepository.clearSelectedMedia() } coAnswers { cleanup.countDown(); Unit }

        viewModel.javaClass.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(viewModel)

        assertTrue("ViewModel cleanup did not finish", cleanup.await(5, TimeUnit.SECONDS))
        coVerify { castMediaUseCase.teardown() }
        coVerify { mediaRepository.clearSelectedMedia() }
    }

    @Test
    fun `changing active TV stops cast and releases selected media`() = runTest {
        val replacement = mockk<ITvController>(relaxed = true)
        activeControllerFlow.value = replacement

        coVerify { castMediaUseCase.teardown() }
        coVerify { mediaRepository.clearSelectedMedia() }
    }

    @Test
    fun `network failure after sending maps to Error with retry action`() = runTest {
        coEvery { castMediaUseCase(any()) } returns Result.failure(CastError.NetworkError(IllegalStateException("lost connection")))
        viewModel.toggleSelection(media.id)
        val events = mutableListOf<CastUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvents.collect(events::add) }

        viewModel.startCasting()

        assertEquals(CastRequestState.ERROR, viewModel.uiState.value.requestState)
        val failure = events.filterIsInstance<CastUiEvent.CastFailed>().single()
        assertEquals(CastErrorMessage.NETWORK, failure.presentation.message)
        assertEquals(CastErrorAction.RETRY, failure.presentation.action)
        job.cancel()
    }

    @Test
    fun `each CastError has a message and an explicit action`() {
        val errors = listOf(
            CastError.DeviceNotFound(), CastError.InvalidLocationUri("file://x"),
            CastError.DiscoveryFailed(), CastError.MissingAVTransport(),
            CastError.UnsupportedCommand("play"), CastError.SeekNotSupported(),
            CastError.MediaRegistrationFailed(), CastError.NoLanAddress(), CastError.NetworkError(IllegalStateException()),
            CastError.SoapFault("1", "fault"), CastError.DeviceRejectedRequest("no"),
            CastError.InvalidCredentials("bad"), CastError.TlsAuthenticationFailed(),
            CastError.UnsupportedMediaSource("file"), CastError.UnsupportedMediaFormat(null),
            CastError.MediaSourceUnavailable(), CastError.ProtocolCommandFailed("failed")
        )

        assertEquals(17, errors.map { it.toPresentation() }.size)
        assertTrue(errors.all { it.toPresentation().message.name.isNotBlank() })
        assertEquals(CastErrorAction.REPAIR_DEVICE, CastError.TlsAuthenticationFailed().toPresentation().action)
        assertEquals(CastErrorMessage.CERTIFICATE_CHANGED, CastError.TlsAuthenticationFailed().toPresentation().message)
        assertEquals(CastErrorAction.RETRY, CastError.NetworkError(IllegalStateException()).toPresentation().action)
    }

    @Test
    fun `free user is sent to Premium before media is selected or cast`() = runTest {
        every { subscriptionRepository.currentPlan } returns MutableStateFlow(SubscriptionPlan.FREE)
        every { featureGate.isFeatureAllowed(PremiumFeature.CAST, SubscriptionPlan.FREE) } returns false
        coEvery { mediaRepository.prepareMedia(any()) } returns Result.success(media)
        val events = mutableListOf<CastUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvents.collect(events::add) }

        viewModel.onMediaPicked(mockk<Uri>(relaxed = true))

        assertEquals(listOf(CastUiEvent.PremiumRequired), events)
        assertTrue(viewModel.uiState.value.selectedMediaIds.isEmpty())
        coVerify { mediaRepository.prepareMedia(any()) }
        coVerify(exactly = 0) { mediaRepository.selectMedia(any()) }
        coVerify(exactly = 0) { castMediaUseCase(any()) }
        job.cancel()
    }

    @Test
    fun `capability is checked before Premium`() = runTest {
        val limitedTv = tv.copy(capabilities = setOf(TvCapability.CAST_IMAGE))
        every { tvController.device } returns limitedTv
        every { tvController.connectionState } returns MutableStateFlow(TvConnectionState.Connected(limitedTv))
        coEvery { mediaRepository.prepareMedia(any()) } returns Result.success(media)
        val events = mutableListOf<CastUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvents.collect(events::add) }

        viewModel.onMediaPicked(mockk<Uri>(relaxed = true))

        assertEquals(listOf(CastUiEvent.CapabilityMissing), events)
        coVerify(exactly = 0) { subscriptionRepository.currentPlan }
        job.cancel()
    }

    @Test
    fun `no active TV does not open picker`() = runTest {
        every { activeTvSession.currentController } returns MutableStateFlow(null)
        val events = mutableListOf<CastUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvents.collect(events::add) }

        viewModel.requestMediaPicker()

        assertEquals(listOf(CastUiEvent.NoActiveDevice), events)
        coVerify(exactly = 0) { subscriptionRepository.currentPlan }
        job.cancel()
    }

    @Test
    fun `disconnected TV is rejected before media selection`() = runTest {
        every { tvController.connectionState } returns MutableStateFlow(TvConnectionState.Idle)
        coEvery { mediaRepository.prepareMedia(any()) } returns Result.success(media)
        val events = mutableListOf<CastUiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvents.collect(events::add) }

        viewModel.onMediaPicked(mockk<Uri>(relaxed = true))

        assertEquals(listOf(CastUiEvent.NotConnected), events)
        coVerify(exactly = 0) { mediaRepository.selectMedia(any()) }
        coVerify(exactly = 0) { subscriptionRepository.currentPlan }
        job.cancel()
    }

    @Test
    fun `play calls controlCast Play on active controller`() = runTest {
        val mockController = mockk<ICastController>(moreInterfaces = arrayOf(ITvController::class), relaxed = true)
        every { activeTvSession.currentController } returns MutableStateFlow(mockController as ITvController)
        coEvery { mockController.controlCast(any()) } returns Result.success(Unit)

        viewModel.play()

        coVerify { mockController.controlCast(CastCommand.Play) }
    }

    @Test
    fun `pause calls controlCast Pause on active controller`() = runTest {
        val mockController = mockk<ICastController>(moreInterfaces = arrayOf(ITvController::class), relaxed = true)
        every { activeTvSession.currentController } returns MutableStateFlow(mockController as ITvController)
        coEvery { mockController.controlCast(any()) } returns Result.success(Unit)

        viewModel.pause()

        coVerify { mockController.controlCast(CastCommand.Pause) }
    }

    @Test
    fun `seekTo calls controlCast SeekTo on active controller`() = runTest {
        val mockController = mockk<ICastController>(moreInterfaces = arrayOf(ITvController::class), relaxed = true)
        every { activeTvSession.currentController } returns MutableStateFlow(mockController as ITvController)
        coEvery { mockController.controlCast(any()) } returns Result.success(Unit)

        viewModel.seekTo(30000L)

        coVerify { mockController.controlCast(CastCommand.SeekTo(30000L)) }
    }
}
