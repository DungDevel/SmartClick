package com.example.smartclick.app.tvconnection.lg.cast

import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.cast.CastSessionInfo
import com.example.smartclick.app.domain.model.CastMediaType
import com.example.smartclick.app.tvconnection.cast.LocalMediaServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class LgWebOsCastController(
    private val transport: LgWebOsCastTransport,
    private val localMediaServer: LocalMediaServer,
    private val ipAddress: String,
    private val clientKey: String?
) : ICastController {

    private val _castPlaybackState = MutableStateFlow(CastPlaybackState.IDLE)
    override val castPlaybackState: StateFlow<CastPlaybackState> = _castPlaybackState.asStateFlow()

    private val _castSessionInfo = MutableStateFlow(CastSessionInfo())
    override val castSessionInfo: StateFlow<CastSessionInfo> = _castSessionInfo.asStateFlow()

    data class MediaViewerSession(
        val sessionId: String,
        val appId: String?,
        val mediaUrl: String
    )

    private var activeSession: MediaViewerSession? = null
    private var registeredMediaSessionId: String? = null

    override suspend fun castMedia(
        url: String,
        mimeType: String,
        type: CastMediaType,
        title: String
    ): Result<Unit> {
        if (url.startsWith("file://", ignoreCase = true)) return Result.failure(CastError.UnsupportedMediaSource("file"))
        if (!url.startsWith("content://", ignoreCase = true) &&
            !url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return Result.failure(CastError.UnsupportedMediaSource(android.net.Uri.parse(url).scheme ?: "unknown"))
        }
        var registeringLocalMedia = false
        return try {
        // Connect if needed
        if (!transport.isConnected) {
            val connectResult = transport.connect(ipAddress, clientKey)
            if (connectResult.isFailure) {
                return Result.failure(connectResult.exceptionOrNull() ?: CastError.NetworkError(Exception("Connect failed")))
            }
        }

        // Clean up previous if any
        if (activeSession != null) {
            transport.sendRequest("ssap://media.viewer/close", JSONObject().apply {
                put("sessionId", activeSession!!.sessionId)
            })
            activeSession = null
        }
        releaseRegisteredMedia()

        _castPlaybackState.value = CastPlaybackState.BUFFERING

        val externalUrl = if (url.startsWith("content://")) {
            registeringLocalMedia = true
            val session = localMediaServer.registerMedia(android.net.Uri.parse(url), mimeType)
            registeredMediaSessionId = session.sessionId
            registeringLocalMedia = false
            session.mediaUrl
        } else {
            url
        }

        _castSessionInfo.value = CastSessionInfo(currentUrl = externalUrl)

        val payload = JSONObject().apply {
            put("target", externalUrl)
            put("mimeType", mimeType)
            put("title", title)
        }

        val result = transport.sendRequest("ssap://media.viewer/open", payload)

        if (result.isFailure) {
            _castPlaybackState.value = CastPlaybackState.IDLE
            releaseRegisteredMedia()
            return Result.failure(result.exceptionOrNull() ?: CastError.ProtocolCommandFailed("Media open failed"))
        }
        val response = result.getOrThrow()
        val sessionId = response.optString("sessionId")
        val appId = response.optString("appId", response.optString("id"))
        if (sessionId.isEmpty()) {
            _castPlaybackState.value = CastPlaybackState.IDLE
            releaseRegisteredMedia()
            return Result.failure(CastError.ProtocolCommandFailed("Missing sessionId in response"))
        }
        activeSession = MediaViewerSession(sessionId, appId.takeIf { it.isNotEmpty() }, externalUrl)
        // Treat MediaViewerReady as BUFFERING until explicit playback confirm.
        _castPlaybackState.value = CastPlaybackState.BUFFERING
        Result.success(Unit)
        } catch (e: Exception) {
            _castPlaybackState.value = CastPlaybackState.IDLE
            _castSessionInfo.value = CastSessionInfo()
            releaseRegisteredMedia()
            Result.failure(if (registeringLocalMedia) {
                if (e is com.example.smartclick.app.tvconnection.cast.NoLanAddressException) CastError.NoLanAddress(e)
                else CastError.MediaRegistrationFailed(e)
            } else CastError.NetworkError(e))
        }
    }

    override suspend fun controlCast(command: CastCommand): Result<Unit> {
        if (!transport.isConnected) {
            if (command == CastCommand.Stop) releaseRegisteredMedia()
            return Result.failure(CastError.NetworkError(Exception("Not connected")))
        }

        return when (command) {
            CastCommand.Play -> {
                val res = transport.sendRequest("ssap://media.controls/play")
                res.map { Unit }
            }
            CastCommand.Pause -> {
                val res = transport.sendRequest("ssap://media.controls/pause")
                res.map { Unit }
            }
            CastCommand.Stop -> {
                try {
                    val res = transport.sendRequest("ssap://media.controls/stop")
                    if (res.isSuccess) _castPlaybackState.value = CastPlaybackState.IDLE
                    res.map { Unit }
                } finally {
                    releaseRegisteredMedia()
                }
            }
            is CastCommand.SeekTo, is CastCommand.SeekBy -> {
                Result.failure(CastError.UnsupportedCommand("Seek is not currently implemented for LG SSAP directly via open endpoint requirements"))
            }
            is CastCommand.SetVolume, CastCommand.Mute, CastCommand.Unmute -> {
                Result.failure(CastError.UnsupportedCommand("Volume controls map to Remote TV volume instead"))
            }
        }
    }

    override suspend fun teardown(): Result<Unit> {
        val session = activeSession
        activeSession = null
        _castPlaybackState.value = CastPlaybackState.IDLE
        _castSessionInfo.value = CastSessionInfo()

        return try {
            if (session != null && transport.isConnected) {
                transport.sendRequest("ssap://media.viewer/close", JSONObject().apply {
                    put("sessionId", session.sessionId)
                }).map { Unit }
            } else {
                Result.success(Unit)
            }
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            try {
                releaseRegisteredMedia()
            } finally {
                transport.disconnect()
            }
        }
    }

    private suspend fun releaseRegisteredMedia() {
        val sessionId = registeredMediaSessionId ?: return
        try {
            localMediaServer.unregisterMedia(sessionId)
        } finally {
            registeredMediaSessionId = null
        }
    }
}
