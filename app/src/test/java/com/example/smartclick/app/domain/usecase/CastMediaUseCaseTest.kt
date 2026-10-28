package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.CastMediaType
import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.connection.DriverRegistry
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.tvconnection.ActiveTvSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CastMediaUseCaseTest {

    private lateinit var activeTvSession: ActiveTvSession
    private lateinit var driverRegistry: DriverRegistry
    private lateinit var tvCredentialStore: ITvCredentialStore

    private lateinit var useCase: CastMediaUseCase
    private lateinit var mockController: ITvController
    private lateinit var mockCastController: ICastController
    private lateinit var mockDriver: IProtocolDriver

    private val testDevice = TvDevice("1", "Test TV", "192.168.1.10", "AA:BB:CC:DD:EE:FF", protocol = TvProtocol.ANDROID_TV_V2)
    private val connectionStateFlow = MutableStateFlow<TvConnectionState>(TvConnectionState.Connected(testDevice))

    @Before
    fun setup() {
        activeTvSession = mockk(relaxed = true)
        driverRegistry = mockk(relaxed = true)
        tvCredentialStore = mockk(relaxed = true)

        mockController = mockk(relaxed = true)
        mockCastController = mockk(relaxed = true)
        mockDriver = mockk(relaxed = true)

        every { activeTvSession.currentController } returns MutableStateFlow(mockController)
        every { mockController.connectionState } returns connectionStateFlow
        every { mockController.device } returns testDevice

        every { driverRegistry.getDriver(any()) } returns mockDriver
        every { mockDriver.createCastController(any(), any()) } returns mockCastController
        coEvery { tvCredentialStore.getCredentials(any(), any()) } returns null

        useCase = CastMediaUseCase(
            activeTvSession = activeTvSession,
            driverRegistry = driverRegistry,
            tvCredentialStore = tvCredentialStore
        )
    }

    @Test
    fun `castMedia succeeds when TV is connected and driver supports casting`() = runTest {
        val media = MediaItem("m1", "Video", "http://example.com/video.mp4", "video/mp4")
        coEvery { mockCastController.castMedia(any(), any(), any(), any()) } returns Result.success(Unit)

        val result = useCase(media)

        assertTrue(result.isSuccess)
        coVerify { mockCastController.castMedia("http://example.com/video.mp4", "video/mp4", CastMediaType.VIDEO, "Video") }
    }

    @Test
    fun `castMedia fails when no TV is connected`() = runTest {
        every { activeTvSession.currentController } returns MutableStateFlow(null)
        val media = MediaItem("m1", "Video", "http://example.com/video.mp4", "video/mp4")

        val result = useCase(media)

        assertTrue(result.isFailure)
    }

    @Test
    fun `castMedia fails when driver does not support casting`() = runTest {
        every { mockDriver.createCastController(any(), any()) } returns null
        val media = MediaItem("m1", "Video", "http://example.com/video.mp4", "video/mp4")

        val result = useCase(media)

        assertTrue(result.isFailure)
    }

    @Test
    fun `castMedia returns failure when creating pinned cast controller throws`() = runTest {
        every { mockDriver.createCastController(any(), any()) } throws IllegalStateException("pinned cast setup failed")
        val media = MediaItem("m1", "Video", "http://example.com/video.mp4", "video/mp4")

        val result = useCase(media)

        assertTrue(result.isFailure)
        assertEquals("pinned cast setup failed", result.exceptionOrNull()?.message)
    }
}
