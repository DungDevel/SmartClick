package com.example.smartclick.app.tvconnection.vidaa

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Hisense VIDAA Driver (DRV-06).
 * Restricted/Blocked status due to proprietary MQTT/TLS client certificate requirements.
 */
class VidaaDriver @Inject constructor() : IProtocolDriver {

    override val protocol: TvProtocol = TvProtocol.VIDAA_MQTT

    override val capabilities: Set<TvCapability> = emptySet()

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? {
        if (candidate.protocolHint == TvProtocol.VIDAA_MQTT) {
            return Fingerprint(
                protocol = TvProtocol.VIDAA_MQTT,
                stableId = "vidaa-${candidate.ipAddress}",
                displayName = "Hisense VIDAA TV",
                confidence = Fingerprint.Confidence.LOW,
                macAddress = ""
            )
        }
        return null
    }

    override fun pair(device: TvDevice): Flow<PairingStep> {
        return flowOf(
            PairingStep.Instruction("vidaa_unsupported_protocol_instruction"),
            PairingStep.Failed(PairingFailure.NetworkControlDisabled("vidaa_unsupported_protocol"))
        )
    }

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        throw UnsupportedOperationException("Hisense VIDAA driver is currently blocked due to missing legal/authenticated MQTT client certificates.")
    }
}
