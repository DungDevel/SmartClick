package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class AndroidTvDriver @Inject constructor() : IProtocolDriver {

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
                confidence = Fingerprint.Confidence.HIGH
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

    companion object {
        val ANDROID_TV_CAPABILITIES = setOf(
            TvCapability.VOLUME,
            TvCapability.CHANNEL_CONTROL,
            TvCapability.OPEN_SEARCH_UI,
            TvCapability.DIGITS,
            TvCapability.BACKSPACE
            // Removed KEYBOARD_TEXT, INSTALLED_APPS, POINTER, SEARCH_CONTENT due to over-promising
        )
    }
}
