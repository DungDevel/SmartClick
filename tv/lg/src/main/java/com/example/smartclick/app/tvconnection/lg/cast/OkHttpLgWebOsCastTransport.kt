package com.example.smartclick.app.tvconnection.lg.cast

import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.app.tvconnection.utils.NetworkSecurityUtils
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class OkHttpLgWebOsCastTransport(
    private val okHttpClient: OkHttpClient
) : LgWebOsCastTransport {

    private var webSocket: WebSocket? = null
    private val messageIdCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<String, CancellableContinuation<Result<JSONObject>>>()

    @Volatile
    override var isConnected: Boolean = false
        private set

    override suspend fun connect(ipAddress: String, clientKey: String?): Result<Unit> = suspendCancellableCoroutine { cont ->
        if (!NetworkSecurityUtils.isPrivateLanAddress(ipAddress)) {
            cont.resume(Result.failure(CastError.NetworkError(Exception("SSRF prevention: Public IP discarded"))))
            return@suspendCancellableCoroutine
        }
        if (isConnected) {
            cont.resume(Result.success(Unit))
            return@suspendCancellableCoroutine
        }

        val wsUrl = "wss://$ipAddress:3001"
        val request = Request.Builder().url(wsUrl).build()

        var hasResumed = false

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // To authenticate, we must send a "register" message containing the clientKey
                val payload = JSONObject().apply {
                    put("forcePairing", false)
                    put("manifest", JSONObject().apply {
                        put("permissions", org.json.JSONArray(listOf(
                            "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CLOSE", "TEST_OPEN", "TEST_PROTECTED",
                            "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_RECORDING",
                            "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER", "READ_APP_STATUS",
                            "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE", "READ_RUNNING_APPS",
                            "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE", "READ_COUNTRY_INFO"
                        )))
                        put("manifestVersion", 1)
                    })
                    if (clientKey != null) {
                        put("client-key", clientKey)
                    }
                }

                val req = JSONObject().apply {
                    put("type", "register")
                    put("id", "register_cast")
                    put("payload", payload)
                }
                webSocket.send(req.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    val id = json.optString("id")

                    if (type == "registered" && id == "register_cast") {
                        if (!hasResumed) {
                            hasResumed = true
                            isConnected = true
                            cont.resume(Result.success(Unit))
                        }
                    } else if (type == "error" && id == "register_cast") {
                        if (!hasResumed) {
                            hasResumed = true
                            cont.resume(Result.failure(CastError.InvalidCredentials("Cast WS Authorization failed")))
                        }
                    } else if (id.isNotEmpty()) {
                        val pending = pendingRequests.remove(id)
                        if (pending != null && pending.isActive) {
                            if (type == "error") {
                                val errorDetail = json.optString("error", "Unknown SSAP error")
                                pending.resume(Result.failure(CastError.ProtocolCommandFailed(errorDetail)))
                            } else {
                                val responsePayload = json.optJSONObject("payload") ?: JSONObject()
                                pending.resume(Result.success(responsePayload))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore malformed messages
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                val error = CastError.NetworkError(t)

                // Fail pending requests
                pendingRequests.values.forEach { if (it.isActive) it.resume(Result.failure(error)) }
                pendingRequests.clear()

                if (!hasResumed) {
                    hasResumed = true
                    cont.resume(Result.failure(error))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                val error = CastError.NetworkError(Exception("WebSocket closed: $reason"))

                pendingRequests.values.forEach { if (it.isActive) it.resume(Result.failure(error)) }
                pendingRequests.clear()

                if (!hasResumed) {
                    hasResumed = true
                    cont.resume(Result.failure(error))
                }
            }
        }

        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    override suspend fun disconnect() {
        webSocket?.close(1000, "Disconnect requested")
        webSocket = null
        isConnected = false
        val error = CastError.NetworkError(Exception("Disconnected intentionally"))
        pendingRequests.values.forEach { if (it.isActive) it.resume(Result.failure(error)) }
        pendingRequests.clear()
    }

    override suspend fun sendRequest(uri: String, payload: JSONObject?): Result<JSONObject> = suspendCancellableCoroutine { cont ->
        val ws = webSocket
        if (!isConnected || ws == null) {
            cont.resume(Result.failure(CastError.NetworkError(Exception("Not connected"))))
            return@suspendCancellableCoroutine
        }

        val requestId = "cast_req_${messageIdCounter.getAndIncrement()}"

        val requestJson = JSONObject().apply {
            put("type", "request")
            put("id", requestId)
            put("uri", uri)
            if (payload != null) {
                put("payload", payload)
            }
        }

        pendingRequests[requestId] = cont

        cont.invokeOnCancellation {
            pendingRequests.remove(requestId)
        }

        val sent = ws.send(requestJson.toString())
        if (!sent) {
            pendingRequests.remove(requestId)
            cont.resume(Result.failure(CastError.NetworkError(Exception("Failed to enqueue WebSocket message"))))
        }
    }
}
