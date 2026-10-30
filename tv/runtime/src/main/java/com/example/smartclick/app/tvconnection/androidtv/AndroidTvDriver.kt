package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.connection.ITvCredentialStore
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
    private val credentialStore: ITvCredentialStore?

    @Inject
    constructor(localMediaServer: LocalMediaServer?, credentialStore: ITvCredentialStore? = null) {
        this.localMediaServer = localMediaServer
        this.credentialStore = credentialStore
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

        val values = credentials?.protocolValues.orEmpty()
        val expectedPin = values[CAST_TLS_SHA256]
        val pinWasEstablished = values[CAST_TLS_PIN_ESTABLISHED] == "true"
        val tlsTrustManager = com.example.smartclick.app.tvconnection.security.GoogleCastTlsTrustManager(
            expectedSha256 = expectedPin,
            firstUseAllowed = !pinWasEstablished && expectedPin.isNullOrBlank()
        )

        // 2. Cast DeviceAuth Trust Manager (for AuthResponse chain validation)
        val castTrustManager = GoogleCastTrustManager(GoogleCastRootCertificates.getDefaultRoots())

        val sslContext = SSLContext.getInstance("TLSv1.2").apply {
            init(null, arrayOf(tlsTrustManager), java.security.SecureRandom())
        }
        val authManager = GoogleCastAuthManager(castTrustManager)

        val controller = GoogleCastController(
            ipAddress = device.ipAddress,
            localMediaServer = mediaServer,
            sslSocketFactory = sslContext.socketFactory,
            authManager = authManager,
            onDeviceAuthenticated = { leafSha256 ->
                val ref = device.credentialsRef ?: throw IllegalStateException("Cast TLS pin cannot be saved without credential reference")
                val store = credentialStore ?: throw IllegalStateException("Credential store unavailable for Cast TLS pin")
                val updated = values.toMutableMap().apply {
                    put(CAST_TLS_SHA256, leafSha256)
                    put(CAST_TLS_PIN_ESTABLISHED, "true")
                }
                if (!store.updateCredentials(device.id, ref, Credentials(updated))) {
                    throw IllegalStateException("Cast TLS credential record no longer exists")
                }
            }
        )
        return controller
    }

    companion object {
        const val CAST_TLS_SHA256 = "cast_tls_sha256"
        const val CAST_TLS_PIN_ESTABLISHED = "cast_tls_pin_established"
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
