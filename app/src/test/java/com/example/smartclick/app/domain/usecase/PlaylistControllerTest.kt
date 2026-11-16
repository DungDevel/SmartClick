package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.model.PlaylistStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistControllerTest {

    private lateinit var castMediaUseCase: CastMediaUseCase
    private lateinit var castController: ICastController
    private lateinit var playlistController: PlaylistController
    private val testDispatcher = StandardTestDispatcher()

    private val photo1 = MediaItem("1", "Photo 1", "content://photo1", "image/jpeg")
    private val photo2 = MediaItem("2", "Photo 2", "content://photo2", "image/jpeg")
    private val video1 = MediaItem("3", "Video 1", "content://video1", "video/mp4")

    @Before
    fun setup() {
        castMediaUseCase = mockk(relaxed = true)
        castController = mockk(relaxed = true)
        every { castController.castPlaybackState } returns MutableStateFlow(CastPlaybackState.IDLE)
        every { castMediaUseCase.activeCastController } returns MutableStateFlow(castController)
        coEvery { castMediaUseCase(any()) } returns Result.success(Unit)

        playlistController = PlaylistController(castMediaUseCase, testDispatcher)
    }

    @After
    fun tearDown() {
        playlistController.stop()
    }

    @Test
    fun `start playlist sets initial state and plays first item`() = runTest(testDispatcher) {
        playlistController.bindScope(this)
        playlistController.start(listOf(photo1, photo2), imageDurationSeconds = 3, loop = false)
        runCurrent()

        val state = playlistController.playlistState.value
        assertEquals(PlaylistStatus.PLAYING, state.status)
        assertEquals(0, state.currentIndex)
        assertEquals(photo1, state.currentItem)
        assertEquals(3, state.imageDurationSeconds)
        assertEquals(false, state.loop)
    }

    @Test
    fun `pause and resume updates status correctly`() = runTest(testDispatcher) {
        playlistController.bindScope(this)
        playlistController.start(listOf(photo1, photo2))
        runCurrent()

        playlistController.pause()
        assertEquals(PlaylistStatus.PAUSED, playlistController.playlistState.value.status)

        playlistController.resume()
        assertEquals(PlaylistStatus.PLAYING, playlistController.playlistState.value.status)
    }

    @Test
    fun `next and prev navigate playlist correctly`() = runTest(testDispatcher) {
        playlistController.bindScope(this)
        playlistController.start(listOf(photo1, photo2, video1))

        playlistController.next()
        assertEquals(1, playlistController.playlistState.value.currentIndex)
        assertEquals(photo2, playlistController.playlistState.value.currentItem)

        playlistController.prev()
        assertEquals(0, playlistController.playlistState.value.currentIndex)
        assertEquals(photo1, playlistController.playlistState.value.currentItem)
    }

    @Test
    fun `consecutive errors transition to Error status after threshold`() = runTest(testDispatcher) {
        playlistController.bindScope(this)
        coEvery { castMediaUseCase(any()) } returns Result.failure(Exception("Failed"))

        playlistController.start(listOf(photo1, photo2, video1))
        advanceUntilIdle()

        val state = playlistController.playlistState.value
        assertEquals(PlaylistStatus.ERROR, state.status)
        assertTrue(state.errorCount >= 3)
    }
}
