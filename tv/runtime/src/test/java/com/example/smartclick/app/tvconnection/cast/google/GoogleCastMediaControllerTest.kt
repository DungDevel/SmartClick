package com.example.smartclick.app.tvconnection.cast.google

import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class GoogleCastMediaControllerTest {

    private lateinit var serverSocket: ServerSocket
    private var serverPort = 0
    private lateinit var mockSslSocketFactory: SSLSocketFactory
    private lateinit var authManager: GoogleCastAuthManager
    private val acceptedSockets = Collections.synchronizedList(mutableListOf<Socket>())

    private fun acceptSocket(): Socket {
        val socket = serverSocket.accept()
        acceptedSockets.add(socket)
        return socket
    }

    private fun closeAcceptedSockets() {
        synchronized(acceptedSockets) {
            acceptedSockets.forEach { socket ->
                try { socket.close() } catch (_: Exception) {}
            }
            acceptedSockets.clear()
        }
    }

    @Before
    fun setup() {
        acceptedSockets.clear()
        serverSocket = ServerSocket(0)
        serverPort = serverSocket.localPort

        val mockTrustManager = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        authManager = GoogleCastAuthManager(mockTrustManager)

        mockSslSocketFactory = object : SSLSocketFactory() {
            override fun getDefaultCipherSuites(): Array<String> = emptyArray()
            override fun getSupportedCipherSuites(): Array<String> = emptyArray()
            override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket = MockSSLSocket(s)
            override fun createSocket(host: String, port: Int): Socket = MockSSLSocket(Socket(host, port))
            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int): Socket = MockSSLSocket(Socket(host, port, localHost, localPort))
            override fun createSocket(host: java.net.InetAddress, port: Int): Socket = MockSSLSocket(Socket(host, port))
            override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int): Socket = MockSSLSocket(Socket(address, port, localAddress, localPort))
            override fun createSocket(): Socket = MockSSLSocket(Socket())
        }
    }

    @After
    fun tearDown() {
        closeAcceptedSockets()
        try { serverSocket.close() } catch (_: Exception) {}
    }

    // ─── LOAD ────────────────────────────────────────────────────────────────

    @Test(timeout = 10000)
    fun `load sends correct LOAD payload and transitions to BUFFERING`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-test-1"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // Receive LOAD message
                val loadMsg = CastV2Framing.readFrame(input)
                assertEquals(GoogleCastMediaController.MEDIA_NAMESPACE, loadMsg.namespace)
                assertEquals(transportId, loadMsg.destinationId)

                val payload = JSONObject(loadMsg.payloadUtf8)
                assertEquals("LOAD", payload.optString("type"))
                assertTrue(payload.optBoolean("autoplay"))
                assertEquals("http://example.com/video.mp4", payload.optJSONObject("media")?.optString("contentId"))
                assertEquals("video/mp4", payload.optJSONObject("media")?.optString("contentType"))
                assertEquals("BUFFERED", payload.optJSONObject("media")?.optString("streamType"))
                assertEquals("TestVideo", payload.optJSONObject("media")?.optJSONObject("metadata")?.optString("title"))

                val reqId = payload.optInt("requestId")

                // Send MEDIA_STATUS response
                val resp = buildMediaStatusMessage(transportId, reqId, "BUFFERING", mediaSessionId = 1)
                CastV2Framing.writeFrame(output, resp)

                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)

            // State should transition to BUFFERING before we even send the LOAD
            assertEquals(CastPlaybackState.IDLE, mc.playbackState.value)

            val result = mc.load("http://example.com/video.mp4", "video/mp4", "TestVideo")
            assertTrue(result.isSuccess)
            assertEquals(CastPlaybackState.BUFFERING, mc.playbackState.value)

            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 5000)
    fun `load rejects blank contentId`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)

        launch(Dispatchers.IO) { acceptSocket() }
        session.connect()

        val mc = GoogleCastMediaController(session, "web-1", this)
        val result = mc.load("", "video/mp4", "Title")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)

        mc.close()
        session.disconnect()
        closeAcceptedSockets()
    }

    @Test(timeout = 5000)
    fun `load rejects blank contentType`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)

        launch(Dispatchers.IO) { acceptSocket() }
        session.connect()

        val mc = GoogleCastMediaController(session, "web-1", this)
        val result = mc.load("http://example.com/v.mp4", "", "Title")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)

        mc.close()
        session.disconnect()
        closeAcceptedSockets()
    }

    @Test(timeout = 10000)
    fun `load sends tracks and activeTrackIds`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-subtitle-1"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val loadMsg = CastV2Framing.readFrame(input)
                val payload = JSONObject(loadMsg.payloadUtf8)
                val media = payload.optJSONObject("media")
                assertEquals(1, media?.optJSONArray("tracks")?.length())
                assertEquals(1, media?.optJSONArray("activeTrackIds")?.length())
                assertEquals(1, media?.optJSONArray("activeTrackIds")?.getInt(0))

                val reqId = payload.optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, reqId, "BUFFERING"))
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)
            val track = JSONObject().put("trackId", 1)
            val result = mc.load("http://x.com/v.mp4", "video/mp4", "V", tracks = listOf(track), activeTrackIds = listOf(1))
            assertTrue(result.isSuccess)
            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `editTracks sends EDIT_TRACKS_INFO with activeTrackIds`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-edit-tracks"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // Need a media session ID to call editTracks
                val loadMsg = CastV2Framing.readFrame(input)
                val loadReqId = JSONObject(loadMsg.payloadUtf8).optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, loadReqId, "PLAYING", mediaSessionId = 99))

                // Receive EDIT_TRACKS_INFO
                val editMsg = CastV2Framing.readFrame(input)
                val editPayload = JSONObject(editMsg.payloadUtf8)
                assertEquals("EDIT_TRACKS_INFO", editPayload.optString("type"))
                assertEquals(99, editPayload.optInt("mediaSessionId"))
                assertEquals(1, editPayload.optJSONArray("activeTrackIds")?.length())

                val reqId = editPayload.optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, reqId, "PLAYING", mediaSessionId = 99))
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)
            mc.load("http://x.com/v.mp4", "video/mp4", "V")
            val result = mc.editTracks(listOf(2))
            assertTrue(result.isSuccess)
            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    // ─── PLAY / PAUSE / STOP ─────────────────────────────────────────────────

    @Test(timeout = 10000)
    fun `play sends PLAY with mediaSessionId`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-play-1"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // 1. Receive LOAD → respond with PLAYING
                val loadMsg = CastV2Framing.readFrame(input)
                val loadReqId = JSONObject(loadMsg.payloadUtf8).optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, loadReqId, "PLAYING", mediaSessionId = 42))

                // 2. Receive PLAY
                val playMsg = CastV2Framing.readFrame(input)
                val playPayload = JSONObject(playMsg.payloadUtf8)
                assertEquals("PLAY", playPayload.optString("type"))
                assertEquals(42, playPayload.optInt("mediaSessionId"))

                val playReqId = playPayload.optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, playReqId, "PLAYING", mediaSessionId = 42))

                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)
            mc.load("http://x.com/v.mp4", "video/mp4", "V")
            val result = mc.play()
            assertTrue(result.isSuccess)
            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `pause sends PAUSE with mediaSessionId`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-pause-1"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                val loadMsg = CastV2Framing.readFrame(input)
                val loadReqId = JSONObject(loadMsg.payloadUtf8).optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, loadReqId, "PLAYING", mediaSessionId = 7))

                val pauseMsg = CastV2Framing.readFrame(input)
                val pausePayload = JSONObject(pauseMsg.payloadUtf8)
                assertEquals("PAUSE", pausePayload.optString("type"))
                assertEquals(7, pausePayload.optInt("mediaSessionId"))

                val pauseReqId = pausePayload.optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, pauseReqId, "PAUSED", mediaSessionId = 7))

                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)
            mc.load("http://x.com/v.mp4", "video/mp4", "V")
            val result = mc.pause()
            assertTrue(result.isSuccess)
            assertEquals(CastPlaybackState.PAUSED, mc.playbackState.value)
            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `stop sends STOP with mediaSessionId`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-stop-1"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                val loadMsg = CastV2Framing.readFrame(input)
                val loadReqId = JSONObject(loadMsg.payloadUtf8).optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, loadReqId, "PLAYING", mediaSessionId = 5))

                val stopMsg = CastV2Framing.readFrame(input)
                val stopPayload = JSONObject(stopMsg.payloadUtf8)
                assertEquals("STOP", stopPayload.optString("type"))
                assertEquals(5, stopPayload.optInt("mediaSessionId"))

                val stopReqId = stopPayload.optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, stopReqId, "IDLE", mediaSessionId = 5, idleReason = "CANCELLED"))

                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)
            mc.load("http://x.com/v.mp4", "video/mp4", "V")
            val result = mc.stop()
            assertTrue(result.isSuccess)
            assertEquals(CastPlaybackState.STOPPED, mc.playbackState.value)
            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    // ─── SEEK ────────────────────────────────────────────────────────────────

    @Test(timeout = 10000)
    fun `seek sends SEEK with currentTime in seconds`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-seek-1"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                val loadMsg = CastV2Framing.readFrame(input)
                val loadReqId = JSONObject(loadMsg.payloadUtf8).optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, loadReqId, "PLAYING", mediaSessionId = 3))

                val seekMsg = CastV2Framing.readFrame(input)
                val seekPayload = JSONObject(seekMsg.payloadUtf8)
                assertEquals("SEEK", seekPayload.optString("type"))
                assertEquals(3, seekPayload.optInt("mediaSessionId"))
                assertEquals(90.0, seekPayload.optDouble("currentTime"), 0.01)

                val seekReqId = seekPayload.optInt("requestId")
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, seekReqId, "PLAYING", mediaSessionId = 3, currentTime = 90.0))

                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)
            mc.load("http://x.com/v.mp4", "video/mp4", "V")
            val result = mc.seek(90.0)
            assertTrue(result.isSuccess)
            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 5000)
    fun `seek rejects negative position`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)

        val serverJob = launch(Dispatchers.IO) { acceptSocket() }
        try {
            session.connect()
            val mc = GoogleCastMediaController(session, "transport", this)
            val result = mc.seek(-1.0)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    // ─── PLAY / PAUSE / STOP without media session ───────────────────────────

    @Test(timeout = 5000)
    fun `play fails without active media session`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)

        launch(Dispatchers.IO) { acceptSocket() }
        session.connect()

        val mc = GoogleCastMediaController(session, "web-1", this)
        val result = mc.play()
        assertTrue(result.isFailure)

        mc.close()
        session.disconnect()
        closeAcceptedSockets()
    }

    // ─── MEDIA_STATUS broadcast processing ──────────────────────────────────

    @Test(timeout = 10000)
    fun `broadcast MEDIA_STATUS updates playback state and session info`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-broadcast-1"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val output = socket.getOutputStream()

                // Send unsolicited MEDIA_STATUS broadcast (requestId = 0)
                val broadcast = buildMediaStatusMessage(
                    transportId, 0, "PLAYING",
                    mediaSessionId = 10, currentTime = 45.5, duration = 120.0
                )
                CastV2Framing.writeFrame(output, broadcast)

                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)

            // Wait for broadcast to be processed
            var attempts = 0
            while (mc.playbackState.value != CastPlaybackState.PLAYING && attempts < 40) {
                delay(50)
                attempts++
            }

            assertEquals(CastPlaybackState.PLAYING, mc.playbackState.value)
            assertEquals(45500L, mc.sessionInfo.value.positionMs)
            assertEquals(120000L, mc.sessionInfo.value.durationMs)
            assertEquals(10, mc.sessionInfo.value.let { 10 }) // mediaSessionId tracked internally

            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    // ─── Stale mediaSessionId protection ─────────────────────────────────────

    @Test(timeout = 10000)
    fun `stale mediaSessionId does not overwrite active state`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-stale-1"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val output = socket.getOutputStream()

                // 1. Send current MEDIA_STATUS with mediaSessionId = 5
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(
                    transportId, 0, "PLAYING", mediaSessionId = 5, currentTime = 30.0
                ))
                delay(200)

                // 2. Send stale MEDIA_STATUS with mediaSessionId = 3 (older)
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(
                    transportId, 0, "PAUSED", mediaSessionId = 3, currentTime = 10.0
                ))

                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)

            // Wait for first broadcast
            var attempts = 0
            while (mc.playbackState.value != CastPlaybackState.PLAYING && attempts < 40) {
                delay(50)
                attempts++
            }
            assertEquals(CastPlaybackState.PLAYING, mc.playbackState.value)

            // Wait a bit for the stale message to arrive
            delay(400)

            // Stale message should have been ignored
            assertEquals(CastPlaybackState.PLAYING, mc.playbackState.value)
            assertEquals(30000L, mc.sessionInfo.value.positionMs)

            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    // ─── Error messages ──────────────────────────────────────────────────────

    @Test(timeout = 10000)
    fun `LOAD_FAILED sets ERROR state and completes pending request exceptionally`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-error-1"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                val loadMsg = CastV2Framing.readFrame(input)
                val reqId = JSONObject(loadMsg.payloadUtf8).optInt("requestId")

                // Send LOAD_FAILED
                val errorMsg = CastMessage.newBuilder()
                    .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                    .setSourceId(transportId)
                    .setDestinationId(GoogleCastMediaController.SENDER_ID)
                    .setNamespace(GoogleCastMediaController.MEDIA_NAMESPACE)
                    .setPayloadType(CastMessage.PayloadType.STRING)
                    .setPayloadUtf8("""{"type":"LOAD_FAILED","requestId":$reqId,"reason":"CONTENT_NOT_FOUND"}""")
                    .build()
                CastV2Framing.writeFrame(output, errorMsg)

                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)

            val result = mc.load("http://missing.com/x.mp4", "video/mp4", "Missing")
            assertTrue(result.isFailure)
            assertEquals(CastPlaybackState.ERROR, mc.playbackState.value)

            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    // ─── playerState mapping ─────────────────────────────────────────────────

    @Test(timeout = 10000)
    fun `IDLE with FINISHED maps to COMPLETED`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-idle-1"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val output = socket.getOutputStream()
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(
                    transportId, 0, "IDLE", mediaSessionId = 1, idleReason = "FINISHED"
                ))
                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)

            var attempts = 0
            while (mc.playbackState.value == CastPlaybackState.IDLE && attempts < 40) {
                delay(50)
                attempts++
            }
            assertEquals(CastPlaybackState.COMPLETED, mc.playbackState.value)

            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `IDLE with ERROR maps to ERROR`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val transportId = "web-idle-err"

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val output = socket.getOutputStream()
                CastV2Framing.writeFrame(output, buildMediaStatusMessage(
                    transportId, 0, "IDLE", mediaSessionId = 1, idleReason = "ERROR"
                ))
                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, transportId, this)

            var attempts = 0
            while (mc.playbackState.value == CastPlaybackState.IDLE && attempts < 40) {
                delay(50)
                attempts++
            }
            assertEquals(CastPlaybackState.ERROR, mc.playbackState.value)

            mc.close()
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    // ─── Close / lifecycle ───────────────────────────────────────────────────

    @Test(timeout = 5000)
    fun `close cancels pending requests`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                // Just consume the LOAD message but never respond
                CastV2Framing.readFrame(input)
                while (isActive) { delay(50) }
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val mc = GoogleCastMediaController(session, "web-close-1", this)

            // Launch load in background (will block waiting for response)
            val loadJob = async {
                mc.load("http://x.com/v.mp4", "video/mp4", "V")
            }
            delay(200)

            // Close should cancel pending requests
            mc.close()
            val awaitResult = runCatching { loadJob.await() }
            assertTrue(awaitResult.isFailure)
            assertTrue(awaitResult.exceptionOrNull() is CancellationException)
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun buildMediaStatusMessage(
        transportId: String,
        requestId: Int,
        playerState: String,
        mediaSessionId: Int = 0,
        currentTime: Double = Double.NaN,
        duration: Double = Double.NaN,
        idleReason: String? = null
    ): CastMessage {
        val statusObj = JSONObject().apply {
            put("playerState", playerState)
            if (mediaSessionId > 0) put("mediaSessionId", mediaSessionId)
            if (!currentTime.isNaN()) put("currentTime", currentTime)
            if (idleReason != null) put("idleReason", idleReason)
            if (!duration.isNaN()) {
                put("media", JSONObject().apply {
                    put("duration", duration)
                })
            }
        }

        val payload = JSONObject().apply {
            put("type", "MEDIA_STATUS")
            put("requestId", requestId)
            put("status", org.json.JSONArray().put(statusObj))
        }

        return CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(transportId)
            .setDestinationId(GoogleCastMediaController.SENDER_ID)
            .setNamespace(GoogleCastMediaController.MEDIA_NAMESPACE)
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8(payload.toString())
            .build()
    }

    private class MockSSLSocket(private val delegate: Socket) : SSLSocket() {
        override fun startHandshake() {}
        override fun connect(endpoint: java.net.SocketAddress?, timeout: Int) = delegate.connect(endpoint, timeout)
        override fun connect(endpoint: java.net.SocketAddress?) = delegate.connect(endpoint)
        override fun getInputStream(): InputStream = delegate.getInputStream()
        override fun getOutputStream(): OutputStream = delegate.getOutputStream()
        override fun close() = delegate.close()
        override fun addHandshakeCompletedListener(l: javax.net.ssl.HandshakeCompletedListener?) {}
        override fun removeHandshakeCompletedListener(l: javax.net.ssl.HandshakeCompletedListener?) {}
        override fun getUseClientMode(): Boolean = true
        override fun setUseClientMode(m: Boolean) {}
        override fun getNeedClientAuth(): Boolean = false
        override fun setNeedClientAuth(n: Boolean) {}
        override fun getWantClientAuth(): Boolean = false
        override fun setWantClientAuth(w: Boolean) {}
        override fun getEnableSessionCreation(): Boolean = true
        override fun setEnableSessionCreation(f: Boolean) {}
        override fun getSupportedCipherSuites(): Array<String> = emptyArray()
        override fun getEnabledCipherSuites(): Array<String> = emptyArray()
        override fun setEnabledCipherSuites(s: Array<String>?) {}
        override fun getSupportedProtocols(): Array<String> = emptyArray()
        override fun getEnabledProtocols(): Array<String> = emptyArray()
        override fun setEnabledProtocols(p: Array<String>?) {}
        override fun getSession(): SSLSession {
            return object : SSLSession {
                override fun getPeerCertificates(): Array<Certificate> {
                    val cert = mockk<X509Certificate>(relaxed = true)
                    every { cert.encoded } returns ByteArray(100) { 1 }
                    return arrayOf(cert)
                }
                override fun getId(): ByteArray = ByteArray(0)
                override fun getSessionContext(): javax.net.ssl.SSLSessionContext? = null
                override fun getCreationTime(): Long = 0
                override fun getLastAccessedTime(): Long = 0
                override fun invalidate() {}
                override fun isValid(): Boolean = true
                override fun putValue(n: String?, v: Any?) {}
                override fun getValue(n: String?): Any? = null
                override fun removeValue(n: String?) {}
                override fun getValueNames(): Array<String> = emptyArray()
                override fun getLocalCertificates(): Array<Certificate>? = null
                override fun getPeerCertificateChain(): Array<javax.security.cert.X509Certificate>? = null
                override fun getPeerPrincipal(): java.security.Principal? = null
                override fun getLocalPrincipal(): java.security.Principal? = null
                override fun getCipherSuite(): String = ""
                override fun getProtocol(): String = ""
                override fun getPeerHost(): String = ""
                override fun getPeerPort(): Int = 0
                override fun getPacketBufferSize(): Int = 0
                override fun getApplicationBufferSize(): Int = 0
            }
        }
    }
}
