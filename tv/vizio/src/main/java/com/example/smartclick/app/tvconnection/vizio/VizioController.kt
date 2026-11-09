package com.example.smartclick.app.tvconnection.vizio

import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvApp
import com.example.smartclick.app.domain.connection.ConnectProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

class VizioController(
    private val ipAddress: String,
    private val authToken: String?,
    private val httpClient: OkHttpClient,
    private val probePort: Int = 7345
) : ITvController {

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    private var currentDevice: TvDevice? = null
    override val device: TvDevice? get() = currentDevice

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.MUTE_ON_OFF,
        TvCapability.POWER_TOGGLE,
        TvCapability.POWER_ON,
        TvCapability.POWER_OFF,
        TvCapability.DPAD
    )

    override suspend fun connect(device: TvDevice): Result<Unit> = withContext(Dispatchers.IO) {
        _connectionState.value = TvConnectionState.Connecting(ConnectProgress.CONNECTING_SOCKET)
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, probePort), 1500)
            }
            currentDevice = device
            _connectionState.value = TvConnectionState.Connected(device)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Connection failed: ${e.message}"
            _connectionState.value = TvConnectionState.Failed(msg)
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        _connectionState.value = TvConnectionState.Idle
        currentDevice = null
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        val mapping = VizioKeyMapper.mapCommandToPayload(command) ?: return@withContext Result.success(Unit)

        try {
            val jsonPayload = JSONObject().apply {
                put("KEYEVENT", JSONObject().apply {
                    put("CODESET", mapping.first)
                    put("CODE", mapping.second)
                    put("ACTION", 1)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url("https://$ipAddress:7345/menu_native/command/key_command")
                .put(requestBody)

            if (!authToken.isNullOrBlank()) {
                requestBuilder.header("AUTH", authToken)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Vizio HTTP Error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getApps(): Result<List<TvApp>> =
        Result.failure(UnsupportedOperationException("Get apps not supported via Vizio protocol yet"))

    override suspend fun launchApp(appId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Launch app not supported via Vizio protocol yet"))
}
