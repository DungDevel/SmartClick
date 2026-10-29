package com.example.smartclick.app.tvconnection.firetv

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
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

class FireTvDriver @Inject constructor() : IProtocolDriver {

    override val protocol: TvProtocol = TvProtocol.FIRE_TV

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.MUTE_ON_OFF,
        TvCapability.POWER_TOGGLE,
        TvCapability.POWER_ON,
        TvCapability.POWER_OFF,
        TvCapability.DPAD,
        TvCapability.KEYBOARD_TEXT
    )

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? = withContext(Dispatchers.IO) {
        if (candidate.protocolHint != TvProtocol.UNKNOWN && candidate.protocolHint != TvProtocol.FIRE_TV) {
            return@withContext null
        }

        // Check ADB port 5555 or mDNS service
        if (candidate.openPorts.contains(5555) || candidate.protocolHint == TvProtocol.FIRE_TV) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(candidate.ipAddress, 5555), 1500)
                    return@withContext Fingerprint(
                        protocol = TvProtocol.FIRE_TV,
                        stableId = "firetv-${candidate.ipAddress}",
                        displayName = candidate.serviceName ?: "Amazon Fire TV",
                        confidence = Fingerprint.Confidence.HIGH,
                        macAddress = candidate.attributes["mac"] ?: ""
                    )
                }
            } catch (e: Exception) {
                if (candidate.protocolHint == TvProtocol.FIRE_TV) {
                    return@withContext Fingerprint(
                        protocol = TvProtocol.FIRE_TV,
                        stableId = "firetv-${candidate.ipAddress}",
                        displayName = "Amazon Fire TV",
                        confidence = Fingerprint.Confidence.LOW,
                        macAddress = ""
                    )
                }
            }
        }
        null
    }

    override fun pair(device: TvDevice): Flow<PairingStep> {
        // Fire TV requires enabling ADB Debugging in developer options
        return flowOf(
            PairingStep.Instruction("firetv_adb_developer_options_instruction"),
            PairingStep.ConfirmOnTv(30_000L),
            PairingStep.Succeeded(null)
        )
    }

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        return FireTvController(device.ipAddress)
    }
}
