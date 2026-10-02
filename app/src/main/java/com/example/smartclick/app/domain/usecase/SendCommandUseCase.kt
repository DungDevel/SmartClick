package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.connection.ReconnectWatcher
import com.example.smartclick.app.domain.model.TvCapability
import javax.inject.Inject

enum class SendCommandResult {
    SUCCESS,
    FAILED,
    NOT_SUPPORTED,
    NOT_CONNECTED
}

class SendCommandUseCase @Inject constructor(
    private val activeTvSession: IActiveTvSession,
    private val reconnectWatcher: ReconnectWatcher
) {
    suspend operator fun invoke(command: RemoteCommand): SendCommandResult {
        val controller = activeTvSession.currentController.value ?: return SendCommandResult.NOT_CONNECTED

        if (!isCommandSupported(command, controller.capabilities)) {
            return SendCommandResult.NOT_SUPPORTED
        }

        return if (controller.connectionState.value is TvConnectionState.Connected) {
            val result = controller.sendCommand(command)
            reconnectWatcher.onCommandResult(result.isSuccess)
            if (result.isSuccess) SendCommandResult.SUCCESS else SendCommandResult.FAILED
        } else {
            SendCommandResult.NOT_CONNECTED
        }
    }

    private fun isCommandSupported(command: RemoteCommand, caps: Set<TvCapability>): Boolean {
        return when (command) {
            is RemoteCommand.VolumeUp,
            is RemoteCommand.VolumeDown,
            is RemoteCommand.Mute -> caps.contains(TvCapability.VOLUME)
            is RemoteCommand.ChannelUp,
            is RemoteCommand.ChannelDown -> caps.contains(TvCapability.CHANNEL_CONTROL)
            is RemoteCommand.PointerMove,
            is RemoteCommand.PointerClick -> caps.contains(TvCapability.POINTER)
            is RemoteCommand.Text -> caps.contains(TvCapability.KEYBOARD_TEXT)
            is RemoteCommand.Numpad -> caps.contains(TvCapability.DIGITS)
            is RemoteCommand.SearchContent -> caps.contains(TvCapability.SEARCH_CONTENT)
            is RemoteCommand.OpenSearchUi -> caps.contains(TvCapability.OPEN_SEARCH_UI)
            else -> true
        }
    }
}
