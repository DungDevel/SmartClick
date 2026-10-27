package com.example.smartclick.app.tvconnection.cast.google

import android.net.Uri
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.app.domain.model.CastMediaType
import com.example.smartclick.app.tvconnection.cast.CastMediaSession
import com.example.smartclick.app.tvconnection.cast.LocalMediaServer
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.DeviceAuthMessage
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthResponse
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthChallenge
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import io.mockk.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.Date
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class GoogleCastControllerTest {

    private lateinit var serverSocket: ServerSocket
    private var serverPort = 0
    private lateinit var mockSslSocketFactory: SSLSocketFactory
    private lateinit var authManager: GoogleCastAuthManager
    private lateinit var mockLocalMediaServer: LocalMediaServer
    private val acceptedSockets = Collections.synchronizedList(mutableListOf<Socket>())

    private lateinit var testKeyPair: KeyPair
    private lateinit var testCert: X509Certificate
    private lateinit var testCertDer: ByteArray

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
        org.bouncycastle.jce.provider.BouncyCastleProvider().let {
            if (java.security.Security.getProvider(it.name) == null) {
                java.security.Security.addProvider(it)
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        testKeyPair = kpg.generateKeyPair()

        val issuer = X500Name("CN=TestCastDevice")
        val serialNumber = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24)
        val notAfter = Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 2)

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer, serialNumber, notBefore, notAfter, issuer, testKeyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(testKeyPair.private)
        testCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certBuilder.build(signer))
        testCertDer = testCert.encoded

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        acceptedSockets.clear()
        serverSocket = ServerSocket(0)
        serverPort = serverSocket.localPort

        val mockTrustManager = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        authManager = GoogleCastAuthManager(mockTrustManager)
        mockLocalMediaServer = mockk(relaxed = true)

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
        unmockkStatic(Uri::class)
        closeAcceptedSockets()
        try { serverSocket.close() } catch (_: Exception) {}
    }

    /**
     * Helper to run a standard Cast V2 server that completes:
     * 1. Connect to receiver-0
     * 2. Heartbeat ping/pong loop
     * 3. Launch Default Media Receiver app -> returns RECEIVER_STATUS with transportId
     * 4. Connect to transportId
     * Returns a pair of (serverJob, transportId)
     */
    private fun CoroutineScope.runFullCastMockServer(
        transportId: String = "web-transport-42",
        onMediaMessage: (suspend (payload: JSONObject, output: OutputStream) -> Unit)? = null
    ): Job {
        return launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                while (isActive) {
                    val msg = CastV2Framing.readFrame(input)
                    when (msg.namespace) {
                        "urn:x-cast:com.google.cast.tp.deviceauth" -> {
                            try {
                                val deviceAuthMsg = DeviceAuthMessage.parseFrom(msg.payloadBinary)
                                if (deviceAuthMsg.hasChallenge()) {
                                    val challenge = deviceAuthMsg.challenge
                                    val signedData = challenge.senderNonce.toByteArray() + testCertDer
                                    val sig = Signature.getInstance("SHA256withRSA").apply {
                                        initSign(testKeyPair.private)
                                        update(signedData)
                                    }.sign()

                                    val response = AuthResponse.newBuilder()
                                        .setSenderNonce(challenge.senderNonce)
                                        .setSignature(com.google.protobuf.ByteString.copyFrom(sig))
                                        .setClientAuthCertificate(com.google.protobuf.ByteString.copyFrom(testCertDer))
                                        .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
                                        .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
                                        .build()

                                    val authMsgOut = DeviceAuthMessage.newBuilder()
                                        .setResponse(response)
                                        .build()

                                    val castMsgOut = CastMessage.newBuilder()
                                        .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                                        .setSourceId("receiver-0")
                                        .setDestinationId(msg.sourceId)
                                        .setNamespace("urn:x-cast:com.google.cast.tp.deviceauth")
                                        .setPayloadType(CastMessage.PayloadType.BINARY)
                                        .setPayloadBinary(com.google.protobuf.ByteString.copyFrom(authMsgOut.toByteArray()))
                                        .build()

                                    CastV2Framing.writeFrame(output, castMsgOut)
                                }
                            } catch (_: Exception) {}
                        }
                        else -> {
                            val payload = if (msg.hasPayloadUtf8() && msg.payloadUtf8.isNotEmpty()) {
                                try { JSONObject(msg.payloadUtf8) } catch (_: Exception) { JSONObject() }
                            } else {
                                JSONObject()
                            }
                            val type = payload.optString("type")

                            when (msg.namespace) {
                                "urn:x-cast:com.google.cast.tp.connection" -> {
                                    // CONNECT message received, no reply needed
                                }
                                "urn:x-cast:com.google.cast.tp.heartbeat" -> {
                                    if (type == "PING") {
                                        val pong = CastMessage.newBuilder()
                                            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                                            .setSourceId("receiver-0")
                                            .setDestinationId(msg.sourceId)
                                            .setNamespace("urn:x-cast:com.google.cast.tp.heartbeat")
                                            .setPayloadType(CastMessage.PayloadType.STRING)
                                            .setPayloadUtf8("""{"type":"PONG"}""")
                                            .build()
                                        CastV2Framing.writeFrame(output, pong)
                                    }
                                }
                                "urn:x-cast:com.google.cast.receiver" -> {
                                    if (type == "LAUNCH") {
                                        val reqId = payload.optInt("requestId")
                                        val appsArray = JSONArray().put(JSONObject().apply {
                                            put("appId", GoogleCastController.DEFAULT_MEDIA_RECEIVER_APP_ID)
                                            put("displayName", "Default Media Receiver")
                                            put("sessionId", "cast-session-1")
                                            put("transportId", transportId)
                                            put("statusText", "Ready")
                                        })
                                        val receiverStatus = JSONObject().apply {
                                            put("type", "RECEIVER_STATUS")
                                            put("requestId", reqId)
                                            put("applications", appsArray)
                                            put("status", JSONObject().apply {
                                                put("applications", appsArray)
                                            })
                                        }

                                        val statusMsg = CastMessage.newBuilder()
                                            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                                            .setSourceId("receiver-0")
                                            .setDestinationId(msg.sourceId)
                                            .setNamespace("urn:x-cast:com.google.cast.receiver")
                                            .setPayloadType(CastMessage.PayloadType.STRING)
                                            .setPayloadUtf8(receiverStatus.toString())
                                            .build()
                                        CastV2Framing.writeFrame(output, statusMsg)
                                    }
                                }
                                GoogleCastMediaController.MEDIA_NAMESPACE -> {
                                    onMediaMessage?.invoke(payload, output)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    @Test(timeout = 10000)
    fun `castMedia with remote URL establishes session and loads media`() = runBlocking {
        val transportId = "test-transport-1"

        val serverJob = runFullCastMockServer(transportId) { payload, output ->
            if (payload.optString("type") == "LOAD") {
                val reqId = payload.optInt("requestId")
                val statusMsg = buildMediaStatusMessage(transportId, reqId, "PLAYING", mediaSessionId = 100)
                CastV2Framing.writeFrame(output, statusMsg)
            }
        }

        val controller = GoogleCastController(
            ipAddress = "127.0.0.1",
            port = serverPort,
            localMediaServer = mockLocalMediaServer,
            sslSocketFactory = mockSslSocketFactory,
            authManager = authManager
        )

        try {
            val result = controller.castMedia(
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                mimeType = "video/mp4",
                type = CastMediaType.VIDEO,
                title = "Big Buck Bunny"
            )

            assertTrue(result.isSuccess)
            assertEquals("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", controller.castSessionInfo.value.currentUrl)
            assertEquals(CastPlaybackState.PLAYING, controller.castPlaybackState.value)
        } finally {
            controller.teardown()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `castMedia with content URI routes through LocalMediaServer`() = runBlocking {
        val transportId = "test-transport-local"
        val mockServedUrl = "http://192.168.1.50:8080/media/abc-123?token=xyz"

        coEvery { mockLocalMediaServer.registerMedia(any(), any(), any()) } returns CastMediaSession(
            sessionId = "abc-123",
            token = "xyz",
            mediaUrl = mockServedUrl,
            uri = mockk(relaxed = true),
            mimeType = "video/mp4",
            title = "My Local Video"
        )

        var loadedContentId = ""
        val serverJob = runFullCastMockServer(transportId) { payload, output ->
            if (payload.optString("type") == "LOAD") {
                loadedContentId = payload.optJSONObject("media")?.optString("contentId") ?: ""
                val reqId = payload.optInt("requestId")
                val statusMsg = buildMediaStatusMessage(transportId, reqId, "BUFFERING", mediaSessionId = 1)
                CastV2Framing.writeFrame(output, statusMsg)
            }
        }

        val controller = GoogleCastController(
            ipAddress = "127.0.0.1",
            port = serverPort,
            localMediaServer = mockLocalMediaServer,
            sslSocketFactory = mockSslSocketFactory,
            authManager = authManager
        )

        try {
            val result = controller.castMedia(
                url = "content://media/external/video/media/42",
                mimeType = "video/mp4",
                type = CastMediaType.VIDEO,
                title = "My Local Video"
            )

            assertTrue(result.isSuccess)
            assertEquals(mockServedUrl, loadedContentId)
            assertEquals(mockServedUrl, controller.castSessionInfo.value.currentUrl)
            coVerify(exactly = 1) { mockLocalMediaServer.registerMedia(any(), "video/mp4", "My Local Video") }
        } finally {
            controller.teardown()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `controlCast fails when no active media session exists`() = runBlocking {
        val controller = GoogleCastController(
            ipAddress = "127.0.0.1",
            port = serverPort,
            localMediaServer = mockLocalMediaServer,
            sslSocketFactory = mockSslSocketFactory,
            authManager = authManager
        )

        val result = controller.controlCast(CastCommand.Play)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CastError.ProtocolCommandFailed)
    }

    @Test(timeout = 10000)
    fun `controlCast delegates Play, Pause, Stop, Seek correctly`() = runBlocking {
        val transportId = "test-transport-commands"
        val receivedCommands = Collections.synchronizedList(mutableListOf<String>())
        var lastSeekTime = -1.0

        val serverJob = runFullCastMockServer(transportId) { payload, output ->
            val type = payload.optString("type")
            val reqId = payload.optInt("requestId")
            receivedCommands.add(type)

            when (type) {
                "LOAD" -> {
                    CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, reqId, "PLAYING", mediaSessionId = 10, currentTime = 10.0))
                }
                "PLAY" -> {
                    CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, reqId, "PLAYING", mediaSessionId = 10))
                }
                "PAUSE" -> {
                    CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, reqId, "PAUSED", mediaSessionId = 10))
                }
                "STOP" -> {
                    CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, reqId, "IDLE", mediaSessionId = 10, idleReason = "CANCELLED"))
                }
                "SEEK" -> {
                    lastSeekTime = payload.optDouble("currentTime", -1.0)
                    CastV2Framing.writeFrame(output, buildMediaStatusMessage(transportId, reqId, "PLAYING", mediaSessionId = 10, currentTime = lastSeekTime))
                }
            }
        }

        val controller = GoogleCastController(
            ipAddress = "127.0.0.1",
            port = serverPort,
            localMediaServer = mockLocalMediaServer,
            sslSocketFactory = mockSslSocketFactory,
            authManager = authManager
        )

        try {
            // First load media to establish session
            controller.castMedia("https://x.com/v.mp4", "video/mp4", CastMediaType.VIDEO, "V")

            // Play
            val playResult = controller.controlCast(CastCommand.Play)
            assertTrue(playResult.isSuccess)

            // Pause
            val pauseResult = controller.controlCast(CastCommand.Pause)
            assertTrue(pauseResult.isSuccess)
            withTimeout(2000) {
                while (controller.castPlaybackState.value != CastPlaybackState.PAUSED) {
                    delay(10)
                }
            }
            assertEquals(CastPlaybackState.PAUSED, controller.castPlaybackState.value)

            // SeekTo (ms -> s)
            val seekResult = controller.controlCast(CastCommand.SeekTo(45000L))
            assertTrue(seekResult.isSuccess)
            assertEquals(45.0, lastSeekTime, 0.01)

            // SeekBy (offset)
            val seekByResult = controller.controlCast(CastCommand.SeekBy(10000L))
            assertTrue(seekByResult.isSuccess)
            assertEquals(55.0, lastSeekTime, 0.01)

            // Stop
            val stopResult = controller.controlCast(CastCommand.Stop)
            assertTrue(stopResult.isSuccess)
            withTimeout(2000) {
                while (controller.castPlaybackState.value != CastPlaybackState.STOPPED) {
                    delay(10)
                }
            }
            assertEquals(CastPlaybackState.STOPPED, controller.castPlaybackState.value)

            assertTrue(receivedCommands.contains("PLAY"))
            assertTrue(receivedCommands.contains("PAUSE"))
            assertTrue(receivedCommands.contains("SEEK"))
            assertTrue(receivedCommands.contains("STOP"))
        } finally {
            controller.teardown()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 5000)
    fun `controlCast returns ProtocolCommandFailed when not connected for volume controls`() = runBlocking {
        val controller = GoogleCastController(
            ipAddress = "127.0.0.1",
            port = serverPort,
            localMediaServer = mockLocalMediaServer,
            sslSocketFactory = mockSslSocketFactory,
            authManager = authManager
        )

        // Volume is supported, but fails when not connected
        val volResult = controller.controlCast(CastCommand.SetVolume(50))
        assertTrue(volResult.isFailure)
        assertTrue(volResult.exceptionOrNull() is CastError.ProtocolCommandFailed)

        val muteResult = controller.controlCast(CastCommand.Mute)
        assertTrue(muteResult.isFailure)
        assertTrue(muteResult.exceptionOrNull() is CastError.ProtocolCommandFailed)
    }

    @Test(timeout = 10000)
    fun `castMedia returns NetworkError if connection fails`() = runBlocking {
        // Use an unopened port
        val unusedPort = serverPort
        serverSocket.close()

        val controller = GoogleCastController(
            ipAddress = "127.0.0.1",
            port = unusedPort,
            localMediaServer = mockLocalMediaServer,
            sslSocketFactory = mockSslSocketFactory,
            authManager = authManager
        )

        val result = controller.castMedia("https://x.com/v.mp4", "video/mp4", CastMediaType.VIDEO, "V")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CastError.NetworkError)
    }

    @Test(timeout = 10000)
    fun `teardown closes session and resets states`() = runBlocking {
        val transportId = "test-transport-teardown"

        val serverJob = runFullCastMockServer(transportId) { payload, output ->
            if (payload.optString("type") == "LOAD") {
                val reqId = payload.optInt("requestId")
                val statusMsg = buildMediaStatusMessage(transportId, reqId, "PLAYING", mediaSessionId = 1)
                CastV2Framing.writeFrame(output, statusMsg)
            }
        }

        val controller = GoogleCastController(
            ipAddress = "127.0.0.1",
            port = serverPort,
            localMediaServer = mockLocalMediaServer,
            sslSocketFactory = mockSslSocketFactory,
            authManager = authManager
        )

        try {
            controller.castMedia("https://x.com/v.mp4", "video/mp4", CastMediaType.VIDEO, "V")
            assertEquals(CastPlaybackState.PLAYING, controller.castPlaybackState.value)

            val teardownResult = controller.teardown()
            assertTrue(teardownResult.isSuccess)

            // State reset to IDLE and empty session info
            assertEquals(CastPlaybackState.IDLE, controller.castPlaybackState.value)
            assertEquals("", controller.castSessionInfo.value.currentUrl)

            // Subsequent command fails
            val cmdResult = controller.controlCast(CastCommand.Play)
            assertTrue(cmdResult.isFailure)
        } finally {
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    private fun buildMediaStatusMessage(
        transportId: String,
        requestId: Int,
        playerState: String,
        mediaSessionId: Int = 0,
        currentTime: Double = Double.NaN,
        idleReason: String? = null
    ): CastMessage {
        val statusObj = JSONObject().apply {
            put("playerState", playerState)
            if (mediaSessionId > 0) put("mediaSessionId", mediaSessionId)
            if (!currentTime.isNaN()) put("currentTime", currentTime)
            if (idleReason != null) put("idleReason", idleReason)
            put("media", JSONObject().apply {
                put("duration", 120.0)
            })
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

    private inner class MockSSLSocket(private val delegate: Socket) : SSLSocket() {
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
                    return arrayOf(testCert)
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
