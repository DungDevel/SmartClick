package com.example.smartclick.app.tvconnection

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
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class FakeDriver @Inject constructor() : IProtocolDriver {
    override val protocol: TvProtocol = TvProtocol.SAMSUNG_TIZEN

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.DIGITS,
        TvCapability.POINTER,
        TvCapability.KEYBOARD_TEXT,
        TvCapability.INSTALLED_APPS
    )

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? {
        if (candidate.protocolHint == TvProtocol.UNKNOWN) return null
        return Fingerprint(
            protocol = candidate.protocolHint,
            stableId = "fake-device-${candidate.ipAddress}",
            displayName = "Fake Tv Device",
            confidence = Fingerprint.Confidence.MEDIUM,
            macAddress = "00:11:22:33:44:55"
        )
    }

    override fun pair(device: TvDevice): Flow<PairingStep> {
        return flowOf(PairingStep.NotRequired, PairingStep.Succeeded(null))
    }

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        return FakeTvController()
    }
}
