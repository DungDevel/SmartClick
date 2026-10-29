package com.example.smartclick.app.tvconnection.sony

import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SonyBraviaController(
    private val ipAddress: String,
    private val psk: String?,
    private val httpClient: OkHttpClient
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
        TvCapability.DPAD,
        TvCapability.CHANNEL_CONTROL,
        TvCapability.SEARCH_CONTENT
    )

    override suspend fun connect(device: TvDevice): Result<Unit> = withContext(Dispatchers.IO) {
        currentDevice = device
        _connectionState.value = TvConnectionState.Connected(device)
        Result.success(Unit)
    }

    override suspend fun disconnect() {
        _connectionState.value = TvConnectionState.Idle
        currentDevice = null
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        val soapBody = SonyKeyMapper.mapCommandToIrcc(command) ?: return@withContext Result.success(Unit)

        try {
            val mediaType = "text/xml; charset=utf-8".toMediaType()
            val requestBody = soapBody.toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url("http://$ipAddress/sony/IRCC")
                .post(requestBody)
                .header("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")

            if (!psk.isNullOrBlank()) {
                requestBuilder.header("X-Auth-PSK", psk)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Sony IRCC HTTP Error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
