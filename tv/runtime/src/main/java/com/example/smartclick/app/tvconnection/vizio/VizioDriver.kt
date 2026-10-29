package com.example.smartclick.app.tvconnection.vizio

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.PairingFailure
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

class VizioDriver @Inject constructor(
    private val httpClient: OkHttpClient
) : IProtocolDriver {

    override val protocol: TvProtocol = TvProtocol.VIZIO_SMARTCAST

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.MUTE_ON_OFF,
        TvCapability.POWER_TOGGLE,
        TvCapability.POWER_ON,
        TvCapability.POWER_OFF,
        TvCapability.DPAD
    )

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? = withContext(Dispatchers.IO) {
        if (candidate.protocolHint != TvProtocol.UNKNOWN && candidate.protocolHint != TvProtocol.VIZIO_SMARTCAST) {
            return@withContext null
        }

        if (candidate.openPorts.contains(7345) || candidate.openPorts.contains(9000) || candidate.protocolHint == TvProtocol.VIZIO_SMARTCAST) {
            return@withContext Fingerprint(
                protocol = TvProtocol.VIZIO_SMARTCAST,
                stableId = "vizio-${candidate.ipAddress}",
                displayName = candidate.serviceName ?: "Vizio SmartCast TV",
                confidence = if (candidate.openPorts.contains(7345)) Fingerprint.Confidence.HIGH else Fingerprint.Confidence.LOW,
                macAddress = candidate.attributes["mac"] ?: ""
            )
        }
        null
    }

    override fun pair(device: TvDevice): Flow<PairingStep> = flow {
        emit(PairingStep.EnterPin())
        try {
            val jsonPayload = JSONObject().apply {
                put("DEVICE_ID", "SmartClickApp")
                put("DEVICE_NAME", "SmartClick Remote")
            }
            val request = Request.Builder()
                .url("https://${device.ipAddress}:7345/pairing/start")
                .put(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    emit(PairingStep.ConfirmOnTv(30_000L))
                } else {
                    emit(PairingStep.Failed(PairingFailure.RejectedOnTv))
                }
            }
        } catch (e: Exception) {
            emit(PairingStep.Failed(PairingFailure.Network(e.message ?: "Vizio pairing failed")))
        }
    }

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        val token = credentials?.protocolValues?.get("auth_token")
        return VizioController(device.ipAddress, token, httpClient)
    }
}
