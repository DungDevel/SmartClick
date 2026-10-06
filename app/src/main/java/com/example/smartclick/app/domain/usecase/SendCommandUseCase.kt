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

        val actualCommand = getEffectiveCommand(command, controller.capabilities) 
            ?: return SendCommandResult.NOT_SUPPORTED

        return if (controller.connectionState.value is TvConnectionState.Connected) {
            val result = controller.sendCommand(actualCommand)
            reconnectWatcher.onCommandResult(result.isSuccess)
            if (result.isSuccess) SendCommandResult.SUCCESS else SendCommandResult.FAILED
        } else {
            SendCommandResult.NOT_CONNECTED
        }
    }

    private fun getEffectiveCommand(command: RemoteCommand, caps: Set<TvCapability>): RemoteCommand? {
        return when (command) {
            is RemoteCommand.Power -> {
                if (caps.contains(TvCapability.POWER_TOGGLE)) command
                else if (caps.contains(TvCapability.POWER_OFF)) RemoteCommand.PowerOff // For LG
                else null
            }
            is RemoteCommand.PowerOn -> {
                if (caps.contains(TvCapability.POWER_ON)) command
                else if (caps.contains(TvCapability.POWER_TOGGLE)) RemoteCommand.Power
                else null
            }
            is RemoteCommand.PowerOff -> {
                if (caps.contains(TvCapability.POWER_OFF)) command
                else if (caps.contains(TvCapability.POWER_TOGGLE)) RemoteCommand.Power
                else null
            }
            is RemoteCommand.VolumeUp, is RemoteCommand.VolumeDown -> {
                if (caps.contains(TvCapability.VOLUME)) command else null
            }
            is RemoteCommand.Mute -> {
                if (caps.contains(TvCapability.VOLUME)) command else null
            }
            is RemoteCommand.MuteOn -> {
                if (caps.contains(TvCapability.MUTE_ON_OFF)) command
                else if (caps.contains(TvCapability.VOLUME)) RemoteCommand.Mute
                else null
            }
            is RemoteCommand.MuteOff -> {
                if (caps.contains(TvCapability.MUTE_ON_OFF)) command
                else if (caps.contains(TvCapability.VOLUME)) RemoteCommand.Mute
                else null
            }
            is RemoteCommand.ChannelUp, is RemoteCommand.ChannelDown -> {
                if (caps.contains(TvCapability.CHANNEL_CONTROL)) command else null
            }
            is RemoteCommand.PointerMove, is RemoteCommand.PointerClick -> {
                if (caps.contains(TvCapability.POINTER)) command else null
            }
            is RemoteCommand.Text -> {
                if (caps.contains(TvCapability.KEYBOARD_TEXT)) command else null
            }
            is RemoteCommand.Numpad -> {
                if (caps.contains(TvCapability.DIGITS)) command else null
            }
            is RemoteCommand.SearchContent -> {
                if (caps.contains(TvCapability.SEARCH_CONTENT)) command else null
            }
            is RemoteCommand.OpenSearchUi -> {
                if (caps.contains(TvCapability.OPEN_SEARCH_UI)) command else null
            }
            is RemoteCommand.Backspace -> {
                if (caps.contains(TvCapability.BACKSPACE)) command else null
            }
            else -> command 
        }
    }
}
