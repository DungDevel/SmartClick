package com.example.smartclick.app.tvconnection.cast.google

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
import java.io.IOException
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

class GoogleCastProtocolClientTest {

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
                try {
                    socket.close()
                } catch (_: Exception) {}
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
        try {
            serverSocket.close()
        } catch (_: Exception) {}
    }

    @Test(timeout = 10000)
    fun `protocol client can connect and get status`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // 1. Receive CONNECT
                val connectMsg = CastV2Framing.readFrame(input)
                assertEquals("urn:x-cast:com.google.cast.tp.connection", connectMsg.namespace)
                assertEquals("{\"type\": \"CONNECT\"}", connectMsg.payloadUtf8)

                // 2. Wait for GET_STATUS
                val statusReq = CastV2Framing.readFrame(input)
                assertEquals("urn:x-cast:com.google.cast.receiver", statusReq.namespace)
                assertTrue(statusReq.payloadUtf8.contains("GET_STATUS"))

                val reqId = JSONObject(statusReq.payloadUtf8).optInt("requestId")

                // 3. Send RECEIVER_STATUS
                val statusResp = CastMessage.newBuilder()
                    .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                    .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                    .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                    .setNamespace("urn:x-cast:com.google.cast.receiver")
                    .setPayloadType(CastMessage.PayloadType.STRING)
                    .setPayloadUtf8("{\"type\": \"RECEIVER_STATUS\", \"requestId\": $reqId, \"volume\": 50, \"applications\": []}")
                    .build()
                CastV2Framing.writeFrame(output, statusResp)
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

        try {
            val connectResult = session.connect()
            assertTrue(connectResult.isSuccess)
            client.connectToReceiver()
            val status = client.getStatus()

            assertNotNull(status)
            assertEquals(50, status?.optInt("volume"))
        } finally {
            session.disconnect()
            client.close()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `protocol client can launch app and get status update`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            try {
                while (isActive) {
                    val msg = CastV2Framing.readFrame(input)
                    if (msg.payloadUtf8.contains("LAUNCH")) {
                        val reqId = JSONObject(msg.payloadUtf8).optInt("requestId")
                        val appId = JSONObject(msg.payloadUtf8).optString("appId")

                        val launchResp = CastMessage.newBuilder()
                            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                            .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                            .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                            .setNamespace("urn:x-cast:com.google.cast.receiver")
                            .setPayloadType(CastMessage.PayloadType.STRING)
                            .setPayloadUtf8("{\"type\": \"RECEIVER_STATUS\", \"requestId\": $reqId, \"applications\": [{\"appId\": \"$appId\", \"transportId\": \"web-123\"}]}")
                            .build()
                        CastV2Framing.writeFrame(output, launchResp)
                        break
                    }
                }
            } catch (e: Exception) {
                // Expected during disconnect
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

        try {
            val connectResult = session.connect()
            assertTrue(connectResult.isSuccess)
            val status = client.launchApp("com.example.smartclick")

            assertNotNull(status)
            val apps = status?.optJSONArray("applications")
            assertEquals(1, apps?.length())
            assertEquals("com.example.smartclick", apps?.getJSONObject(0)?.optString("appId"))
        } finally {
            session.disconnect()
            client.close()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `launchErrorHandling triggers failure`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                val msg = CastV2Framing.readFrame(input)
                if (msg.payloadUtf8.contains("LAUNCH")) {
                    val reqId = JSONObject(msg.payloadUtf8).optInt("requestId")

                    val errResp = CastMessage.newBuilder()
                        .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                        .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                        .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                        .setNamespace("urn:x-cast:com.google.cast.receiver")
                        .setPayloadType(CastMessage.PayloadType.STRING)
                        .setPayloadUtf8("{\"type\": \"LAUNCH_ERROR\", \"requestId\": $reqId, \"reason\": \"NOT_FOUND\"}")
                        .build()
                    CastV2Framing.writeFrame(output, errResp)
                }
            } catch (e: Exception) {
                // Expected during disconnect
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

        try {
            val connectResult = session.connect()
            assertTrue(connectResult.isSuccess)
            var caughtException: IOException? = null
            try {
                client.launchApp("com.bad.app")
            } catch (e: IOException) {
                caughtException = e
            }

            assertNotNull("Expected IOException for LAUNCH_ERROR", caughtException)
            assertTrue(caughtException!!.message!!.contains("NOT_FOUND"))
        } finally {
            session.disconnect()
            client.close()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `launch success via broadcast RECEIVER_STATUS`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                val msg = CastV2Framing.readFrame(input)
                if (msg.payloadUtf8.contains("LAUNCH")) {
                    val appId = JSONObject(msg.payloadUtf8).optString("appId")

                    // Send broadcast RECEIVER_STATUS with requestId = 0 and actual app running
                    val resp = CastMessage.newBuilder()
                        .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                        .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                        .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                        .setNamespace("urn:x-cast:com.google.cast.receiver")
                        .setPayloadType(CastMessage.PayloadType.STRING)
                        .setPayloadUtf8("{\"type\": \"RECEIVER_STATUS\", \"requestId\": 0, \"applications\": [{\"appId\": \"$appId\", \"transportId\": \"web-123\"}]}")
                        .build()
                    CastV2Framing.writeFrame(output, resp)
                }
            } catch (e: Exception) {
                // Expected during disconnect
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

        try {
            val connectResult = session.connect()
            assertTrue(connectResult.isSuccess)
            val status = client.launchApp("com.broadcast.app")

            assertNotNull(status)
            assertEquals("com.broadcast.app", status?.optJSONArray("applications")?.getJSONObject(0)?.optString("appId"))
        } finally {
            session.disconnect()
            client.close()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `setVolume sends SET_VOLUME payload`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                val msg = CastV2Framing.readFrame(input)
                val payload = JSONObject(msg.payloadUtf8)
                assertEquals("SET_VOLUME", payload.optString("type"))
                assertEquals(0.5, payload.optJSONObject("volume")?.optDouble("level") ?: 0.0, 0.01)

                val reqId = payload.optInt("requestId")
                val resp = CastMessage.newBuilder()
                    .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                    .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                    .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                    .setNamespace("urn:x-cast:com.google.cast.receiver")
                    .setPayloadType(CastMessage.PayloadType.STRING)
                    .setPayloadUtf8("{\"type\": \"RECEIVER_STATUS\", \"requestId\": $reqId, \"status\": {\"volume\": {\"level\": 0.5}}}")
                    .build()
                CastV2Framing.writeFrame(output, resp)
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val result = client.setVolume(0.5f)
            assertTrue(result.isSuccess)
        } finally {
            client.close()
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `setMute sends SET_VOLUME with muted flag`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                val msg = CastV2Framing.readFrame(input)
                val payload = JSONObject(msg.payloadUtf8)
                assertEquals("SET_VOLUME", payload.optString("type"))
                assertTrue(payload.optJSONObject("volume")?.optBoolean("muted") == true)

                val reqId = payload.optInt("requestId")
                val resp = CastMessage.newBuilder()
                    .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                    .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                    .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                    .setNamespace("urn:x-cast:com.google.cast.receiver")
                    .setPayloadType(CastMessage.PayloadType.STRING)
                    .setPayloadUtf8("{\"type\": \"RECEIVER_STATUS\", \"requestId\": $reqId, \"status\": {\"volume\": {\"muted\": true}}}")
                    .build()
                CastV2Framing.writeFrame(output, resp)
            } catch (_: Exception) {}
        }

        try {
            session.connect()
            val result = client.setMute(true)
            assertTrue(result.isSuccess)
        } finally {
            client.close()
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `launch skips wrong appId and waits for correct one`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                val msg = CastV2Framing.readFrame(input)
                if (msg.payloadUtf8.contains("LAUNCH")) {

                    // 1. Send status with wrong app
                    val wrongResp = CastMessage.newBuilder()
                        .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                        .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                        .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                        .setNamespace("urn:x-cast:com.google.cast.receiver")
                        .setPayloadType(CastMessage.PayloadType.STRING)
                        .setPayloadUtf8("{\"type\": \"RECEIVER_STATUS\", \"requestId\": 0, \"applications\": [{\"appId\": \"wrong.app\"}]}")
                        .build()
                    CastV2Framing.writeFrame(output, wrongResp)

                    delay(100)

                    // 2. Send status with correct app
                    val correctResp = CastMessage.newBuilder()
                        .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                        .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                        .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                        .setNamespace("urn:x-cast:com.google.cast.receiver")
                        .setPayloadType(CastMessage.PayloadType.STRING)
                        .setPayloadUtf8("{\"type\": \"RECEIVER_STATUS\", \"requestId\": 0, \"applications\": [{\"appId\": \"correct.app\"}]}")
                        .build()
                    CastV2Framing.writeFrame(output, correctResp)
                }
            } catch (e: Exception) {
                // Expected during disconnect
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

        try {
            val connectResult = session.connect()
            assertTrue(connectResult.isSuccess)
            val status = client.launchApp("correct.app")

            assertNotNull(status)
            assertEquals("correct.app", status?.optJSONArray("applications")?.getJSONObject(0)?.optString("appId"))
        } finally {
            session.disconnect()
            client.close()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `connectToApp validates transportId`() {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, GlobalScope)

        var caught: IllegalArgumentException? = null
        try {
            runBlocking {
                client.connectToApp("")
            }
        } catch (e: IllegalArgumentException) {
            caught = e
        }

        assertNotNull(caught)
    }

    @Test(timeout = 10000)
    fun `manual disconnect cleanup`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        try {
            val connectResult = session.connect()
            assertTrue(connectResult.isSuccess)
            client.connectToReceiver()
        } finally {
            session.disconnect()
            client.close()
            closeAcceptedSockets()
        }
    }

    @Test(timeout = 10000)
    fun `client close cleanup`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        try {
            val connectResult = session.connect()
            assertTrue(connectResult.isSuccess)
            client.connectToReceiver()
            client.startHeartbeat()
        } finally {
            client.close()
            session.disconnect()
            closeAcceptedSockets()
        }
    }

    @Test(timeout = 10000)
    fun `heartbeat timeout triggers disconnect`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        try {
            val connectResult = session.connect()
            assertTrue(connectResult.isSuccess)
            client.startHeartbeat(intervalMs = 100, timeoutNanos = 400_000_000L)

            withTimeout(5000) {
                while (session.connectionState.value != GoogleCastSession.State.DISCONNECTED &&
                       session.connectionState.value != GoogleCastSession.State.FAILED) {
                    delay(50)
                }
            }
            assertTrue(
                session.connectionState.value == GoogleCastSession.State.DISCONNECTED ||
                session.connectionState.value == GoogleCastSession.State.FAILED
            )
        } finally {
            client.close()
            session.disconnect()
            closeAcceptedSockets()
        }
    }

    @Test(timeout = 10000)
    fun `early message arrival before collector registers is preserved and delivered`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)

        val earlyMsg = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
            .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
            .setNamespace("urn:x-cast:com.google.cast.media")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8("EARLY_BUFFERED_MESSAGE")
            .build()

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val output = socket.getOutputStream()
                // Emit message immediately upon connection before any client collector subscribes
                CastV2Framing.writeFrame(output, earlyMsg)
                while (isActive) {
                    delay(50)
                }
            } catch (e: Exception) {
                // Expected during disconnect
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

        try {
            val connectResult = session.connect()
            assertTrue(connectResult.isSuccess)

            // Small delay to ensure readLoop reads and buffers the message before we start collecting
            delay(100)

            // Collector registers after the message has arrived; must receive the buffered message due to replay = 1
            val received = session.incomingMessages.first()
            assertEquals("EARLY_BUFFERED_MESSAGE", received.payloadUtf8)
        } finally {
            session.disconnect()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
    }

    @Test(timeout = 10000)
    fun `stale or unrelated replayed message cannot satisfy new pending request`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, authManager)
        val client = GoogleCastProtocolClient(session, this)

        val serverJob = launch(Dispatchers.IO) {
            val socket = acceptSocket()
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // 1. Send stale message with unrelated requestId = 999
                val staleResp = CastMessage.newBuilder()
                    .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                    .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                    .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                    .setNamespace("urn:x-cast:com.google.cast.receiver")
                    .setPayloadType(CastMessage.PayloadType.STRING)
                    .setPayloadUtf8("{\"type\": \"RECEIVER_STATUS\", \"requestId\": 999, \"volume\": 10, \"applications\": []}")
                    .build()
                CastV2Framing.writeFrame(output, staleResp)

                // 2. Also send broadcast with requestId = 0
                val broadcastResp = CastMessage.newBuilder()
                    .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                    .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                    .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                    .setNamespace("urn:x-cast:com.google.cast.receiver")
                    .setPayloadType(CastMessage.PayloadType.STRING)
                    .setPayloadUtf8("{\"type\": \"RECEIVER_STATUS\", \"requestId\": 0, \"volume\": 20, \"applications\": []}")
                    .build()
                CastV2Framing.writeFrame(output, broadcastResp)

                // 3. Await GET_STATUS request from client
                val statusReq = CastV2Framing.readFrame(input)
                val expectedReqId = JSONObject(statusReq.payloadUtf8).optInt("requestId")

                // 4. Send legitimate response matching expected requestId
                val legitimateResp = CastMessage.newBuilder()
                    .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                    .setSourceId(GoogleCastProtocolClient.RECEIVER_ID)
                    .setDestinationId(GoogleCastProtocolClient.SENDER_ID)
                    .setNamespace("urn:x-cast:com.google.cast.receiver")
                    .setPayloadType(CastMessage.PayloadType.STRING)
                    .setPayloadUtf8("{\"type\": \"RECEIVER_STATUS\", \"requestId\": $expectedReqId, \"volume\": 95, \"applications\": []}")
                    .build()
                CastV2Framing.writeFrame(output, legitimateResp)
            } catch (e: Exception) {
                // Expected during disconnect
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

        try {
            val connectResult = session.connect()
            assertTrue(connectResult.isSuccess)

            // Pending request must only be completed by the response matching expectedReqId
            val status = client.getStatus()
            assertNotNull(status)
            assertEquals("Pending request must only be completed by matching requestId response", 95, status?.optInt("volume"))
        } finally {
            session.disconnect()
            client.close()
            closeAcceptedSockets()
            serverJob.cancelAndJoin()
        }
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
