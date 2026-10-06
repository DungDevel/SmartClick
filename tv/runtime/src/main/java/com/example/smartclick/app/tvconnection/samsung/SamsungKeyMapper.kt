package com.example.smartclick.app.tvconnection.samsung

import com.example.smartclick.app.domain.model.RemoteCommand

object SamsungKeyMapper {
    fun mapCommand(command: RemoteCommand): String? {
        return when (command) {
            RemoteCommand.Power -> "KEY_POWER"
            RemoteCommand.VolumeUp -> "KEY_VOLUP"
            RemoteCommand.VolumeDown -> "KEY_VOLDOWN"
            RemoteCommand.Mute, RemoteCommand.MuteOn, RemoteCommand.MuteOff -> "KEY_MUTE"
            RemoteCommand.ChannelUp -> "KEY_CHUP"
            RemoteCommand.ChannelDown -> "KEY_CHDOWN"
            RemoteCommand.DPadUp -> "KEY_UP"
            RemoteCommand.DPadDown -> "KEY_DOWN"
            RemoteCommand.DPadLeft -> "KEY_LEFT"
            RemoteCommand.DPadRight -> "KEY_RIGHT"
            RemoteCommand.Ok -> "KEY_ENTER"
            RemoteCommand.Back -> "KEY_RETURN"
            RemoteCommand.Home -> "KEY_HOME"
            is RemoteCommand.Numpad -> "KEY_${command.digit}"
            RemoteCommand.OpenSearchUi -> "KEY_SEARCH"
            else -> null
        }
    }
}
