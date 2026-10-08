package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvApp
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import kotlinx.coroutines.flow.StateFlow

interface ITvController {
    val connectionState: StateFlow<TvConnectionState>
    val device: TvDevice?

    val capabilities: Set<TvCapability>
        get() = emptySet()

    suspend fun connect(device: TvDevice): Result<Unit>
    suspend fun disconnect()
    suspend fun sendCommand(command: RemoteCommand): Result<Unit>

    suspend fun getApps(): Result<List<TvApp>> = Result.failure(UnsupportedOperationException("Get apps not supported via this protocol yet"))
    suspend fun launchApp(appId: String): Result<Unit> = Result.failure(UnsupportedOperationException("Launch app not supported via this protocol yet"))
}
