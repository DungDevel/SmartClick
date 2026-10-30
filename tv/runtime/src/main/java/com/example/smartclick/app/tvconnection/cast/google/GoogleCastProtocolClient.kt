package com.example.smartclick.app.tvconnection.cast.google

import android.util.Log
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.selects.select
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handles the high-level Google Cast receiver protocol sequencing.
 * - Virtual connection to receiver-0
 * - Heartbeat (PING / PONG)
 * - Application Status (GET_STATUS / RECEIVER_STATUS)
 * - App Launch
 */
class GoogleCastProtocolClient(
    private val session: GoogleCastSession,
    private val scope: CoroutineScope
) {
    private val requestIdCounter = AtomicInteger(1)
    private val requestManager = PendingRequestManager()
    private var heartbeatJob: Job? = null
    private var lastPongTimestamp: Long = 0

    // Explicit client lifecycle management
    private val clientJob = SupervisorJob(scope.coroutineContext[Job])
    private val clientScope = scope + clientJob

    private val _receiverStatusFlow = MutableSharedFlow<JSONObject>(extraBufferCapacity = 16)
    val receiverStatusFlow: SharedFlow<JSONObject> = _receiverStatusFlow.asSharedFlow()

    init {
        // Cleanup resources when disconnected
        clientScope.launch {
            session.connectionState.collect { state ->
                if (state == GoogleCastSession.State.DISCONNECTED || state == GoogleCastSession.State.FAILED) {
                    // The read loop emits its last frame before publishing DISCONNECTED. Keep the
                    // incoming-message collector alive so a final RECEIVER_STATUS is not dropped
                    // while pending requests are being completed. The controller closes us when
                    // replacing or tearing down this session.
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                }
            }
        }

        clientScope.launch {
            session.incomingMessages.collect { msg ->
                if (msg.payloadType == CastMessage.PayloadType.STRING) {
                    try {
                        val json = JSONObject(msg.payloadUtf8)
                        val type = json.optString("type")

                        // 1. Handle Heartbeat
                        if (msg.namespace == "urn:x-cast:com.google.cast.tp.heartbeat") {
                            if (type == "PING") {
                                sendPong()
                            } else if (type == "PONG") {
                                lastPongTimestamp = System.nanoTime()
                            }
                            return@collect
                        }

                        // 2. Handle Receiver Protocol
                        if (msg.namespace == "urn:x-cast:com.google.cast.receiver") {
                            when (type) {
                                "RECEIVER_STATUS" -> {
                                    val requestId = json.optInt("requestId", 0)
                                    _receiverStatusFlow.tryEmit(json)
                                    requestManager.completeStatusRequest(requestId, json)

                                    val apps = json.optJSONArray("applications")
                                        ?: json.optJSONObject("status")?.optJSONArray("applications")
                                    if (apps != null) {
                                        for (i in 0 until apps.length()) {
                                            val appId = apps.getJSONObject(i).optString("appId")
                                            if (appId.isNotEmpty()) {
                                                requestManager.completeAppLaunchConfirmation(appId, json)
                                            }
                                        }
                                    }
                                }
                                "LAUNCH_STATUS" -> {
                                    val launchRequestId = json.optInt("launchRequestId", 0)
                                    requestManager.completeLaunchStatusRequest(launchRequestId, json)
                                }
                                "LAUNCH_ERROR" -> {
                                    val requestId = json.optInt("requestId", 0)
                                    requestManager.completeLaunchErrorRequest(requestId, json)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GoogleCastProtocolClient", "Malformed message received: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * Shuts down the client, cancelling all background collectors and heartbeat jobs.
     */
    fun close() {
        cleanup()
    }

    private fun cleanup() {
        heartbeatJob?.cancel()
        requestManager.clearAll()
        clientJob.cancel()
    }

    suspend fun connectToReceiver() {
        val msg = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(SENDER_ID)
            .setDestinationId(RECEIVER_ID)
            .setNamespace("urn:x-cast:com.google.cast.tp.connection")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8("{\"type\": \"CONNECT\"}")
            .build()
        session.sendMessage(msg)
    }

    suspend fun getStatus(): JSONObject? {
        val reqId = requestIdCounter.incrementAndGet()
        // Register before sending to avoid race conditions
        val deferred = requestManager.registerStatusRequest(reqId)

        val msg = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(SENDER_ID)
            .setDestinationId(RECEIVER_ID)
            .setNamespace("urn:x-cast:com.google.cast.receiver")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8("{\"type\": \"GET_STATUS\", \"requestId\": $reqId}")
            .build()

        session.sendMessage(msg)

        return try {
            withTimeout(5000) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        } finally {
            requestManager.clearRequest(reqId)
        }
    }

    suspend fun setVolume(level: Float): Result<Unit> {
        val reqId = requestIdCounter.incrementAndGet()
        val deferred = requestManager.registerStatusRequest(reqId)

        val payload = JSONObject().apply {
            put("type", "SET_VOLUME")
            put("requestId", reqId)
            put("volume", JSONObject().apply {
                put("level", level.coerceIn(0f, 1f).toDouble())
            })
        }

        val msg = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(SENDER_ID)
            .setDestinationId(RECEIVER_ID)
            .setNamespace("urn:x-cast:com.google.cast.receiver")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8(payload.toString())
            .build()

        session.sendMessage(msg)

        return try {
            withTimeout(5000) {
                deferred.await()
                Result.success(Unit)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(java.io.IOException("SET_VOLUME timed out"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            requestManager.clearRequest(reqId)
        }
    }

    suspend fun setMute(muted: Boolean): Result<Unit> {
        val reqId = requestIdCounter.incrementAndGet()
        val deferred = requestManager.registerStatusRequest(reqId)

        val payload = JSONObject().apply {
            put("type", "SET_VOLUME")
            put("requestId", reqId)
            put("volume", JSONObject().apply {
                put("muted", muted)
            })
        }

        val msg = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(SENDER_ID)
            .setDestinationId(RECEIVER_ID)
            .setNamespace("urn:x-cast:com.google.cast.receiver")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8(payload.toString())
            .build()

        session.sendMessage(msg)

        return try {
            withTimeout(5000) {
                deferred.await()
                Result.success(Unit)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(java.io.IOException("SET_VOLUME (mute) timed out"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            requestManager.clearRequest(reqId)
        }
    }

    suspend fun launchApp(appId: String): JSONObject? {
        val reqId = requestIdCounter.incrementAndGet()

        val errorDeferred = requestManager.registerLaunchErrorRequest(reqId)
        requestManager.registerLaunchStatusRequest(reqId) // Only for completeness
        val appConfirmationDeferred = requestManager.registerAppLaunchConfirmation(appId)

        val msg = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(SENDER_ID)
            .setDestinationId(RECEIVER_ID)
            .setNamespace("urn:x-cast:com.google.cast.receiver")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8("{\"type\": \"LAUNCH\", \"appId\": \"$appId\", \"requestId\": $reqId}")
            .build()

        session.sendMessage(msg)

        return try {
            withTimeout(15000) {
                select<JSONObject?> {
                    // Prefer a valid receiver status if the socket disconnects just after
                    // broadcasting it: cleanup cancels the competing error deferred.
                    appConfirmationDeferred.onAwait { it }
                    errorDeferred.onAwait {
                        throw java.io.IOException("Launch error: ${it.optString("reason")}")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: Exception) {
            if (e is java.io.IOException) throw e
            if (e is CancellationException) throw e
            null
        } finally {
            requestManager.clearRequest(reqId)
            requestManager.clearAppLaunchConfirmation(appId)
        }
    }

    suspend fun connectToApp(transportId: String) {
        if (transportId.isBlank()) {
            throw IllegalArgumentException("Invalid transportId")
        }

        val msg = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(SENDER_ID)
            .setDestinationId(transportId)
            .setNamespace("urn:x-cast:com.google.cast.tp.connection")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8("{\"type\": \"CONNECT\"}")
            .build()
        session.sendMessage(msg)
    }

    fun startHeartbeat(intervalMs: Long = 5000L, timeoutNanos: Long = 15L * 1_000_000_000L) {
        heartbeatJob?.cancel()
        heartbeatJob = clientScope.launch {
            lastPongTimestamp = System.nanoTime()

            while (isActive) {
                delay(intervalMs)

                val state = session.connectionState.value
                if (state != GoogleCastSession.State.CONNECTED &&
                    state != GoogleCastSession.State.AUTHENTICATED) {
                    break
                }

                // Check for timeout using monotonic clock
                if (System.nanoTime() - lastPongTimestamp > timeoutNanos) {
                    Log.w("GoogleCastProtocolClient", "Receiver heartbeat timeout. Disconnecting.")
                    session.disconnect() // This triggers FAILED state
                    break
                }

                val msg = CastMessage.newBuilder()
                    .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
                    .setSourceId(SENDER_ID)
                    .setDestinationId(RECEIVER_ID)
                    .setNamespace("urn:x-cast:com.google.cast.tp.heartbeat")
                    .setPayloadType(CastMessage.PayloadType.STRING)
                    .setPayloadUtf8("{\"type\": \"PING\"}")
                    .build()
                session.sendMessage(msg)
            }
        }
    }

    private suspend fun sendPong() {
        val msg = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(SENDER_ID)
            .setDestinationId(RECEIVER_ID)
            .setNamespace("urn:x-cast:com.google.cast.tp.heartbeat")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8("{\"type\": \"PONG\"}")
            .build()
        session.sendMessage(msg)
    }

    companion object {
        const val SENDER_ID = "sender-smartclick"
        const val RECEIVER_ID = "receiver-0"
    }
}
