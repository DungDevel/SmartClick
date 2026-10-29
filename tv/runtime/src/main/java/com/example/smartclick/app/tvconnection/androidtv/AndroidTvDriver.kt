package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.tvconnection.cast.LocalMediaServer
import com.example.smartclick.app.tvconnection.cast.google.GoogleCastAuthManager
import com.example.smartclick.app.tvconnection.cast.google.GoogleCastController
import com.example.smartclick.app.tvconnection.security.GoogleCastRootCertificates
import com.example.smartclick.app.tvconnection.security.GoogleCastTrustManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.FlowCollector
import java.security.SecureRandom
import javax.inject.Inject
import javax.net.ssl.SSLContext

class AndroidTvDriver : IProtocolDriver {
    private val localMediaServer: LocalMediaServer?

    @Inject
    constructor(localMediaServer: LocalMediaServer?) {
        this.localMediaServer = localMediaServer
    }

    constructor() : this(null)

    override val protocol: TvProtocol = TvProtocol.ANDROID_TV_V2

    override val capabilities: Set<TvCapability> = ANDROID_TV_CAPABILITIES

    private var pairingSession: AndroidTvPairingSession? = null

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? {
        if (candidate.protocolHint == protocol) {
            // val pairingPort = candidate.openPorts.firstOrNull() ?: 6467
            // Extract a stable ID from attributes if available (TXT records), or fallback to ip
            val stableId = candidate.attributes["btmac"] ?: candidate.attributes["id"] ?: candidate.ipAddress
            return Fingerprint(
                protocol = protocol,
                stableId = stableId,
                displayName = candidate.serviceName ?: "Android TV",
                confidence = Fingerprint.Confidence.HIGH,
                macAddress = candidate.attributes["mac"] ?: candidate.attributes["btmac"] ?: ""
            )
        }
        return null
    }

    override suspend fun providePin(pin: String) {
        pairingSession?.providePin(pin)
    }

    override fun pair(device: TvDevice): Flow<PairingStep> = channelFlow {
        val pairingPort = 6467
        val session = AndroidTvPairingSession(device.ipAddress, pairingPort, "SmartClick", "atvremote")
        pairingSession = session
        try {
            session.startPairing(object : FlowCollector<PairingStep> {
                override suspend fun emit(value: PairingStep) {
                    send(value)
                }
            })
        } finally {
            pairingSession = null
        }
    }

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        val controlPort = 6466 // Usually the control port is exactly 6466, according to most sources
        return AndroidTvRemoteSession(device.ipAddress, controlPort, AndroidTvCertificateManager.getOrGenerateClientKeyAndCertificate(), credentials?.protocolValues?.get("cert_sha256"))
    }

    override fun createCastController(device: TvDevice, credentials: Credentials?): ICastController? {
        val mediaServer = localMediaServer ?: return null

        // 1. TLS Trust Manager (for transport-level security on port 8009)
        // Production implementation should pin the Cast device's TLS certificate.
        // Currently using TOFU approach for the TLS layer.
        val tlsTrustManager = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {
                // TODO: Implement actual pinning or CA validation for TLS transport
            }
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }

        // 2. Cast DeviceAuth Trust Manager (for AuthResponse chain validation)
        val castTrustManager = GoogleCastTrustManager(GoogleCastRootCertificates.getDefaultRoots())

        val sslContext = SSLContext.getInstance("TLSv1.2").apply {
            init(null, arrayOf(tlsTrustManager), java.security.SecureRandom())
        }
        val authManager = GoogleCastAuthManager(castTrustManager)

        return GoogleCastController(
            ipAddress = device.ipAddress,
            localMediaServer = mediaServer,
            sslSocketFactory = sslContext.socketFactory,
            authManager = authManager
        )
    }

    companion object {
        val ANDROID_TV_CAPABILITIES = setOf(
            TvCapability.VOLUME,
            TvCapability.CHANNEL_CONTROL,
            TvCapability.OPEN_SEARCH_UI,
            TvCapability.DIGITS,
            TvCapability.DPAD,
            TvCapability.BACKSPACE, TvCapability.POWER_TOGGLE,
            TvCapability.CAST_VIDEO,
            TvCapability.CAST_AUDIO,
            TvCapability.CAST_IMAGE,
            TvCapability.CAST_PLAYBACK,
            TvCapability.CAST_SEEK
            // Removed KEYBOARD_TEXT, INSTALLED_APPS, POINTER, SEARCH_CONTENT due to over-promising
        )
    }
}
