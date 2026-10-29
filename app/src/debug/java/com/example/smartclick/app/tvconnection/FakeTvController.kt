package com.example.smartclick.app.tvconnection

import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.connection.ConnectProgress

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvApp
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class FakeTvController @Inject constructor() : ITvController {
    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    private var _device: TvDevice? = null
    override val device: TvDevice?
        get() = _device

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.DIGITS,
        TvCapability.POINTER,
        TvCapability.DPAD,
        TvCapability.KEYBOARD_TEXT,
        TvCapability.INSTALLED_APPS
    )

    // Tests configure this to mock network errors
    var failConnect = false
    var failCommands = false

    override suspend fun connect(device: TvDevice): Result<Unit> {
        if (failConnect) {
            _connectionState.value = TvConnectionState.Failed("Fake failure")
            return Result.failure(Exception("Fake failure"))
        }

        _device = device
        _connectionState.value = TvConnectionState.Connecting(ConnectProgress.CONNECTING_SOCKET)
        delay(500)

        _connectionState.value = TvConnectionState.Connecting(ConnectProgress.WAITING_FOR_USER_ACCEPT_ON_TV)
        delay(2000)

        _connectionState.value = TvConnectionState.Connecting(ConnectProgress.SAVING_PAIRING_KEY)
        delay(500)

        _connectionState.value = TvConnectionState.Connected(device = device)
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        _connectionState.value = TvConnectionState.Idle
        _device = null
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        if (_connectionState.value !is TvConnectionState.Connected) {
            return Result.failure(Exception("FakeTvController: Blocked command $command because state is not Connected."))
        }
        if (failCommands) {
            return Result.failure(Exception("Fake commands failed."))
        }
        println("FakeTvController: Sent $command successfully.")
        return Result.success(Unit)
    }

    override suspend fun getApps(): Result<List<TvApp>> {
        val mockApps = listOf(
            TvApp("12", "Netflix", null),
            TvApp("837", "YouTube", null)
        )
        return Result.success(mockApps)
    }

    override suspend fun launchApp(appId: String): Result<Unit> {
        println("FakeTvController: Launched app $appId successfully.")
        return Result.success(Unit)
    }
}

