package com.example.smartclick.app.tvconnection.cast.google

import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.DeviceAuthMessage
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthChallenge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.MessageDigest
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class GoogleCastSession(
    private val ipAddress: String,
    private val port: Int = 8009,
    private val sslSocketFactory: SSLSocketFactory = SSLContext.getDefault().socketFactory,
    private val authManager: GoogleCastAuthManager,
    private val authTimeoutMillis: Long = 5000
) {

    enum class State {
        DISCONNECTED, CONNECTING, CONNECTED, AUTHENTICATING, AUTHENTICATED, CLOSING, FAILED
    }

    private val _connectionState = MutableStateFlow(State.DISCONNECTED)
    val connectionState: StateFlow<State> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<CastMessage>(replay = 1, extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<CastMessage> = _incomingMessages.asSharedFlow()

    private var sessionScope: CoroutineScope? = null
    private var socket: SSLSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var tlsPeerCertificateDer: ByteArray? = null
    private var tlsPeerCertificateSha256: String? = null

    private val writeMutex = Mutex()

    var isAudioOnly: Boolean = false
        private set

    suspend fun connect(): Result<Unit> {
        val currentState = _connectionState.value
        if (currentState == State.CONNECTED || currentState == State.AUTHENTICATED || currentState == State.CONNECTING) {
            return Result.success(Unit)
        }

        _incomingMessages.resetReplayCache()
        _connectionState.value = State.CONNECTING
        val scope = CoroutineScope(Dispatchers.IO + Job())
        sessionScope = scope

        return try {
            withContext(Dispatchers.IO) {
                val newSocket = sslSocketFactory.createSocket() as SSLSocket
                newSocket.connect(InetSocketAddress(ipAddress, port), 5000)
                newSocket.startHandshake()

                // Capture TLS Peer Certificate for identity binding in Auth step
                val certs = newSocket.session.peerCertificates
                if (certs.isEmpty()) {
                    throw SSLPeerUnverifiedException("No peer certificates found during TLS handshake")
                }
                val peerCert = certs[0] as java.security.cert.X509Certificate

                // Kiểm tra thời hạn
                peerCert.checkValidity()

                // Kiểm tra thời hạn không quá 4 ngày theo khuyến nghị của Open Screen (đối với TLS peer cert)
                val validityDays = (peerCert.notAfter.time - peerCert.notBefore.time) / (1000 * 60 * 60 * 24)
                if (validityDays > 4) {
                     // Log hoặc xử lý tùy theo chính sách dự án. Chromium Open Screen kiểm tra nghiêm ngặt.
                     // throw SSLPeerUnverifiedException("TLS peer certificate lifetime exceeds 4 days")
                }

                tlsPeerCertificateDer = peerCert.encoded
                tlsPeerCertificateSha256 = MessageDigest.getInstance("SHA-256").digest(peerCert.encoded)
                    .joinToString("") { "%02x".format(it) }

                socket = newSocket
                inputStream = newSocket.inputStream
                outputStream = newSocket.outputStream

                _connectionState.value = State.CONNECTED

                scope.launch {
                    readLoop()
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _connectionState.value = State.FAILED
            cleanup()
            Result.failure(e)
        }
    }

    /**
     * Initiates the Google Cast Device Authentication flow.
     */
    suspend fun authenticate(): Result<Unit> {
        if (_connectionState.value != State.CONNECTED) {
            return Result.failure(IOException("Must be TLS connected before authentication"))
        }

        _connectionState.value = State.AUTHENTICATING

        return try {
            val peerCert = tlsPeerCertificateDer ?: return Result.failure(IOException("TLS peer certificate not captured"))

            // --- C1 SECURITY FIX: Kiểm tra thời hạn TLS Peer Certificate ---
            val cf = java.security.cert.CertificateFactory.getInstance("X.509")
            val tlsCert = cf.generateCertificate(java.io.ByteArrayInputStream(peerCert)) as java.security.cert.X509Certificate
            tlsCert.checkValidity()

            // Chromium Open Screen: Kiểm tra thời hạn không vượt quá 4 ngày
            val validityMillis = tlsCert.notAfter.time - tlsCert.notBefore.time
            val maxValidityMillis = 4L * 24 * 60 * 60 * 1000
            if (validityMillis > maxValidityMillis) {
                throw java.security.cert.CertificateException("TLS peer certificate lifetime exceeds 4 days limit")
            }
            // ----------------------------------------------------------------

            // 1. Create Challenge
            val nonce = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val challenge = AuthChallenge.newBuilder()
                .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
                .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
                .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
                .build()

            val authMessage = DeviceAuthMessage.newBuilder()
                .setChallenge(challenge)
                .build()

            // 2. Send Challenge
            sendMessage(createCastMessage("urn:x-cast:com.google.cast.tp.deviceauth", authMessage))

            // 3. Wait for Response with bounded timeout
            val response = withTimeout(authTimeoutMillis) {
                waitForAuthResponse(challenge)
            }

            // 4. Verify Response using the captured TLS peer certificate
            authManager.verifyResponse(challenge, response, peerCert).onSuccess { audioOnly ->
                onDeviceAuthenticated?.invoke(tlsPeerCertificateSha256 ?: throw IOException("TLS peer fingerprint missing"))
                isAudioOnly = audioOnly
                _connectionState.value = State.AUTHENTICATED
            }.onFailure {
                _connectionState.value = State.FAILED
                throw it
            }

            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = State.FAILED
            cleanup()
            Result.failure(e)
        }
    }

    private suspend fun waitForAuthResponse(challenge: AuthChallenge): com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthResponse {
        // We use firstOrNull to avoid infinite collection and ensure we get the first matching response
        val response = incomingMessages.firstOrNull { msg ->
            if (msg.namespace == "urn:x-cast:com.google.cast.tp.deviceauth") {
                try {
                    val deviceAuthMsg = DeviceAuthMessage.parseFrom(msg.payloadBinary)
                    deviceAuthMsg.hasResponse() && deviceAuthMsg.response.senderNonce.equals(challenge.senderNonce)
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }
        } ?: throw IOException("Authentication response timeout or invalid message")

        val deviceAuthMsg = DeviceAuthMessage.parseFrom(response.payloadBinary)
        return deviceAuthMsg.response
    }

    private fun createCastMessage(namespace: String, payload: com.google.protobuf.MessageLite): CastMessage {
        return CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId("smartclick-sender")
            .setDestinationId("receiver")
            .setNamespace(namespace)
            .setPayloadType(CastMessage.PayloadType.BINARY)
            .setPayloadBinary(com.google.protobuf.ByteString.copyFrom(payload.toByteArray()))
            .build()
    }

    private suspend fun readLoop() {
        val input = inputStream ?: return
        try {
            while (sessionScope?.isActive == true) {
                val message = CastV2Framing.readFrame(input)
                _incomingMessages.tryEmit(message)
            }
        } catch (e: EOFException) {
            handleDisconnection(State.DISCONNECTED)
        } catch (e: Exception) {
            handleDisconnection(State.FAILED)
        }
    }

    suspend fun sendMessage(message: CastMessage): Result<Unit> {
        if (_connectionState.value != State.CONNECTED && _connectionState.value != State.AUTHENTICATING && _connectionState.value != State.AUTHENTICATED) {
            return Result.failure(IOException("Socket is not in a state that allows sending"))
        }

        val out = outputStream ?: return Result.failure(IOException("OutputStream is null"))

        return try {
            writeMutex.withLock {
                withContext(Dispatchers.IO) {
                    CastV2Framing.writeFrame(out, message)
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            handleDisconnection(State.FAILED)
            Result.failure(e)
        }
    }

    suspend fun disconnect() {
        if (_connectionState.value == State.DISCONNECTED) return
        _connectionState.value = State.CLOSING
        cleanup()
        _connectionState.value = State.DISCONNECTED
    }

    private fun handleDisconnection(newState: State) {
        if (_connectionState.value != State.CLOSING && _connectionState.value != State.DISCONNECTED) {
            _connectionState.value = newState
        }
        cleanup()
    }

    private fun cleanup() {
        sessionScope?.cancel()
        sessionScope = null
        try {
            socket?.close()
        } catch (ignore: Exception) {}

        socket = null
        inputStream = null
        outputStream = null
        tlsPeerCertificateDer = null
        tlsPeerCertificateSha256 = null
        // Keep the last received frame available to protocol collectors: EOF can arrive
        // immediately after a valid response and cleanup must not erase that response.
    }

    // Internal Exception for TLS issues
    private class SSLPeerUnverifiedException(message: String) : IOException(message)

    internal var onDeviceAuthenticated: (suspend (String) -> Unit)? = null
}

