package com.example.smartclick.app.tvconnection.lg

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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.SecureRandom
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import com.example.smartclick.app.tvconnection.cast.LocalMediaServer
import com.example.smartclick.app.tvconnection.lg.cast.LgWebOsCastController
import com.example.smartclick.app.tvconnection.lg.cast.OkHttpLgWebOsCastTransport
import com.example.smartclick.app.domain.connection.ICastController

class LgWebOsDriver @Inject constructor(
    private val localMediaServer: LocalMediaServer
) : IProtocolDriver {
    override val protocol: TvProtocol = TvProtocol.LG_WEBOS

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.KEYBOARD_TEXT,
        TvCapability.INSTALLED_APPS,
        TvCapability.POINTER,
        TvCapability.CHANNEL_CONTROL,
        TvCapability.BACKSPACE,
        TvCapability.MUTE_ON_OFF,
        TvCapability.POWER_OFF,
        TvCapability.CAST_IMAGE,
        TvCapability.CAST_VIDEO,
        TvCapability.CAST_AUDIO,
        TvCapability.CAST_PLAYBACK
    )

    private val tofuTrustManager = com.example.smartclick.app.tvconnection.security.PinningTrustManager(expectedSha256 = null, isPairing = true)

    private val sslContext = SSLContext.getInstance("TLSv1.2").apply {
        init(null, arrayOf<TrustManager>(tofuTrustManager), SecureRandom())
    }

    // Test seam: `internal var` thay cho `private val` để unit test có thể thay OkHttpClient giả
    // và kiểm thử xử lý message ghép nối mà không cần mạng thật. Production vẫn dùng TOFU client dựng ở đây
    // (giống cách LgWebOsController nhận okHttpClient qua constructor để test được).
    internal var pairingClient: OkHttpClient = OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, tofuTrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? = withContext(Dispatchers.IO) {
        if (candidate.protocolHint != TvProtocol.UNKNOWN && candidate.protocolHint != TvProtocol.LG_WEBOS) {
            return@withContext null
        }

        if (candidate.protocolHint == TvProtocol.LG_WEBOS || candidate.openPorts.contains(3000) || candidate.openPorts.contains(3001)) {
            val fallbackId = candidate.usn ?: candidate.location?.hashCode()?.toString()
            val stableId = if (fallbackId != null) "lg-$fallbackId" else "lg-${candidate.ipAddress}"
            return@withContext Fingerprint(
                protocol = TvProtocol.LG_WEBOS,
                stableId = stableId,
                displayName = candidate.serviceName ?: "LG WebOS TV",
                confidence = if (candidate.protocolHint == TvProtocol.LG_WEBOS) Fingerprint.Confidence.HIGH else Fingerprint.Confidence.LOW,
                macAddress = candidate.attributes["mac"] ?: candidate.attributes["wakeup"]?.let {
                    val macRegex = "(?i)MAC=([0-9A-F:]+)".toRegex()
                    macRegex.find(it)?.groupValues?.get(1)
                } ?: ""
            )
        }
        null
    }

    override fun pair(device: TvDevice): Flow<PairingStep> = callbackFlow {
        trySend(PairingStep.ConfirmOnTv(30_000L))

        // Prefer WSS (3001) for modern LG WebOS. Fallback logic can be implemented, but trying 3001 first is safer.
        val wsUrl = "wss://${device.ipAddress}:3001"
        val request = Request.Builder().url(wsUrl).build()

        val listener = object : WebSocketListener() {
            var certHashToSave: String? = null

            override fun onOpen(webSocket: WebSocket, response: Response) {
                val certs = response.handshake?.peerCertificates
                if (!certs.isNullOrEmpty()) {
                    val cert = certs[0]
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    certHashToSave = md.digest(cert.encoded).joinToString("") { "%02x".format(it) }
                }

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
                        val payload = json.optJSONObject("payload")
                        val clientKey = payload?.optString("client-key")
                        if (clientKey != null) {
                            val credsMap = mutableMapOf("client-key" to clientKey)
                            if (certHashToSave != null) {
                                credsMap["cert_sha256"] = certHashToSave!!
                            }
                            trySend(PairingStep.Succeeded(Credentials(credsMap)))
                            webSocket.close(1000, "Paired")
                            close()
                        }
                    } else if (type == "error") {
                        trySend(PairingStep.Failed(PairingFailure.RejectedOnTv))
                        webSocket.close(1000, "Error")
                        close()
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = t.message ?: "WS Failure"
                if (msg.contains("Connection refused", ignoreCase = true) || msg.contains("Connection reset", ignoreCase = true) || msg.contains("Socket closed", ignoreCase = true)) {
                    trySend(PairingStep.Instruction("pairing_network_control_disabled"))
                    trySend(PairingStep.Failed(PairingFailure.NetworkControlDisabled("pairing_network_control_disabled")))
                } else {
                    trySend(PairingStep.Failed(PairingFailure.Network(msg)))
                }
                close()
            }
        }

        val ws = pairingClient.newWebSocket(request, listener)

        awaitClose {
            ws.close(1000, "Collector cancelled")
        }
    }.flowOn(Dispatchers.IO)

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        val clientKey = credentials?.protocolValues?.get("client-key")
        val certSha256 = credentials?.protocolValues?.get("cert_sha256")

        val controllerClient = if (certSha256 != null) {
            try {
                val trustManager = com.example.smartclick.app.tvconnection.security.PinningTrustManager(certSha256)
                val newSslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2").apply {
                    init(null, arrayOf<javax.net.ssl.TrustManager>(trustManager), java.security.SecureRandom())
                }
                pairingClient.newBuilder()
                    .sslSocketFactory(newSslContext.socketFactory, trustManager)
                    .build()
            } catch (e: Exception) {
                pairingClient
            }
        } else {
            pairingClient
        }

        return LgWebOsController(clientKey, controllerClient)
    }

    override fun createCastController(device: TvDevice, credentials: Credentials?): ICastController {
        val clientKey = credentials?.protocolValues?.get("client-key")
        val certSha256 = credentials?.protocolValues?.get("cert_sha256")

        val transportClient = if (certSha256 != null) {
            try {
                val trustManager = com.example.smartclick.app.tvconnection.security.PinningTrustManager(certSha256)
                val newSslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2").apply {
                    init(null, arrayOf<javax.net.ssl.TrustManager>(trustManager), java.security.SecureRandom())
                }
                pairingClient.newBuilder()
                    .sslSocketFactory(newSslContext.socketFactory, trustManager)
                    .build()
            } catch (e: Exception) {
                pairingClient
            }
        } else {
            pairingClient
        }

        val transport = OkHttpLgWebOsCastTransport(transportClient)
        return LgWebOsCastController(transport, localMediaServer, device.ipAddress, clientKey)
    }
}
