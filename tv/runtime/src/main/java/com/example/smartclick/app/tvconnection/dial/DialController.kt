package com.example.smartclick.app.tvconnection.dial

import com.example.smartclick.app.domain.connection.ConnectProgress
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.tvconnection.utils.NetworkSecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DialController @Inject constructor() : ITvController {

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    override var device: TvDevice? = null
        private set

    override val capabilities: Set<TvCapability> = emptySet()

    override suspend fun connect(device: TvDevice): Result<Unit> = withContext(Dispatchers.IO) {
        val hostPart = device.ipAddress.substringBefore(":")
        if (!NetworkSecurityUtils.isPrivateLanAddress(hostPart)) {
            _connectionState.value = TvConnectionState.Failed("Invalid IP address")
            return@withContext Result.failure(SecurityException("IP address is not in private LAN range"))
        }

        this@DialController.device = device
        _connectionState.value = TvConnectionState.Connecting(ConnectProgress.CONNECTING_SOCKET)

        try {
            _connectionState.value = TvConnectionState.Connected(device)
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = TvConnectionState.Failed(e.message ?: "Connection failed")
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        _connectionState.value = TvConnectionState.Idle
        device = null
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Remote commands not supported via DIAL protocol"))
    }

}
