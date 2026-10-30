package com.example.smartclick.app.tvconnection.cast.google

import android.util.Log
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.cast.CastSessionInfo
import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.model.CastMediaType
import com.example.smartclick.app.tvconnection.cast.LocalMediaServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Top-level Google Cast ICastController implementation.
 *
 * Orchestrates the full Cast V2 session lifecycle:
 * 1. Connect TLS to port 8009
 * 2. Virtual connection to receiver-0
 * 3. Start heartbeat
 * 4. Launch Default Media Receiver (CC1AD845)
 * 5. Obtain transportId from RECEIVER_STATUS
 * 6. Virtual connection to transportId
 * 7. Hand off to GoogleCastMediaController for media commands
 * 8. Clean teardown
 *
 * NOTE: Authentication uses a TOFU trust-manager approach. Production-grade Cast
 * authentication against Google's root CA is NOT yet implemented. See GoogleCastAuthManager.
 */
class GoogleCastController(
    private val ipAddress: String,
    private val port: Int = 8009,
    private val localMediaServer: LocalMediaServer,
    private val sslSocketFactory: SSLSocketFactory,
    private val authManager: GoogleCastAuthManager,
    private val onDeviceAuthenticated: (suspend (String) -> Unit)? = null
) : ICastController {

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var castSession: GoogleCastSession? = null
    private var protocolClient: GoogleCastProtocolClient? = null
    private var mediaController: GoogleCastMediaController? = null
    private var sessionFailure: Throwable? = null
    private var deviceAuthFailed = false

    private val _castPlaybackState = MutableStateFlow(CastPlaybackState.IDLE)
    override val castPlaybackState: StateFlow<CastPlaybackState> = _castPlaybackState.asStateFlow()

    private val _castSessionInfo = MutableStateFlow(CastSessionInfo())
    override val castSessionInfo: StateFlow<CastSessionInfo> = _castSessionInfo.asStateFlow()

    // Track state collection job
    private var stateCollectionJob: Job? = null

    override suspend fun castMedia(
        url: String,
        mimeType: String,
        type: CastMediaType,
        title: String
    ): Result<Unit> {
        if (url.startsWith("file://", ignoreCase = true)) {
            return Result.failure(CastError.UnsupportedMediaSource("file"))
        }
        if (type == CastMediaType.VIDEO && castSession?.isAudioOnly == true) {
            return Result.failure(CastError.ProtocolCommandFailed("Audio-Only receiver rejects video casting"))
        }

        return try {
            // 1. Ensure session is established
            val mc = ensureMediaController()
                ?: return Result.failure(sessionFailure?.takeIf { deviceAuthFailed || isTlsIdentityFailure(it) }?.let { CastError.TlsAuthenticationFailed(it) }
                    ?: CastError.NetworkError(Exception("Failed to establish Cast session")))

            // 2. Resolve URL: local URIs go through LocalMediaServer
            val resolvedUrl = if (url.startsWith("content://")) {
                val mediaSession = try {
                    localMediaServer.registerMedia(android.net.Uri.parse(url), mimeType, title)
                } catch (e: Exception) {
                    return Result.failure(CastError.MediaRegistrationFailed(e))
                }
                mediaSession.mediaUrl
            } else {
                url
            }

            _castSessionInfo.value = _castSessionInfo.value.copy(currentUrl = resolvedUrl)

            // 3. Send LOAD command
            mc.load(resolvedUrl, mimeType, title)
        } catch (e: Exception) {
            Result.failure(CastError.NetworkError(e))
        }
    }

    override suspend fun controlCast(command: CastCommand): Result<Unit> {
        val mc = mediaController
        val pc = protocolClient

        return when (command) {
            CastCommand.Play -> mc?.play() ?: Result.failure(CastError.ProtocolCommandFailed("No active media session"))
            CastCommand.Pause -> mc?.pause() ?: Result.failure(CastError.ProtocolCommandFailed("No active media session"))
            CastCommand.Stop -> mc?.stop() ?: Result.failure(CastError.ProtocolCommandFailed("No active media session"))
            is CastCommand.SeekTo -> {
                val positionSeconds = command.positionMs / 1000.0
                mc?.seek(positionSeconds) ?: Result.failure(CastError.ProtocolCommandFailed("No active media session"))
            }
            is CastCommand.SeekBy -> {
                val currentMs = _castSessionInfo.value.positionMs
                val targetMs = (currentMs + command.offsetMs).coerceAtLeast(0)
                val positionSeconds = targetMs / 1000.0
                mc?.seek(positionSeconds) ?: Result.failure(CastError.ProtocolCommandFailed("No active media session"))
            }
            is CastCommand.SetVolume -> pc?.setVolume(command.volume.toFloat() / 100f) ?: Result.failure(CastError.ProtocolCommandFailed("Protocol client not ready"))
            is CastCommand.Mute -> pc?.setMute(true) ?: Result.failure(CastError.ProtocolCommandFailed("Protocol client not ready"))
            is CastCommand.Unmute -> pc?.setMute(false) ?: Result.failure(CastError.ProtocolCommandFailed("Protocol client not ready"))
        }
    }

    private suspend fun setReceiverVolume(level: Float): Result<Unit> {
        // Obsolete: Replaced by protocolClient.setVolume
        return Result.success(Unit)
    }

    private suspend fun setReceiverMute(muted: Boolean): Result<Unit> {
        // Obsolete: Replaced by protocolClient.setMute
        return Result.success(Unit)
    }

    private fun createReceiverMessage(payload: JSONObject): com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage {
        return com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage.newBuilder()
            .setProtocolVersion(com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId("smartclick-sender")
            .setDestinationId("receiver-0")
            .setNamespace("urn:x-cast:com.google.cast.receiver")
            .setPayloadType(com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage.PayloadType.STRING)
            .setPayloadUtf8(payload.toString())
            .build()
    }

    override suspend fun teardown(): Result<Unit> {
        return try {
            stateCollectionJob?.cancel()
            stateCollectionJob = null

            mediaController?.close()
            mediaController = null

            protocolClient?.close()
            protocolClient = null

            castSession?.disconnect()
            castSession = null

            _castPlaybackState.value = CastPlaybackState.IDLE
            _castSessionInfo.value = CastSessionInfo()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Establishes the full Cast V2 session if not already connected, and returns
     * the media controller ready for commands.
     */
    private suspend fun ensureMediaController(): GoogleCastMediaController? {
        sessionFailure = null
        deviceAuthFailed = false
        // Return existing if still valid
        mediaController?.let { mc ->
            val state = castSession?.connectionState?.value
            if (state == GoogleCastSession.State.CONNECTED || state == GoogleCastSession.State.AUTHENTICATED) {
                return mc
            }
        }

        // Clean up stale state
        mediaController?.close()
        mediaController = null
        protocolClient?.close()
        protocolClient = null
        castSession?.disconnect()

        // Step 1: Create TLS session
        val session = GoogleCastSession(ipAddress, port, sslSocketFactory, authManager)
        session.onDeviceAuthenticated = onDeviceAuthenticated
        val connectResult = session.connect()
        if (connectResult.isFailure) {
            Log.e(TAG, "TLS connection failed: ${connectResult.exceptionOrNull()?.message}")
            sessionFailure = connectResult.exceptionOrNull()
            return null
        }
        castSession = session

        // Step 1b: Authenticate session
        val authResult = session.authenticate()
        if (authResult.isFailure) {
            Log.e(TAG, "Device authentication failed: ${authResult.exceptionOrNull()?.message}")
            session.disconnect()
            sessionFailure = authResult.exceptionOrNull()
            deviceAuthFailed = true
            castSession = null
            return null
        }

        // Step 2: Create protocol client (virtual connection to receiver-0)
        val protocol = GoogleCastProtocolClient(session, controllerScope)
        protocolClient = protocol

        // Step 3: Connect to receiver
        protocol.connectToReceiver()

        // Step 4: Start heartbeat
        protocol.startHeartbeat()

        // Step 5: Launch Default Media Receiver
        val launchResult = protocol.launchApp(DEFAULT_MEDIA_RECEIVER_APP_ID)
        if (launchResult == null) {
            Log.e(TAG, "Failed to launch Default Media Receiver")
            return null
        }

        // Step 6: Extract transportId from RECEIVER_STATUS
        val transportId = extractTransportId(launchResult)
        if (transportId == null) {
            Log.e(TAG, "No transportId in RECEIVER_STATUS response")
            return null
        }

        // Step 7: Virtual connection to transportId
        protocol.connectToApp(transportId)

        // Step 8: Create media controller
        val mc = GoogleCastMediaController(session, transportId, controllerScope) { state ->
            _castPlaybackState.value = state
        }
        mediaController = mc

        // Forward media controller state to ICastController flows
        stateCollectionJob?.cancel()
        stateCollectionJob = controllerScope.launch {
            launch {
                mc.sessionInfo.collect { info ->
                    // Merge, keeping currentUrl from our level if media controller doesn't set it
                    val current = _castSessionInfo.value
                    _castSessionInfo.value = info.copy(
                        currentUrl = info.currentUrl.ifEmpty { current.currentUrl }
                    )
                }
            }
        }

        return mc
    }

    private fun extractTransportId(receiverStatus: JSONObject): String? {
        // The status comes from RECEIVER_STATUS which has a "status" object containing "applications" array
        val status = receiverStatus.optJSONObject("status")
            ?: receiverStatus  // fallback: the json itself might be the status

        val apps = status.optJSONArray("applications")
            ?: receiverStatus.optJSONArray("applications")  // fallback

        if (apps != null) {
            for (i in 0 until apps.length()) {
                val app = apps.getJSONObject(i)
                if (app.optString("appId") == DEFAULT_MEDIA_RECEIVER_APP_ID) {
                    return app.optString("transportId").takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }

    private fun isTlsIdentityFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is java.security.cert.CertificateException || current is javax.net.ssl.SSLHandshakeException) return true
            current = current.cause
        }
        return false
    }

    companion object {
        private const val TAG = "GoogleCastController"
        const val DEFAULT_MEDIA_RECEIVER_APP_ID = "CC1AD845"
    }
}
