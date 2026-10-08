package com.example.smartclick.app.tvconnection.lg.cast

import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.model.CastMediaType
import com.example.smartclick.app.tvconnection.cast.LocalMediaServer
import io.mockk.coEvery
import io.mockk.mockk
import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class LgWebOsCastControllerTest {

    private lateinit var fakeTransport: FakeLgWebOsCastTransport
    private lateinit var mockLocalMediaServer: LocalMediaServer
    private lateinit var controller: LgWebOsCastController

    @Before
    fun setup() {
        io.mockk.mockkStatic(Uri::class)
        io.mockk.every { Uri.parse(any()) } returns mockk(relaxed = true)

        fakeTransport = FakeLgWebOsCastTransport()
        mockLocalMediaServer = mockk()

        coEvery { mockLocalMediaServer.registerMedia(any(), any(), any()) } answers {
            com.example.smartclick.app.tvconnection.cast.CastMediaSession(
                sessionId = "mock-id",
                token = "mock-token",
                mediaUrl = "http://192.168.1.100:8080/mock-video.mp4",
                uri = firstArg<Uri>(),
                mimeType = secondArg<String>(),
                title = thirdArg<String?>()
            )
        }

        controller = LgWebOsCastController(
            transport = fakeTransport,
            localMediaServer = mockLocalMediaServer,
            ipAddress = "192.168.1.50",
            clientKey = "mock-client-key"
        )
    }

    @After
    fun teardown() {
        io.mockk.unmockkAll()
    }

    @Test
    fun `castMedia automatically connects if not connected`() = runTest {
        val result = controller.castMedia("http://example.com/video.mp4", "video/mp4", CastMediaType.VIDEO, "Test Video")

        assertTrue(result.isSuccess)
        assertTrue(fakeTransport.isConnected)
        assertEquals("mock-client-key", fakeTransport.registeredKeys.first())
    }

    @Test
    fun `castMedia transforms content uri via local server`() = runTest {
        val result = controller.castMedia("content://media/1", "video/mp4", CastMediaType.VIDEO, "Local Video")

        assertTrue(result.isSuccess)
        val req = fakeTransport.recordedRequests.find { it.optString("uri") == "ssap://media.viewer/open" }
        val payload = req?.optJSONObject("payload")

        assertEquals("http://192.168.1.100:8080/mock-video.mp4", payload?.optString("target"))
        assertEquals(CastPlaybackState.BUFFERING, controller.castPlaybackState.value)
    }

    @Test
    fun `castMedia fails if missing sessionId in response`() = runTest {
        fakeTransport.overrideResponse = JSONObject().apply { put("returnValue", true) } // No sessionId

        val result = controller.castMedia("http://example.com/video.mp4", "video/mp4", CastMediaType.VIDEO, "Test Video")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CastError.ProtocolCommandFailed)
        assertEquals(CastPlaybackState.IDLE, controller.castPlaybackState.value)
    }

    @Test
    fun `controlCast Play does not transition state to PLAYING directly (avoids false positive)`() = runTest {
        controller.castMedia("http://example.com/video.mp4", "video/mp4", CastMediaType.VIDEO, "Test Video")

        val result = controller.controlCast(CastCommand.Play)
        assertTrue(result.isSuccess)
        assertEquals(CastPlaybackState.BUFFERING, controller.castPlaybackState.value) // State staysBUFFERING because of open
        val req = fakeTransport.recordedRequests.last()
        assertEquals("ssap://media.controls/play", req.optString("uri"))
    }

    @Test
    fun `controlCast Pause does not transition state to PAUSED directly`() = runTest {
        controller.castMedia("http://example.com/video.mp4", "video/mp4", CastMediaType.VIDEO, "Test Code")

        val result = controller.controlCast(CastCommand.Pause)
        assertTrue(result.isSuccess)
        assertEquals(CastPlaybackState.BUFFERING, controller.castPlaybackState.value)
    }

    @Test
    fun `controlCast Stop sends stop command and transitions state`() = runTest {
        controller.castMedia("http://ex.com/vid", "video/mp4", CastMediaType.VIDEO, "")

        val result = controller.controlCast(CastCommand.Stop)
        assertTrue(result.isSuccess)
        assertEquals(CastPlaybackState.IDLE, controller.castPlaybackState.value)

        val lastReq = fakeTransport.recordedRequests.last()
        assertEquals("ssap://media.controls/stop", lastReq.optString("uri"))
    }

    @Test
    fun `controlCast Seek To and Seek By are unsupported`() = runTest {
        fakeTransport.connect("192.168.1.50", "mock-client-key")
        val res = controller.controlCast(CastCommand.SeekTo(5000))
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is CastError.UnsupportedCommand)
    }

    @Test
    fun `teardown closes viewer and resets state`() = runTest {
        controller.castMedia("http://ex.com/vid", "video/mp4", CastMediaType.VIDEO, "")
        fakeTransport.recordedRequests.clear() // clear the open call

        val result = controller.teardown()
        assertTrue(result.isSuccess)

        assertEquals(CastPlaybackState.IDLE, controller.castPlaybackState.value)
        val closeReq = fakeTransport.recordedRequests.first()
        assertEquals("ssap://media.viewer/close", closeReq.optString("uri"))
        assertEquals("test-session-123", closeReq.optJSONObject("payload")?.optString("sessionId"))

        assertTrue(!fakeTransport.isConnected)
    }
}
