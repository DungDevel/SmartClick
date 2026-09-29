package com.example.smartclick.app.tvconnection.samsung

import com.example.smartclick.app.domain.connection.ConnectProgress
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SamsungTizenController(
    private val appNameBase64: String,
    private val token: String?,
    private val okHttpClient: OkHttpClient
) : ITvController {

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    private var _device: TvDevice? = null
    override val device: TvDevice? get() = _device

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.DIGITS
    )

    override suspend fun connect(device: TvDevice): Result<Unit> = suspendCoroutine { cont ->
        if (_connectionState.value is TvConnectionState.Connected) {
            cont.resume(Result.success(Unit))
            return@suspendCoroutine
        }

        _device = device
        _connectionState.value = TvConnectionState.Connecting(ConnectProgress.CONNECTING_SOCKET)

        val urlBuilder = StringBuilder("wss://${device.ipAddress}:8002/api/v2/channels/samsung.remote.control?name=$appNameBase64")
        if (token != null) {
            urlBuilder.append("&token=$token")
        }

        val request = Request.Builder().url(urlBuilder.toString()).build()
        var hasResumed = false

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Wait for ms.channel.connect event to confirm authorization
                // But WS connection is established. It might not be fully paired if the token is rejected.
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val event = json.optString("event")
                    if (event == "ms.channel.connect") {
                        if (!hasResumed) {
                            hasResumed = true
                            _connectionState.value = TvConnectionState.Connected(device)
                            cont.resume(Result.success(Unit))
                        }
                    } else if (event == "ms.channel.unauthorized") {
                        if (!hasResumed) {
                            hasResumed = true
                            _connectionState.value = TvConnectionState.Failed("Unauthorized")
                            cont.resume(Result.failure(Exception("Unauthorized by TV")))
                        }
                    }
                } catch (e: Exception) {
                    // Ignore irrelevant messages
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = TvConnectionState.Idle
                if (!hasResumed) {
                    hasResumed = true
                    cont.resume(Result.failure(Exception("Closed: $reason")))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = TvConnectionState.Failed(t.message ?: "Network error")
                if (!hasResumed) {
                    hasResumed = true
                    cont.resume(Result.failure(t))
                }
            }
        }

        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    override suspend fun disconnect() {
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        _connectionState.value = TvConnectionState.Idle
        _device = null
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        val keyCode = SamsungKeyMapper.mapCommand(command)
            ?: return Result.failure(Exception("Unsupported command"))

        val ws = webSocket ?: return Result.failure(Exception("Not connected"))

        val payload = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", keyCode)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }

        val sent = ws.send(payload.toString())
        return if (sent) Result.success(Unit) else Result.failure(Exception("Failed to send socket payload"))
    }
}
