package com.example.smartclick.app.tvconnection.cast.google

import android.util.Log
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.cast.CastSessionInfo
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles Google Cast media namespace (urn:x-cast:com.google.cast.media) operations.
 *
 * This controller sends media commands (LOAD, PLAY, PAUSE, STOP, SEEK, GET_STATUS)
 * to the application transportId — NOT to receiver-0. It processes MEDIA_STATUS
 * broadcasts to maintain playback state and session info.
 *
 * NOTE: Authentication uses a TOFU trust-manager approach. Production-grade Cast
 * authentication against Google's root CA is NOT yet implemented. See GoogleCastAuthManager.
 */
class GoogleCastMediaController(
    private val session: GoogleCastSession,
    private val transportId: String,
    private val scope: CoroutineScope,
    private val onPlaybackStateChanged: (CastPlaybackState) -> Unit = {}
) {
    private val requestIdCounter = AtomicInteger(100) // start at 100 to avoid collision with protocol client
    private val pendingMediaRequests = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()

    // Explicit client lifecycle management
    private val controllerJob = SupervisorJob(scope.coroutineContext[Job])
    private val controllerScope = scope + controllerJob

    private var currentMediaSessionId: Int = 0

    private val _playbackState = MutableStateFlow(CastPlaybackState.IDLE)
    val playbackState: StateFlow<CastPlaybackState> = _playbackState.asStateFlow()

    private val _sessionInfo = MutableStateFlow(CastSessionInfo())
    val sessionInfo: StateFlow<CastSessionInfo> = _sessionInfo.asStateFlow()

    init {
        controllerScope.launch {
            session.incomingMessages.collect { msg ->
                if (msg.namespace == MEDIA_NAMESPACE &&
                    msg.payloadType == CastMessage.PayloadType.STRING
                ) {
                    try {
                        val json = JSONObject(msg.payloadUtf8)
                        handleMediaMessage(json)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing media message: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun handleMediaMessage(json: JSONObject) {
        val type = json.optString("type")
        val requestId = json.optInt("requestId", 0)

        when (type) {
            "MEDIA_STATUS" -> {
                val statusArray = json.optJSONArray("status")
                if (statusArray != null && statusArray.length() > 0) {
                    val status = statusArray.getJSONObject(0)
                    processMediaStatus(status)
                }

                // Complete pending request if correlated
                if (requestId > 0) {
                    pendingMediaRequests.remove(requestId)?.complete(json)
                }
            }
            "INVALID_PLAYER_STATE",
            "LOAD_FAILED",
            "LOAD_CANCELLED",
            "INVALID_REQUEST" -> {
                Log.w(TAG, "Cast media error: $type (requestId=$requestId)")
                updatePlaybackState(CastPlaybackState.ERROR)

                if (requestId > 0) {
                    val reason = json.optString("reason", type)
                    pendingMediaRequests.remove(requestId)
                        ?.completeExceptionally(IOException("Cast media error: $type ($reason)"))
                }
            }
        }
    }

    private fun processMediaStatus(status: JSONObject) {
        val incomingSessionId = status.optInt("mediaSessionId", 0)

        // Prevent stale session state from overwriting active state
        if (currentMediaSessionId > 0 && incomingSessionId > 0 && incomingSessionId < currentMediaSessionId) {
            Log.d(TAG, "Ignoring stale MEDIA_STATUS for session $incomingSessionId (active=$currentMediaSessionId)")
            return
        }

        if (incomingSessionId > 0) {
            currentMediaSessionId = incomingSessionId
        }

        // Map playerState
        val playerState = status.optString("playerState", "")
        val idleReason = status.optString("idleReason", "")
        val newPlaybackState = mapPlayerState(playerState, idleReason)
        updatePlaybackState(newPlaybackState)

        // Update session info with partial update support
        val currentTime = status.optDouble("currentTime", Double.NaN)
        val media = status.optJSONObject("media")
        val duration = media?.optDouble("duration", Double.NaN) ?: Double.NaN

        val currentInfo = _sessionInfo.value
        _sessionInfo.value = currentInfo.copy(
            positionMs = if (!currentTime.isNaN()) (currentTime * 1000).toLong() else currentInfo.positionMs,
            durationMs = if (!duration.isNaN()) (duration * 1000).toLong() else currentInfo.durationMs
        )
    }

    private fun mapPlayerState(playerState: String, idleReason: String): CastPlaybackState {
        return when (playerState) {
            "PLAYING" -> CastPlaybackState.PLAYING
            "PAUSED" -> CastPlaybackState.PAUSED
            "BUFFERING" -> CastPlaybackState.BUFFERING
            "IDLE" -> when (idleReason) {
                "FINISHED" -> CastPlaybackState.COMPLETED
                "ERROR" -> CastPlaybackState.ERROR
                "CANCELLED" -> CastPlaybackState.STOPPED
                else -> CastPlaybackState.IDLE
            }
            else -> CastPlaybackState.IDLE
        }
    }

    suspend fun load(
        contentId: String,
        contentType: String,
        title: String,
        metadataType: Int = 0,
        metadata: Map<String, Any> = emptyMap(),
        tracks: List<JSONObject> = emptyList(),
        activeTrackIds: List<Int> = emptyList()
    ): Result<Unit> {
        if (contentId.isBlank()) {
            return Result.failure(IllegalArgumentException("contentId must not be blank"))
        }
        if (contentType.isBlank()) {
            return Result.failure(IllegalArgumentException("contentType must not be blank"))
        }
        if (!isSessionReady()) {
            return Result.failure(IOException("Session is not connected"))
        }

        val reqId = requestIdCounter.incrementAndGet()
        val payload = JSONObject().apply {
            put("type", "LOAD")
            put("requestId", reqId)
            put("autoplay", true)
            put("media", JSONObject().apply {
                put("contentId", contentId)
                put("contentType", contentType)
                put("streamType", "BUFFERED")
                put("metadata", JSONObject().apply {
                    put("title", title)
                    put("metadataType", metadataType)
                    metadata.forEach { (key, value) -> put(key, value) }
                })
                if (tracks.isNotEmpty()) {
                    put("tracks", org.json.JSONArray(tracks))
                }
                if (activeTrackIds.isNotEmpty()) {
                    put("activeTrackIds", org.json.JSONArray(activeTrackIds))
                }
            })
        }

        updatePlaybackState(CastPlaybackState.BUFFERING)
        return sendMediaCommand(reqId, payload)
    }

    suspend fun editTracks(activeTrackIds: List<Int>): Result<Unit> {
        if (!isSessionReady()) {
            return Result.failure(IOException("Session is not connected"))
        }
        if (currentMediaSessionId <= 0) {
            return Result.failure(IOException("No active media session"))
        }

        val reqId = requestIdCounter.incrementAndGet()
        val payload = JSONObject().apply {
            put("type", "EDIT_TRACKS_INFO")
            put("requestId", reqId)
            put("mediaSessionId", currentMediaSessionId)
            put("activeTrackIds", org.json.JSONArray(activeTrackIds))
        }

        return sendMediaCommand(reqId, payload)
    }

    suspend fun play(): Result<Unit> {
        if (!isSessionReady()) {
            return Result.failure(IOException("Session is not connected"))
        }
        if (currentMediaSessionId <= 0) {
            return Result.failure(IOException("No active media session"))
        }

        val reqId = requestIdCounter.incrementAndGet()
        val payload = JSONObject().apply {
            put("type", "PLAY")
            put("requestId", reqId)
            put("mediaSessionId", currentMediaSessionId)
        }

        return sendMediaCommand(reqId, payload)
    }

    suspend fun pause(): Result<Unit> {
        if (!isSessionReady()) {
            return Result.failure(IOException("Session is not connected"))
        }
        if (currentMediaSessionId <= 0) {
            return Result.failure(IOException("No active media session"))
        }

        val reqId = requestIdCounter.incrementAndGet()
        val payload = JSONObject().apply {
            put("type", "PAUSE")
            put("requestId", reqId)
            put("mediaSessionId", currentMediaSessionId)
        }

        return sendMediaCommand(reqId, payload)
    }

    suspend fun stop(): Result<Unit> {
        if (!isSessionReady()) {
            return Result.failure(IOException("Session is not connected"))
        }
        if (currentMediaSessionId <= 0) {
            return Result.failure(IOException("No active media session"))
        }

        val reqId = requestIdCounter.incrementAndGet()
        val payload = JSONObject().apply {
            put("type", "STOP")
            put("requestId", reqId)
            put("mediaSessionId", currentMediaSessionId)
        }

        return sendMediaCommand(reqId, payload)
    }

    suspend fun seek(positionSeconds: Double): Result<Unit> {
        if (positionSeconds < 0) {
            return Result.failure(IllegalArgumentException("Seek position must be non-negative"))
        }
        if (!isSessionReady()) {
            return Result.failure(IOException("Session is not connected"))
        }
        if (currentMediaSessionId <= 0) {
            return Result.failure(IOException("No active media session"))
        }

        val reqId = requestIdCounter.incrementAndGet()
        val payload = JSONObject().apply {
            put("type", "SEEK")
            put("requestId", reqId)
            put("mediaSessionId", currentMediaSessionId)
            put("currentTime", positionSeconds)
        }

        return sendMediaCommand(reqId, payload)
    }

    suspend fun getMediaStatus(): Result<JSONObject> {
        if (!isSessionReady()) {
            return Result.failure(IOException("Session is not connected"))
        }

        val reqId = requestIdCounter.incrementAndGet()
        val payload = JSONObject().apply {
            put("type", "GET_STATUS")
            put("requestId", reqId)
        }

        val deferred = CompletableDeferred<JSONObject>()
        pendingMediaRequests[reqId] = deferred

        val sendResult = sendRawMediaMessage(payload)
        if (sendResult.isFailure) {
            pendingMediaRequests.remove(reqId)?.cancel()
            return Result.failure(sendResult.exceptionOrNull()!!)
        }

        return try {
            val response = withTimeout(5000) { deferred.await() }
            Result.success(response)
        } catch (e: TimeoutCancellationException) {
            pendingMediaRequests.remove(reqId)?.cancel()
            Result.failure(IOException("Media status request timed out"))
        } catch (e: Exception) {
            pendingMediaRequests.remove(reqId)?.cancel()
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun sendMediaCommand(reqId: Int, payload: JSONObject): Result<Unit> {
        val deferred = CompletableDeferred<JSONObject>()
        pendingMediaRequests[reqId] = deferred

        val sendResult = sendRawMediaMessage(payload)
        if (sendResult.isFailure) {
            pendingMediaRequests.remove(reqId)?.cancel()
            return Result.failure(sendResult.exceptionOrNull()!!)
        }

        return try {
            withTimeout(10000) { deferred.await() }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            pendingMediaRequests.remove(reqId)?.cancel()
            Result.failure(IOException("Media command timed out"))
        } catch (e: Exception) {
            pendingMediaRequests.remove(reqId)?.cancel()
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun sendRawMediaMessage(payload: JSONObject): Result<Unit> {
        val msg = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(SENDER_ID)
            .setDestinationId(transportId)
            .setNamespace(MEDIA_NAMESPACE)
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8(payload.toString())
            .build()

        return session.sendMessage(msg)
    }

    private fun isSessionReady(): Boolean {
        val state = session.connectionState.value
        return state == GoogleCastSession.State.CONNECTED || state == GoogleCastSession.State.AUTHENTICATED
    }

    private fun updatePlaybackState(state: CastPlaybackState) {
        _playbackState.value = state
        onPlaybackStateChanged(state)
    }

    fun close() {
        pendingMediaRequests.values.forEach { it.cancel() }
        pendingMediaRequests.clear()
        controllerJob.cancel()
    }

    companion object {
        private const val TAG = "GoogleCastMediaCtrl"
        const val MEDIA_NAMESPACE = "urn:x-cast:com.google.cast.media"
        const val SENDER_ID = "sender-smartclick"
    }
}
