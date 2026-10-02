package com.example.smartclick.app.tvconnection.samsung

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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import android.util.Base64
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SamsungTizenDriver @Inject constructor() : IProtocolDriver {

    override val protocol: TvProtocol = TvProtocol.SAMSUNG_TIZEN

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.DIGITS,
        TvCapability.CHANNEL_CONTROL,
        TvCapability.OPEN_SEARCH_UI
    )

    private val applicationName = "SmartClick"
    private val appNameBase64: String get() = Base64.encodeToString(applicationName.toByteArray(), Base64.NO_WRAP)

    private val tofuTrustManager = com.example.smartclick.app.tvconnection.security.PinningTrustManager(expectedSha256 = null, isPairing = true)

    private val sslContext = SSLContext.getInstance("TLSv1.2").apply {
        init(null, arrayOf<TrustManager>(tofuTrustManager), SecureRandom())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, tofuTrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? {
        val url = "http://${candidate.ipAddress}:8001/api/v2/"
        val request = Request.Builder().url(url).build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val deviceMap = json.optJSONObject("device") ?: return null

                val model = deviceMap.optString("modelName", "Samsung/Tizen TV")
                val mac = deviceMap.optString("wifiMac", candidate.ipAddress)
                val id = deviceMap.optString("id", mac) // UUID

                Fingerprint(
                    protocol = protocol,
                    stableId = id,
                    displayName = model
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun pair(device: TvDevice): Flow<PairingStep> = callbackFlow {
        // Emit ConfirmOnTv right away
        trySend(PairingStep.ConfirmOnTv(30_000L))

        val wsUrl = "wss://${device.ipAddress}:8002/api/v2/channels/samsung.remote.control?name=$appNameBase64"
        val request = Request.Builder().url(wsUrl).build()

        val listener = object : WebSocketListener() {
            var certHashToSave: String? = null
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connection opened, capture certificate hash
                val certs = response.handshake?.peerCertificates
                if (!certs.isNullOrEmpty()) {
                    val cert = certs[0]
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    certHashToSave = md.digest(cert.encoded).joinToString("") { "%02x".format(it) }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val event = json.optString("event")
                    if (event == "ms.channel.connect") {
                        val data = json.optJSONObject("data")
                        if (data != null && data.has("token")) {
                            val token = data.getString("token")
                            val credsMap = mutableMapOf("token" to token)
                            if (certHashToSave != null) {
                                credsMap["cert_sha256"] = certHashToSave!!
                            }
                            trySend(PairingStep.Succeeded(Credentials(credsMap)))
                            webSocket.close(1000, "Paired")
                            close()
                        }
                    } else if (event == "ms.channel.unauthorized") {
                        trySend(PairingStep.Failed(PairingFailure.RejectedOnTv))
                        webSocket.close(1000, "Unauthorized")
                        close()
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors for other messages
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(PairingStep.Failed(PairingFailure.Network(t.message ?: "WS Failure")))
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // The channel might already be closed on success/failure, trySend handles closing
            }
        }

        okHttpClient.newWebSocket(request, listener)

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        val token = credentials?.protocolValues?.get("token")
        val certSha256 = credentials?.protocolValues?.get("cert_sha256")
        
        val controllerClient = if (certSha256 != null) {
            try {
                val trustManager = com.example.smartclick.app.tvconnection.security.PinningTrustManager(certSha256)
                val newSslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2").apply {
                    init(null, arrayOf<javax.net.ssl.TrustManager>(trustManager), java.security.SecureRandom())
                }
                okHttpClient.newBuilder()
                    .sslSocketFactory(newSslContext.socketFactory, trustManager)
                    .build()
            } catch (e: Exception) {
                okHttpClient // Fallback to trust all if something fails, though it shouldn't
            }
        } else {
            okHttpClient
        }
        
        return SamsungTizenController(appNameBase64, token, controllerClient)
    }
}
