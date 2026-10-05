package com.example.smartclick.app.domain.connection.fake

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class FakeDriver : IProtocolDriver {
    override val protocol: TvProtocol = TvProtocol.UNKNOWN
    
    override val capabilities: Set<TvCapability> = TvCapability.values().toSet()

    var shouldFingerprintReturnNull = false
    var customFingerprint: Fingerprint? = null

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? {
        if (shouldFingerprintReturnNull) return null
        
        // Strict irrelevant protocol filter
        if (candidate.protocolHint != TvProtocol.UNKNOWN && candidate.protocolHint != protocol) {
            return null
        }
        
        return customFingerprint ?: Fingerprint(
            protocol = TvProtocol.UNKNOWN,
            stableId = "fake-${candidate.ipAddress}",
            displayName = "Fake Virtual TV",
            confidence = Fingerprint.Confidence.HIGH
        )
    }

    override fun pair(device: TvDevice): Flow<PairingStep> = flow {
        emit(PairingStep.NotRequired)
        emit(PairingStep.Succeeded(Credentials(emptyMap())))
    }

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        return FakeController(device)
    }
}

class FakeController(
    override val device: TvDevice?
) : ITvController {
    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState

    override val capabilities: Set<TvCapability>
        get() = device?.capabilities ?: emptySet()

    override suspend fun connect(device: TvDevice): Result<Unit> {
        val dev = device ?: TvDevice("fake", "fake", "127.0.0.1", "", protocol = TvProtocol.UNKNOWN)
        _connectionState.value = TvConnectionState.Connected(dev)
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        _connectionState.value = TvConnectionState.Idle
    }

    var lastCommand: RemoteCommand? = null
        private set

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        if (_connectionState.value !is TvConnectionState.Connected) {
            return Result.failure(IllegalStateException("Not connected"))
        }
        lastCommand = command
        return Result.success(Unit)
    }
}
