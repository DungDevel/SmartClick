package com.example.smartclick.app.tvconnection.androidtv

import android.util.Log
import com.example.smartclick.app.domain.connection.ConnectProgress
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteDirection
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteKeyInject
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteMessage
import com.example.smartclick.app.tvconnection.androidtv.proto.RemotePingResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import com.example.smartclick.app.tvconnection.utils.NetworkSecurityUtils

class AndroidTvRemoteSession(
    private val ipAddress: String,
    private val port: Int,
    private val clientCertPair: Pair<PrivateKey, X509Certificate>,
    private val expectedCertSha256: String?
) : ITvController {

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState

    private var _device: TvDevice? = null
    override val device: TvDevice? get() = _device

    override val capabilities: Set<TvCapability> = AndroidTvDriver.ANDROID_TV_CAPABILITIES

    private var socket: SSLSocket? = null
    private var outputStream: OutputStream? = null
    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null
    private val writeMutex = Mutex()

    override suspend fun connect(device: TvDevice): Result<Unit> = withContext(Dispatchers.IO) {
        if (!NetworkSecurityUtils.isPrivateLanAddress(device.ipAddress)) {
            _connectionState.value = TvConnectionState.Failed("Connection rejected: SSRF protection (Public IP skipped)")
            return@withContext Result.failure(Exception("Connection rejected: SSRF protection"))
        }
        _connectionState.value = TvConnectionState.Connecting(ConnectProgress.CONNECTING_SOCKET)
        this@AndroidTvRemoteSession._device = device

        try {
            val trustManager = arrayOf<TrustManager>(com.example.smartclick.app.tvconnection.security.PinningTrustManager(expectedCertSha256))

            val (privateKey, clientCert) = clientCertPair

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry("client", privateKey, charArrayOf(), arrayOf(clientCert))
            }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, charArrayOf())
            }

            val sslContext = SSLContext.getInstance("TLSv1.2").apply {
                init(kmf.keyManagers, trustManager, SecureRandom())
            }

            val factory: SSLSocketFactory = sslContext.socketFactory
            socket = factory.createSocket(ipAddress, port) as SSLSocket
            socket?.startHandshake()

            outputStream = socket?.outputStream

            readJob?.cancel()
            readJob = startReadLoop(socket!!.inputStream)

            _connectionState.value = TvConnectionState.Connected(device)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AndroidTvRemote", "Connect failed", e)
            _connectionState.value = TvConnectionState.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    private fun startReadLoop(inputStream: InputStream): Job {
        return sessionScope.launch {
            try {
                while (isActive) {
                    val msg = RemoteMessage.parseDelimitedFrom(inputStream)
                    if (msg == null) break
                    handleMessage(msg)
                }
            } catch (e: Exception) {
                Log.e("AndroidTvRemote", "Read loop error", e)
            } finally {
                disconnect()
            }
        }
    }

    private fun handleMessage(msg: RemoteMessage) {
        if (msg.hasRemotePingRequest()) {
            val req = msg.remotePingRequest
            val resp = RemoteMessage.newBuilder()
                .setRemotePingResponse(
                    RemotePingResponse.newBuilder()
                        .setVal1(req.val1)
                        .build()
                ).build()
            runBlocking {
                sendMsg(resp)
            }
        } else if (msg.hasRemoteConfigure()) {
            val resp = RemoteMessage.newBuilder()
                .setRemoteConfigure(
                    com.example.smartclick.app.tvconnection.androidtv.proto.RemoteConfigure.newBuilder()
                        .setCode1(622)
                        .setDeviceInfo(
                            com.example.smartclick.app.tvconnection.androidtv.proto.RemoteDeviceInfo.newBuilder()
                                .setModel("SmartClick")
                                .setVendor("SmartClick")
                                .setUnknown1(1)
                                .setUnknown2("1")
                                .setPackageName("com.example.smartclick")
                                .setAppVersion("1.0.0")
                                .build()
                        ).build()
                ).build()
            runBlocking {
                sendMsg(resp)
            }
        } else if (msg.hasRemoteSetActive()) {
            val resp = RemoteMessage.newBuilder()
                .setRemoteSetActive(
                    com.example.smartclick.app.tvconnection.androidtv.proto.RemoteSetActive.newBuilder()
                        .setActive(622)
                        .build()
                ).build()
            runBlocking {
                sendMsg(resp)
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            readJob?.cancel()
            readJob = null
            try {
                socket?.close()
            } catch (e: Exception) {}
            socket = null
            outputStream = null
            _connectionState.value = TvConnectionState.Idle
        }
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        if (socket == null || outputStream == null) {
            return@withContext Result.failure(Exception("Not connected"))
        }

        val keyCode = AndroidTvKeyMapper.mapCommand(command)
        if (keyCode != null) {
            try {
                val msg = RemoteMessage.newBuilder()
                    .setRemoteKeyInject(
                        RemoteKeyInject.newBuilder()
                            .setKeyCode(keyCode)
                            .setDirection(RemoteDirection.SHORT)
                            .build()
                    ).build()
                sendMsg(msg)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("Command not supported or not implemented yet"))
        }
    }

    private suspend fun sendMsg(msg: RemoteMessage) {
        writeMutex.withLock {
            val out = outputStream ?: return@withLock
            try {
                msg.writeDelimitedTo(out)
                out.flush()
            } catch (e: Exception) {
                Log.e("AndroidTvRemote", "Failed to send message", e)
            }
        }
    }
}
