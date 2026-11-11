package com.example.smartclick.app.tvconnection.sony

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class SonyBraviaDriver @Inject constructor(
    private val httpClient: OkHttpClient
) : IProtocolDriver {

    override val protocol: TvProtocol = TvProtocol.SONY_BRAVIA

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.MUTE_ON_OFF,
        TvCapability.POWER_TOGGLE,
        TvCapability.POWER_ON,
        TvCapability.POWER_OFF,
        TvCapability.DPAD,
        TvCapability.CHANNEL_CONTROL
    )

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? = withContext(Dispatchers.IO) {
        if (candidate.protocolHint != TvProtocol.UNKNOWN && candidate.protocolHint != TvProtocol.SONY_BRAVIA) {
            return@withContext null
        }

        try {
            val requestBody = "{\"method\":\"getInterfaceInformation\",\"id\":33,\"params\":[],\"version\":\"1.0\"}".toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("http://${candidate.ipAddress}/sony/system")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                if (json.opt("id") != 33) return@withContext null

                val result = json.opt("result") as? JSONArray
                val firstResult = result?.optJSONObject(0)
                val confidence = when {
                    firstResult?.has("interfaceVersion") == true -> Fingerprint.Confidence.HIGH
                    json.opt("error") is JSONArray -> Fingerprint.Confidence.MEDIUM
                    else -> return@withContext null
                }

                return@withContext Fingerprint(
                    protocol = TvProtocol.SONY_BRAVIA,
                    stableId = "sony-${candidate.ipAddress}",
                    displayName = candidate.serviceName ?: "Sony Bravia TV",
                    confidence = confidence,
                    macAddress = candidate.attributes["mac"] ?: ""
                )
            }
        } catch (_: Exception) {
            return@withContext null
        }
        null
    }

    override fun pair(device: TvDevice): Flow<PairingStep> {
        return flowOf(PairingStep.Instruction("sony_psk_instruction"), PairingStep.Succeeded(null))
    }

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        val psk = credentials?.protocolValues?.get("psk")
        return SonyBraviaController(device.ipAddress, psk, httpClient)
    }
}
