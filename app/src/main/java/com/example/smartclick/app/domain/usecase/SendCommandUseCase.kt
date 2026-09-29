package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.tvconnection.ReconnectWatcher
import javax.inject.Inject

class SendCommandUseCase @Inject constructor(
    private val activeTvSession: IActiveTvSession,
    private val reconnectWatcher: ReconnectWatcher
) {
    suspend operator fun invoke(command: RemoteCommand) {
        val controller = activeTvSession.currentController.value ?: return

        if (controller.connectionState.value is TvConnectionState.Connected) {
            val result = controller.sendCommand(command)
            reconnectWatcher.onCommandResult(result.isSuccess)
        }
    }
}


