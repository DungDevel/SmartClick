package com.example.smartclick.app.features.cast

import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.repository.IMediaRepository
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

    private lateinit var viewModel: CastViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mediaRepository = mockk(relaxed = true)
        activeTvSession = mockk(relaxed = true)
        castMediaUseCase = mockk(relaxed = true)

        every { mediaRepository.getLocalMedia() } returns MutableStateFlow(
            listOf(MediaItem("1", "Video 1", "url1", "video/mp4"))
        )
        every { activeTvSession.currentController } returns MutableStateFlow(null)
        every { activeTvSession.connectionState } returns MutableStateFlow(TvConnectionState.Idle)

        viewModel = CastViewModel(mediaRepository, activeTvSession, castMediaUseCase)
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
    fun `startCasting invokes usecase successfully`() = runTest {
        viewModel.toggleSelection("1")
        coEvery { castMediaUseCase(any()) } returns Result.success(Unit)

        viewModel.startCasting()

        assertTrue(viewModel.uiState.value.isCasting)
        coVerify { castMediaUseCase(any()) }
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
