package com.example.smartclick.app.tvconnection.cast.google

import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleCastSessionTest {

    private lateinit var mockServer: ServerSocket
    private var serverPort = 0
    private var serverAcceptedSocket: Socket? = null

    private lateinit var mockSslSocketFactory: SSLSocketFactory

    @Before
    fun setup() {
        mockServer = ServerSocket(0)
        serverPort = mockServer.localPort

        // Create a mock auth manager and trust manager for session tests
        val mockTrustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val authManager = GoogleCastAuthManager(mockTrustManager)

        mockSslSocketFactory = object : SSLSocketFactory() {
            override fun getDefaultCipherSuites(): Array<String> = emptyArray()
            override fun getSupportedCipherSuites(): Array<String> = emptyArray()

            override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
                return MockSSLSocket(s)
            }

            override fun createSocket(host: String, port: Int): Socket {
                val s = Socket(host, port)
                return MockSSLSocket(s)
            }

            override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
                val s = Socket(host, port, localHost, localPort)
                return MockSSLSocket(s)
            }

            override fun createSocket(host: InetAddress, port: Int): Socket {
                val s = Socket(host, port)
                return MockSSLSocket(s)
            }

            override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
                val s = Socket(address, port, localAddress, localPort)
                return MockSSLSocket(s)
            }

            override fun createSocket(): Socket {
                val s = Socket()
                return MockSSLSocket(s)
            }
        }

        // Store authManager to pass to sessions
        this.currentAuthManager = authManager
    }

    private lateinit var currentAuthManager: GoogleCastAuthManager

    @After
    fun tearDown() {
        serverAcceptedSocket?.close()
        mockServer.close()
    }

    @Test
    fun `session transitions to CONNECTED upon successful connection`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, currentAuthManager)

        launch(Dispatchers.IO) {
            serverAcceptedSocket = mockServer.accept()
        }

        val result = session.connect()
        assertTrue(result.isSuccess)
        assertEquals(GoogleCastSession.State.CONNECTED, session.connectionState.value)

        session.disconnect()
        assertEquals(GoogleCastSession.State.DISCONNECTED, session.connectionState.value)
    }

    @Test
    fun `session transitions to FAILED upon connection error`() = runBlocking {
        // Close mock server so connection is refused
        mockServer.close()
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, currentAuthManager)

        val result = session.connect()
        assertTrue(result.isFailure)
        assertEquals(GoogleCastSession.State.FAILED, session.connectionState.value)
    }

    @Test
    fun `send message writes framed protobuf to output stream`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, currentAuthManager)

        launch(Dispatchers.IO) {
            serverAcceptedSocket = mockServer.accept()
        }

        session.connect()

        val outMessage = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId("sender")
            .setDestinationId("receiver")
            .setNamespace("urn:x-cast:com.google.cast.media")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8("TEST_PAYLOAD")
            .build()

        session.sendMessage(outMessage)

        val serverIn = serverAcceptedSocket!!.getInputStream()
        val receivedMessage = CastV2Framing.readFrame(serverIn)

        assertEquals("sender", receivedMessage.sourceId)
        assertEquals("receiver", receivedMessage.destinationId)
        assertEquals("TEST_PAYLOAD", receivedMessage.payloadUtf8)

        session.disconnect()
    }

    @Test
    fun `read loop emits messages to incoming flow`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, currentAuthManager)
        
        val serverJob = launch(Dispatchers.IO) {
            val accepted = mockServer.accept()
            serverAcceptedSocket = accepted
            val serverOut = accepted.getOutputStream()
            val inMessage = CastMessage.newBuilder()
                .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                .setSourceId("receiver")
                .setDestinationId("sender")
                .setNamespace("urn:x-cast:com.google.cast.media")
                .setPayloadType(CastMessage.PayloadType.STRING)
                .setPayloadUtf8("SERVER_MSG")
                .build()

            CastV2Framing.writeFrame(serverOut, inMessage)
        }

        session.connect()

        val received = session.incomingMessages.first()
        assertEquals("SERVER_MSG", received.payloadUtf8)

        session.disconnect()
        serverJob.cancelAndJoin()
    }

    @Test
    fun `EOF terminates read loop and disconnected state safely`() = runBlocking {
        val session = GoogleCastSession("127.0.0.1", serverPort, mockSslSocketFactory, currentAuthManager)
        
        launch(Dispatchers.IO) {
            serverAcceptedSocket = mockServer.accept()
            delay(100) // let it connect
            serverAcceptedSocket!!.close() // induce EOF
        }

        session.connect()
        assertEquals(GoogleCastSession.State.CONNECTED, session.connectionState.value)

        // Wait for EOF exception to process in the loop
        var attempts = 0
        while (session.connectionState.value != GoogleCastSession.State.DISCONNECTED && attempts < 20) {
            delay(100)
            attempts++
        }
        assertEquals("Session should have transitioned to DISCONNECTED after EOF", GoogleCastSession.State.DISCONNECTED, session.connectionState.value)
    }

    // A minimal MockSSLSocket wrapper around a standard Socket for testing
    private class MockSSLSocket(private val delegate: Socket) : SSLSocket() {
        override fun startHandshake() {}
        override fun connect(endpoint: SocketAddress?) = delegate.connect(endpoint)
        override fun connect(endpoint: SocketAddress?, timeout: Int) = delegate.connect(endpoint, timeout)
        override fun getInputStream(): InputStream = delegate.getInputStream()
        override fun getOutputStream(): OutputStream = delegate.getOutputStream()
        override fun close() = delegate.close()

        override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener?) {}
        override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener?) {}
        override fun getUseClientMode(): Boolean = true
        override fun setUseClientMode(mode: Boolean) {}
        override fun getNeedClientAuth(): Boolean = false
        override fun setNeedClientAuth(need: Boolean) {}
        override fun getWantClientAuth(): Boolean = false
        override fun setWantClientAuth(want: Boolean) {}
        override fun getEnableSessionCreation(): Boolean = true
        override fun setEnableSessionCreation(flag: Boolean) {}

        override fun getSupportedCipherSuites(): Array<String> = emptyArray()
        override fun getEnabledCipherSuites(): Array<String> = emptyArray()
        override fun setEnabledCipherSuites(suites: Array<String>?) {}
        override fun getSupportedProtocols(): Array<String> = emptyArray()
        override fun getEnabledProtocols(): Array<String> = emptyArray()
        override fun setEnabledProtocols(protocols: Array<String>?) {}
        override fun getSession(): SSLSession {
            return object : SSLSession {
                override fun getPeerCertificates(): Array<java.security.cert.Certificate> {
                    val cert = io.mockk.mockk<java.security.cert.X509Certificate>(relaxed = true)
                    io.mockk.every { cert.encoded } returns ByteArray(100) { 1 }
                    return arrayOf(cert)
                }
                override fun getId(): ByteArray = ByteArray(0)
                override fun getSessionContext(): javax.net.ssl.SSLSessionContext? = null
                override fun getCreationTime(): Long = 0
                override fun getLastAccessedTime(): Long = 0
                override fun invalidate() {}
                override fun isValid(): Boolean = true
                override fun putValue(name: String?, value: Any?) {}
                override fun getValue(name: String?): Any? = null
                override fun removeValue(name: String?) {}
                override fun getValueNames(): Array<String> = emptyArray()
                override fun getLocalCertificates(): Array<java.security.cert.Certificate>? = null
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
