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
import com.example.smartclick.app.tvconnection.security.PinningTrustManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.security.SecureRandom
import javax.inject.Inject
import javax.net.ssl.SSLContext

class AndroidTvDriver @Inject constructor(
    private val localMediaServer: LocalMediaServer? = null
) : IProtocolDriver {

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

    override fun pair(device: TvDevice): Flow<PairingStep> = flow {
        val pairingPort = 6467
        val session = AndroidTvPairingSession(device.ipAddress, pairingPort, "SmartClick", "atvremote")
        pairingSession = session
        try {
            session.startPairing(this)
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

        val certSha256 = credentials?.protocolValues?.get("cert_sha256")
        val trustManager = if (certSha256 != null) {
            PinningTrustManager(certSha256)
        } else {
            // TOFU: accept any peer certificate on first connection.
            // Production-grade Cast authentication is NOT yet implemented.
            PinningTrustManager(null)
        }
        val sslContext = SSLContext.getInstance("TLSv1.2").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        val authManager = GoogleCastAuthManager(trustManager)

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
