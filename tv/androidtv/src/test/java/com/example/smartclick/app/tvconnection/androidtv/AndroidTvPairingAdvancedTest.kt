package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.connection.PairingFailure
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.tvconnection.androidtv.proto.PoloProto
import com.example.smartclick.app.tvconnection.androidtv.proto.PoloProto.OuterMessage
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.net.ServerSocket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class AndroidTvPairingAdvancedTest {

    @Before
    fun setup() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = generateCertificate("CN=MockClient", kp)
        mockkObject(AndroidTvCertificateManager)
        every { AndroidTvCertificateManager.getOrGenerateClientKeyAndCertificate() } returns Pair(kp.private, cert)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private class MockPoloServer {
        var port: Int = 0
            private set
        private var serverSocket: ServerSocket? = null
        private val scope = CoroutineScope(Dispatchers.IO + Job())

        fun start() {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val cert = generateCertificate("CN=MockPolo", kp)
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry("server", kp.private, "pwd".toCharArray(), arrayOf(cert))
            }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(ks, "pwd".toCharArray())
            }
            val ctx = SSLContext.getInstance("TLSv1.2").apply {
                init(kmf.keyManagers, null, SecureRandom())
            }
            serverSocket = ctx.serverSocketFactory.createServerSocket(0).apply {
                port = localPort
            }

            scope.launch {
                while (isActive) {
                    try {
                        val socket = serverSocket?.accept() as? SSLSocket ?: break
                        socket.use {
                            it.startHandshake()
                            val input = it.inputStream
                            val output = it.outputStream

                            // 1. PairingRequest
                            val req = OuterMessage.parseDelimitedFrom(input) ?: return@use
                            if (req.hasPairingRequest()) {
                                OuterMessage.newBuilder()
                                    .setProtocolVersion(2)
                                    .setStatus(OuterMessage.Status.STATUS_OK)
                                    .setPairingRequestAck(PoloProto.PairingRequestAck.getDefaultInstance())
                                    .build()
                                    .writeDelimitedTo(output)
                                output.flush()
                            }

                            // 2. Options
                            val opts = OuterMessage.parseDelimitedFrom(input) ?: return@use
                            if (opts.hasOptions()) {
                                OuterMessage.newBuilder()
                                    .setProtocolVersion(2)
                                    .setStatus(OuterMessage.Status.STATUS_OK)
                                    .setOptions(opts.options)
                                    .build()
                                    .writeDelimitedTo(output)
                                output.flush()
                            }

                            // 3. Configuration
                            val cfg = OuterMessage.parseDelimitedFrom(input) ?: return@use
                            if (cfg.hasConfiguration()) {
                                OuterMessage.newBuilder()
                                    .setProtocolVersion(2)
                                    .setStatus(OuterMessage.Status.STATUS_OK)
                                    .setConfigurationAck(PoloProto.ConfigurationAck.getDefaultInstance())
                                    .build()
                                    .writeDelimitedTo(output)
                                output.flush()
                            }

                            // 4. Secret
                            val sec = OuterMessage.parseDelimitedFrom(input) ?: return@use
                            if (sec.hasSecret()) {
                                OuterMessage.newBuilder()
                                    .setProtocolVersion(2)
                                    .setStatus(OuterMessage.Status.STATUS_OK)
                                    .setSecretAck(
                                        PoloProto.SecretAck.newBuilder()
                                            .setSecret(com.google.protobuf.ByteString.copyFrom(ByteArray(32)))
                                            .build()
                                    )
                                    .build()
                                    .writeDelimitedTo(output)
                                output.flush()
                            }
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            }
        }

        fun stop() {
            serverSocket?.close()
        }
    }

    private companion object {
        fun generateCertificate(dn: String, kp: java.security.KeyPair): X509Certificate {
            val issuer = org.bouncycastle.asn1.x500.X500Name(dn)
            val serial = BigInteger.valueOf(System.currentTimeMillis())
            val b = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                issuer, serial, Date(System.currentTimeMillis() - 86400000L),
                Date(System.currentTimeMillis() + 86400000L * 365), issuer, kp.public
            )
            val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
            return org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(b.build(signer))
        }
    }

    @Test
    fun `successful pairing handshake flow with valid hex pin`() = runTest {
        val server = MockPoloServer()
        server.start()

        val session = AndroidTvPairingSession(
            ipAddress = "127.0.0.1",
            port = server.port,
            clientName = "SmartClick",
            serviceName = "atvremote"
        )

        val steps = mutableListOf<PairingStep>()
        try {
            val job = launch {
                session.startPairing(object : FlowCollector<PairingStep> {
                    override suspend fun emit(value: PairingStep) {
                        steps.add(value)
                        if (value is PairingStep.EnterPin) {
                            session.providePin("1234AF")
                        }
                    }
                })
            }
            job.join()
        } finally {
            server.stop()
        }

        assertTrue(steps.any { it is PairingStep.EnterPin })
        assertTrue(steps.any { it is PairingStep.Succeeded })
    }

    @Test
    fun `pin shorter than 6 characters is rejected`() = runTest {
        val server = MockPoloServer()
        server.start()

        val session = AndroidTvPairingSession(
            ipAddress = "127.0.0.1",
            port = server.port,
            clientName = "SmartClick",
            serviceName = "atvremote"
        )

        val steps = mutableListOf<PairingStep>()
        try {
            val job = launch {
                session.startPairing(object : FlowCollector<PairingStep> {
                    override suspend fun emit(value: PairingStep) {
                        steps.add(value)
                        if (value is PairingStep.EnterPin) {
                            session.providePin("12345") // 5 chars
                        }
                    }
                })
            }
            job.join()
        } finally {
            server.stop()
        }

        assertTrue(steps.any { it is PairingStep.Failed && it.reason is PairingFailure.WrongPin })
    }

    @Test
    fun `pin longer than 6 characters is rejected`() = runTest {
        val server = MockPoloServer()
        server.start()

        val session = AndroidTvPairingSession(
            ipAddress = "127.0.0.1",
            port = server.port,
            clientName = "SmartClick",
            serviceName = "atvremote"
        )

        val steps = mutableListOf<PairingStep>()
        try {
            val job = launch {
                session.startPairing(object : FlowCollector<PairingStep> {
                    override suspend fun emit(value: PairingStep) {
                        steps.add(value)
                        if (value is PairingStep.EnterPin) {
                            session.providePin("1234567") // 7 chars
                        }
                    }
                })
            }
            job.join()
        } finally {
            server.stop()
        }

        assertTrue(steps.any { it is PairingStep.Failed && it.reason is PairingFailure.WrongPin })
    }

    @Test
    fun `pin with non-hex characters is rejected`() = runTest {
        val server = MockPoloServer()
        server.start()

        val session = AndroidTvPairingSession(
            ipAddress = "127.0.0.1",
            port = server.port,
            clientName = "SmartClick",
            serviceName = "atvremote"
        )

        val steps = mutableListOf<PairingStep>()
        try {
            val job = launch {
                session.startPairing(object : FlowCollector<PairingStep> {
                    override suspend fun emit(value: PairingStep) {
                        steps.add(value)
                        if (value is PairingStep.EnterPin) {
                            session.providePin("12345G") // 'G' is not hex
                        }
                    }
                })
            }
            job.join()
        } finally {
            server.stop()
        }

        assertTrue(steps.any { it is PairingStep.Failed && it.reason is PairingFailure.WrongPin })
    }

    @Test
    fun `unicode non-ascii digits are rejected`() = runTest {
        val server = MockPoloServer()
        server.start()

        val session = AndroidTvPairingSession(
            ipAddress = "127.0.0.1",
            port = server.port,
            clientName = "SmartClick",
            serviceName = "atvremote"
        )

        val steps = mutableListOf<PairingStep>()
        try {
            val job = launch {
                session.startPairing(object : FlowCollector<PairingStep> {
                    override suspend fun emit(value: PairingStep) {
                        steps.add(value)
                        if (value is PairingStep.EnterPin) {
                            // Arabic-Indic digits: ٠١٢٣٤٥ (Unicode characters)
                            session.providePin("٠١٢٣٤٥")
                        }
                    }
                })
            }
            job.join()
        } finally {
            server.stop()
        }

        assertTrue(steps.any { it is PairingStep.Failed && it.reason is PairingFailure.WrongPin })
    }

    @Test
    fun `pairing timeout when pin not provided`() = runTest {
        val server = MockPoloServer()
        server.start()

        val session = AndroidTvPairingSession(
            ipAddress = "127.0.0.1",
            port = server.port,
            clientName = "SmartClick",
            serviceName = "atvremote",
            pinTimeoutMs = 50L
        )

        val steps = mutableListOf<PairingStep>()
        try {
            session.startPairing(object : FlowCollector<PairingStep> {
                override suspend fun emit(value: PairingStep) {
                    steps.add(value)
                }
            })
        } finally {
            server.stop()
        }

        assertTrue(steps.any { it is PairingStep.EnterPin })
        assertTrue(steps.any { it is PairingStep.Failed && it.reason is PairingFailure.TimedOut })
    }

    @Test
    fun `cancellation exception is not swallowed as network error`() = runTest {
        val session = AndroidTvPairingSession(
            ipAddress = "127.0.0.1",
            port = 6467,
            clientName = "SmartClick",
            serviceName = "atvremote"
        )

        val steps = mutableListOf<PairingStep>()
        val job = launch {
            session.startPairing(object : FlowCollector<PairingStep> {
                override suspend fun emit(value: PairingStep) {
                    steps.add(value)
                }
            })
        }

        job.cancelAndJoin()

        assertTrue(steps.none { it is PairingStep.Failed && it.reason is PairingFailure.Network })
    }
}
