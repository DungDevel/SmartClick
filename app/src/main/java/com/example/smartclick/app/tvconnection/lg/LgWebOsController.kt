package com.example.smartclick.app.tvconnection.lg

import com.example.smartclick.app.domain.connection.ConnectProgress
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvApp
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

class LgWebOsController(
    private val clientKey: String?,
    private val okHttpClient: OkHttpClient
) : ITvController {

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    private var _device: TvDevice? = null
    override val device: TvDevice? get() = _device

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.KEYBOARD_TEXT,
        TvCapability.INSTALLED_APPS
    )

    private var commandIdCounter = 1

    override suspend fun connect(device: TvDevice): Result<Unit> = suspendCoroutine { cont ->
        if (_connectionState.value is TvConnectionState.Connected) {
            cont.resume(Result.success(Unit))
            return@suspendCoroutine
        }

        _device = device
        _connectionState.value = TvConnectionState.Connecting(ConnectProgress.CONNECTING_SOCKET)

        val wsUrl = "ws://${device.ipAddress}:3000"
        val request = Request.Builder().url(wsUrl).build()
        var hasResumed = false

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send register payload with clientKey
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
                    put("id", "register_0")
                    put("payload", payload)
                }
                webSocket.send(req.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    if (type == "registered") {
                        if (!hasResumed) {
                            hasResumed = true
                            _connectionState.value = TvConnectionState.Connected(device)
                            cont.resume(Result.success(Unit))
                        }
                    } else if (type == "error") {
                        val errorDesc = json.optString("error", "Unknown error")
                        if (!hasResumed) {
                            hasResumed = true
                            _connectionState.value = TvConnectionState.Failed(errorDesc)
                            cont.resume(Result.failure(Exception("Rejected by TV: $errorDesc")))
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
        val mappedUri = LgKeyMapper.mapCommand(command)
        val ws = webSocket ?: return Result.failure(Exception("Not connected"))

        if (mappedUri != null) {
            val req = JSONObject().apply {
                put("type", "request")
                put("id", commandIdCounter++.toString())
                put("uri", mappedUri)
            }
            val sent = ws.send(req.toString())
            return if (sent) Result.success(Unit) else Result.failure(Exception("Failed to send socket payload"))
        }

        // Handle specific endpoints that need payloads
        when (command) {
            RemoteCommand.Mute -> {
                val req = JSONObject().apply {
                    put("type", "request")
                    put("id", commandIdCounter++.toString())
                    put("uri", "ssap://audio/setMute")
                    put("payload", JSONObject().apply { put("mute", true) })
                }
                val sent = ws.send(req.toString())
                return if (sent) Result.success(Unit) else Result.failure(Exception("Failed to send mute payload"))
            }
            // For complex keys (D-pad), it technically needs the Pointer Input Socket for newer TVs.
            // But we will use best-effort fallback to basic URIs if available or fail.
            else -> return Result.failure(Exception("Unsupported command on LG WebOS without pointer socket"))
        }
    }

    override suspend fun getApps(): Result<List<TvApp>> {
        // ssap://com.webos.applicationManager/listLaunchPoints
        return Result.success(emptyList())
    }

    override suspend fun launchApp(appId: String): Result<Unit> {
        // ssap://system.launcher/launch
        // {"id": "appId"}
        val ws = webSocket ?: return Result.failure(Exception("Not connected"))
        val req = JSONObject().apply {
            put("type", "request")
            put("id", commandIdCounter++.toString())
            put("uri", "ssap://system.launcher/launch")
            put("payload", JSONObject().apply { put("id", appId) })
        }
        val sent = ws.send(req.toString())
        return if (sent) Result.success(Unit) else Result.failure(Exception("Failed to launch app"))
    }
}
