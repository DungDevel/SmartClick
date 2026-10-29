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
import kotlinx.coroutines.suspendCancellableCoroutine
import com.example.smartclick.app.tvconnection.utils.NetworkSecurityUtils

class LgWebOsController(
    private val clientKey: String?,
    private val okHttpClient: OkHttpClient
) : ITvController {

    private var webSocket: WebSocket? = null
    private var pointerSocket: WebSocket? = null
    private var pointerSocketReady: Boolean = false

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    private var _device: TvDevice? = null
    override val device: TvDevice? get() = _device

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.KEYBOARD_TEXT,
        TvCapability.INSTALLED_APPS,
        TvCapability.POINTER,
        TvCapability.CHANNEL_CONTROL,
        TvCapability.BACKSPACE,
        TvCapability.MUTE_ON_OFF,
        TvCapability.POWER_OFF
    )

    private var commandIdCounter = 1
    private val pendingRequests = mutableMapOf<String, kotlinx.coroutines.CancellableContinuation<JSONObject>>()

    override suspend fun connect(device: TvDevice): Result<Unit> = suspendCancellableCoroutine { cont ->
        if (!NetworkSecurityUtils.isPrivateLanAddress(device.ipAddress)) {
            cont.resume(Result.failure(Exception("Connection rejected: SSRF protection (Public IP skipped)")))
            return@suspendCancellableCoroutine
        }
        if (_connectionState.value is TvConnectionState.Connected) {
            cont.resume(Result.success(Unit))
            return@suspendCancellableCoroutine
        }

        _device = device
        _connectionState.value = TvConnectionState.Connecting(ConnectProgress.CONNECTING_SOCKET)

        val wsUrl = "wss://${device.ipAddress}:3001"
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
                    val id = json.optString("id")

                    if (id.isNotEmpty()) {
                        val continuation = pendingRequests.remove(id)
                        continuation?.resume(json)
                    }

                    if (type == "registered") {
                        if (!hasResumed) {
                            hasResumed = true
                            _connectionState.value = TvConnectionState.Connected(device)
                            cont.resume(Result.success(Unit))
                        }
                        setupPointerInputSocket(webSocket)
                    } else if (type == "error") {
                        val errorDesc = json.optString("error", "Unknown error")
                        if (!hasResumed) {
                            hasResumed = true
                            _connectionState.value = TvConnectionState.Failed(errorDesc)
                            cont.resume(Result.failure(Exception("Rejected by TV: $errorDesc")))
                        }
                    } else if (type == "response") {
                        val payload = json.optJSONObject("payload")
                        if (payload?.has("socketPath") == true) {
                            val socketPath = payload.getString("socketPath")
                            connectPointerSocket(socketPath)
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
                pointerSocket?.close(1000, "Main closed")
                pointerSocket = null
                pointerSocketReady = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = TvConnectionState.Failed(t.message ?: "Network error")
                if (!hasResumed) {
                    hasResumed = true
                    cont.resume(Result.failure(t))
                }
                pointerSocket?.close(1000, "Main failed")
                pointerSocket = null
                pointerSocketReady = false
            }
        }

        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    private fun setupPointerInputSocket(webSocket: WebSocket) {
        val req = JSONObject().apply {
            put("type", "request")
            put("id", "req_pointer")
            put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
        }
        webSocket.send(req.toString())
    }

    private fun connectPointerSocket(socketPath: String) {
        val request = Request.Builder().url(socketPath).build()
        pointerSocketReady = false
        pointerSocket = okHttpClient.newWebSocket(request, object: WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                pointerSocket = webSocket
                pointerSocketReady = true
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                clearPointerSocketIfCurrent(webSocket)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                clearPointerSocketIfCurrent(webSocket)
            }
        })
    }

    private fun clearPointerSocketIfCurrent(socket: WebSocket) {
        if (pointerSocket === socket) {
            pointerSocket = null
            pointerSocketReady = false
        }
    }

    private fun sendPointerFrame(frame: String): Result<Unit> {
        val socket = pointerSocket?.takeIf { pointerSocketReady }
            ?: return Result.failure(Exception("Pointer socket not ready"))
        return if (socket.send(frame)) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to send pointer socket payload"))
        }
    }

    override suspend fun disconnect() {
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        pointerSocket?.close(1000, "User requested disconnect")
        pointerSocket = null
        pointerSocketReady = false
        _connectionState.value = TvConnectionState.Idle
        _device = null
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        val ws = webSocket ?: return Result.failure(Exception("Not connected"))


        val mappedUri = LgKeyMapper.mapCommand(command)

        if (mappedUri != null) {
            val req = JSONObject().apply {
                put("type", "request")
                put("id", commandIdCounter++.toString())
                put("uri", mappedUri)
            }
            val sent = ws.send(req.toString())
            return if (sent) Result.success(Unit) else Result.failure(Exception("Failed to send socket payload"))
        }

        // Pointer socket commands for Dpad
        val pointerKey = when (command) {
            RemoteCommand.DPadUp -> "UP"
            RemoteCommand.DPadDown -> "DOWN"
            RemoteCommand.DPadLeft -> "LEFT"
            RemoteCommand.DPadRight -> "RIGHT"
            RemoteCommand.Ok -> "ENTER"
            RemoteCommand.Back -> "BACK"
            RemoteCommand.Home -> "HOME"
            is RemoteCommand.PointerMove -> null // Special case if we implement actual mouse motion
            is RemoteCommand.PointerClick -> "ENTER"
            else -> null
        }

        if (pointerKey != null) {
            return sendPointerFrame("type:button\nname:$pointerKey\n\n")
        }

        when (command) {
            RemoteCommand.Mute,
            RemoteCommand.MuteOn,
            RemoteCommand.MuteOff -> {
                val req = JSONObject().apply {
                    put("type", "request")
                    put("id", commandIdCounter++.toString())
                    put("uri", "ssap://audio/setMute")
                    // Note: Ideally toggle requires reading current state. We pass true to force mute for now.
                    put("payload", JSONObject().apply { put("mute", command != RemoteCommand.MuteOff) })
                }
                val sent = ws.send(req.toString())
                return if (sent) Result.success(Unit) else Result.failure(Exception("Failed to send mute payload"))
            }
            is RemoteCommand.PointerMove -> {
                return sendPointerFrame("type:move\ndx:${command.dx}\ndy:${command.dy}\ndown:0\n\n")
            }
            is RemoteCommand.Text -> {
                 val req = JSONObject().apply {
                    put("type", "request")
                    put("id", commandIdCounter++.toString())
                    put("uri", "ssap://com.webos.service.ime/insertText")
                    put("payload", JSONObject().apply {
                        put("text", command.text)
                        put("replace", 0)
                    })
                }
                ws.send(req.toString())
                return Result.success(Unit)
            }
            RemoteCommand.PowerOff, RemoteCommand.Power -> {
                val req = JSONObject().apply {
                    put("type", "request")
                    put("id", commandIdCounter++.toString())
                    put("uri", "ssap://system/turnOff")
                }
                val sent = ws.send(req.toString())
                return if (sent) Result.success(Unit) else Result.failure(Exception("Failed to send turnOff payload"))
            }
            RemoteCommand.Backspace -> {
                 val req = JSONObject().apply {
                    put("type", "request")
                    put("id", commandIdCounter++.toString())
                    put("uri", "ssap://com.webos.service.ime/deleteCharacters")
                    put("payload", JSONObject().apply {
                        put("count", 1)
                    })
                }
                ws.send(req.toString())
                return Result.success(Unit)
            }
            else -> return Result.failure(Exception("Unsupported command on LG WebOS"))
        }
    }

    override suspend fun getApps(): Result<List<TvApp>> {
        val ws = webSocket ?: return Result.failure(Exception("Not connected"))

        val id = commandIdCounter++.toString()
        val req = JSONObject().apply {
            put("type", "request")
            put("id", id)
            put("uri", "ssap://com.webos.applicationManager/listApps")
        }

        return try {
            val responseJson = suspendCancellableCoroutine<JSONObject> { cont ->
                pendingRequests[id] = cont
                if (!ws.send(req.toString())) {
                    pendingRequests.remove(id)
                    cont.resumeWith(Result.failure(Exception("Failed to send socket payload")))
                }
                cont.invokeOnCancellation {
                    pendingRequests.remove(id)
                }
            }

            val payload = responseJson.optJSONObject("payload")
            val appsArray = payload?.optJSONArray("apps")
            val resultList = mutableListOf<TvApp>()

            if (appsArray != null) {
                for (i in 0 until appsArray.length()) {
                    val appObj = appsArray.optJSONObject(i) ?: continue
                    val appId = appObj.optString("id")
                    val title = appObj.optString("title")
                    val iconUrl = appObj.optString("icon")
                    if (appId.isNotEmpty() && title.isNotEmpty()) {
                        resultList.add(TvApp(id = appId, name = title, iconUrl = iconUrl))
                    }
                }
            }
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun launchApp(appId: String): Result<Unit> {
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
