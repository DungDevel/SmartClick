package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.PairingFailure
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.tvconnection.androidtv.proto.PoloProto
import com.example.smartclick.app.tvconnection.androidtv.proto.PoloProto.OuterMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class AndroidTvPairingSession(
    private val ipAddress: String,
    private val port: Int,
    private val clientName: String,
    private val serviceName: String
) {
    private val pinChannel = Channel<String>(Channel.CONFLATED)

    suspend fun providePin(pin: String) {
        pinChannel.send(pin)
    }

    suspend fun startPairing(collector: FlowCollector<PairingStep>) {
        withContext(Dispatchers.IO) {
            var socket: SSLSocket? = null
            try {
                // TOFU mode via PinningTrustManager
                val tofuTrustManager = com.example.smartclick.app.tvconnection.security.PinningTrustManager(expectedSha256 = null, isPairing = true)
                val tofuCerts = arrayOf<TrustManager>(tofuTrustManager)

                val (privateKey, clientCert) = AndroidTvCertificateManager.getOrGenerateClientKeyAndCertificate()

                // Need a keystore with this keypair to initialize KeyManagerFactory
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setKeyEntry("client", privateKey, charArrayOf(), arrayOf(clientCert))
                }
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                    init(keyStore, charArrayOf())
                }

                val sslContext = SSLContext.getInstance("TLSv1.2").apply {
                    init(kmf.keyManagers, tofuCerts, SecureRandom())
                }

                val factory: SSLSocketFactory = sslContext.socketFactory
                socket = factory.createSocket(ipAddress, port) as SSLSocket
                socket.startHandshake()

                val serverCert = socket.session.peerCertificates.firstOrNull() as? X509Certificate
                    ?: throw RuntimeException("No server certificate")

                val inputStream = socket.inputStream
                val outputStream = socket.outputStream

                // 1. Send PairingRequest
                val requestMsg = OuterMessage.newBuilder()
                    .setProtocolVersion(2)
                    .setStatus(OuterMessage.Status.STATUS_OK)
                    .setPairingRequest(
                        PoloProto.PairingRequest.newBuilder()
                            .setClientName(clientName)
                            .setServiceName(serviceName)
                            .build()
                    )
                    .build()
                sendMsg(outputStream, requestMsg)

                // 2. Read PairingRequestAck
                val ackMsg = readMsg(inputStream)
                if (ackMsg.status != OuterMessage.Status.STATUS_OK || !ackMsg.hasPairingRequestAck()) {
                    collector.emit(PairingStep.Failed(PairingFailure.Unknown("Expected PairingRequestAck but got $ackMsg")))
                    return@withContext
                }

                // 3. Send Options
                val optionsMsg = OuterMessage.newBuilder()
                    .setProtocolVersion(2)
                    .setStatus(OuterMessage.Status.STATUS_OK)
                    .setOptions(
                        PoloProto.Options.newBuilder()
                            .setPreferredRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT)
                            .addInputEncodings(
                                PoloProto.Options.Encoding.newBuilder()
                                    .setType(PoloProto.Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
                                    .setSymbolLength(6)
                            )
                    ).build()
                sendMsg(outputStream, optionsMsg)

                // 4. Read Options
                val serverOptions = readMsg(inputStream)
                if (!serverOptions.hasOptions()) {
                    collector.emit(PairingStep.Failed(PairingFailure.Unknown("Expected Options")))
                    return@withContext
                }

                // 5. Send Configuration
                val configMsg = OuterMessage.newBuilder()
                    .setProtocolVersion(2)
                    .setStatus(OuterMessage.Status.STATUS_OK)
                    .setConfiguration(
                        PoloProto.Configuration.newBuilder()
                            .setClientRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT)
                            .setEncoding(
                                PoloProto.Options.Encoding.newBuilder()
                                    .setType(PoloProto.Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
                                    .setSymbolLength(6)
                            )
                    ).build()
                sendMsg(outputStream, configMsg)

                // 6. Read ConfigurationAck
                val configAck = readMsg(inputStream)
                if (!configAck.hasConfigurationAck()) {
                    collector.emit(PairingStep.Failed(PairingFailure.Unknown("Expected ConfigurationAck")))
                    return@withContext
                }

                // At this point, the TV is showing a PIN.
                collector.emit(PairingStep.EnterPin(6))
                val pin = pinChannel.receive()

                if (pin.length != 6) {
                    collector.emit(PairingStep.Failed(PairingFailure.WrongPin()))
                    return@withContext
                }

                val secretBytes = generateSecret(clientCert, serverCert, pin)

                // Send Secret
                val secretMsg = OuterMessage.newBuilder()
                    .setProtocolVersion(2)
                    .setStatus(OuterMessage.Status.STATUS_OK)
                    .setSecret(
                        PoloProto.Secret.newBuilder()
                            .setSecret(com.google.protobuf.ByteString.copyFrom(secretBytes))
                    ).build()
                sendMsg(outputStream, secretMsg)

                // Read SecretAck
                val secretAck = readMsg(inputStream)
                if (secretAck.hasSecretAck()) {
                    val md = MessageDigest.getInstance("SHA-256")
                    val certHash = md.digest(serverCert.encoded).joinToString("") { "%02x".format(it) }
                    collector.emit(PairingStep.Succeeded(Credentials(mapOf("cert_sha256" to certHash))))
                } else {
                    collector.emit(PairingStep.Failed(PairingFailure.WrongPin()))
                }

            } catch (e: Exception) {
                collector.emit(PairingStep.Failed(PairingFailure.Network(e.message ?: "Network error")))
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {}
            }
        }
    }

    private fun sendMsg(out: OutputStream, msg: OuterMessage) {
        msg.writeDelimitedTo(out)
        out.flush()
    }

    private fun readMsg(inputStream: InputStream): OuterMessage {
        return OuterMessage.parseDelimitedFrom(inputStream) ?: throw RuntimeException("Stream closed")
    }

    companion object {
        internal fun generateSecret(clientCert: X509Certificate, serverCert: X509Certificate, pin: String): ByteArray {
            val clientKey = clientCert.publicKey as RSAPublicKey
            val serverKey = serverCert.publicKey as RSAPublicKey

            val md = MessageDigest.getInstance("SHA-256")

            md.update(clientKey.modulus.toUnsignedByteArray())
            md.update(clientKey.publicExponent.toPythonExponentBytes())

            md.update(serverKey.modulus.toUnsignedByteArray())
            md.update(serverKey.publicExponent.toPythonExponentBytes())

            // from: pairing_code[2:]
            val hexTail = pin.substring(2)
            md.update(hexToBytes(hexTail))

            val hash = md.digest()

            // optional local validation
            // val expectedFirstByte = pin.substring(0, 2).toInt(16).toByte()

            return hash
        }

        internal fun BigInteger.toUnsignedByteArray(): ByteArray {
            val bytes = this.toByteArray()
            return if (bytes[0].toInt() == 0 && bytes.size > 1) {
                bytes.copyOfRange(1, bytes.size)
            } else {
                bytes
            }
        }

        internal fun BigInteger.toPythonExponentBytes(): ByteArray {
            val hexStr = this.toString(16).uppercase()
            var pythonStr = "0$hexStr"
            if (pythonStr.length % 2 != 0) {
                pythonStr = "0$pythonStr"
            }
            return hexToBytes(pythonStr)
        }

        internal fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            }
            return data
        }
    }
}

