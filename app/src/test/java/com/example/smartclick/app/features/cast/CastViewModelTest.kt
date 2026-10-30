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
import com.example.smartclick.app.domain.usecase.CastMediaUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    private val media = MediaItem("1", "Video 1", "content://picker/video", "video/mp4")

    private lateinit var viewModel: CastViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mediaRepository = mockk(relaxed = true)
        activeTvSession = mockk(relaxed = true)
        castMediaUseCase = mockk(relaxed = true)
        featureGate = mockk(relaxed = true)
        subscriptionRepository = mockk(relaxed = true)
        controller = mockk(relaxed = true)
        tvController = mockk(relaxed = true)
        tv = TvDevice("tv", "TV", "192.168.1.2", "", protocol = TvProtocol.ROKU_ECP,
            capabilities = setOf(TvCapability.CAST_IMAGE, TvCapability.CAST_VIDEO))
        every { tvController.device } returns tv
        every { tvController.connectionState } returns MutableStateFlow(TvConnectionState.Connected(tv))
        every { activeTvSession.currentController } returns MutableStateFlow(tvController)

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
        coVerify { castMediaUseCase(any()) }
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
