package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteConfigure
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteMessage
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteSetActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.ServerSocket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket

class MockAndroidTvServer {

    var port: Int = 0
        private set

    lateinit var serverFingerprint: String
        private set

    lateinit var clientCertPair: Pair<java.security.PrivateKey, X509Certificate>
        private set

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun start() {
        // 1. Generate Server Certificate
        val serverKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val serverCert = generateCertificate("CN=MockAndroidTVServer", serverKeyPair)

        serverFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(serverCert.encoded)
            .joinToString("") { "%02x".format(it) }

        // 2. Generate Client Certificate (For passing into session)
        val clientKp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val clientCert = generateCertificate("CN=MockAndroidTVClient", clientKp)
        clientCertPair = Pair(clientKp.private, clientCert)

        // 3. Setup SSL Server Socket
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("server", serverKeyPair.private, "pwd".toCharArray(), arrayOf(serverCert))
        }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, "pwd".toCharArray())
        }

        val sslContext = SSLContext.getInstance("TLSv1.2").apply {
            init(kmf.keyManagers, null, SecureRandom())
        }

        val factory: SSLServerSocketFactory = sslContext.serverSocketFactory
        serverSocket = factory.createServerSocket(0).apply {
            port = this.localPort
        }

        scope.launch {
            while (isActive) {
                try {
                    val socket = serverSocket?.accept() as? SSLSocket ?: break
                    handleClient(socket)
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun handleClient(socket: SSLSocket) {
        scope.launch {
            try {
                socket.use {
                    it.startHandshake()
                    val inputStream = it.inputStream
                    val outputStream = it.outputStream

                    while (isActive) {
                        try {
                            val msg = RemoteMessage.parseDelimitedFrom(inputStream) ?: break

                            if (msg.hasRemoteConfigure()) {
                                val reply = RemoteMessage.newBuilder().setRemoteConfigure(
                                    RemoteConfigure.newBuilder()
                                        .setCode1(msg.remoteConfigure.code1)
                                        .setDeviceInfo(msg.remoteConfigure.deviceInfo.toBuilder().setVendor("MockServer").setModel("MockModel"))
                                ).build()
                                reply.writeDelimitedTo(outputStream)
                            } else if (msg.hasRemoteSetActive()) {
                                val reply = RemoteMessage.newBuilder().setRemoteSetActive(
                                    RemoteSetActive.newBuilder().setActive(msg.remoteSetActive.active)
                                ).build()
                                reply.writeDelimitedTo(outputStream)
                            }
                        } catch (e: Exception) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore handshake or socket errors resulting from test validations rejecting the connection
            }
        }
    }

    fun stop() {
        serverSocket?.close()
    }

    private fun generateCertificate(dn: String, keyPair: java.security.KeyPair): X509Certificate {
        val issuer = X500Name(dn)
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date(System.currentTimeMillis() - 86400000L)
        val notAfter = Date(System.currentTimeMillis() + 86400000L * 365)

        val builder = JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, issuer, keyPair.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)

        val certHolder = builder.build(signer)
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }
}
