package com.example.smartclick.app.tvconnection.samsung

import com.example.smartclick.app.domain.model.RemoteCommand

object SamsungKeyMapper {
    fun mapCommand(command: RemoteCommand): String? {
        return when (command) {
            RemoteCommand.Power -> "KEY_POWER"
            RemoteCommand.VolumeUp -> "KEY_VOLUP"
            RemoteCommand.VolumeDown -> "KEY_VOLDOWN"
            RemoteCommand.Mute -> "KEY_MUTE"
            RemoteCommand.DPadUp -> "KEY_UP"
            RemoteCommand.DPadDown -> "KEY_DOWN"
            RemoteCommand.DPadLeft -> "KEY_LEFT"
            RemoteCommand.DPadRight -> "KEY_RIGHT"
            RemoteCommand.Ok -> "KEY_ENTER"
            RemoteCommand.Back -> "KEY_RETURN"
            RemoteCommand.Home -> "KEY_HOME"
            RemoteCommand.Menu -> "KEY_MENU"
            RemoteCommand.Input -> "KEY_SOURCE"
            RemoteCommand.PlayPause -> "KEY_PLAY_PLAYBACK" // Usually Play, but Samsung often interprets this conditionally. "KEY_PLAY" or "KEY_PAUSE".
            RemoteCommand.Next -> "KEY_FF"
            RemoteCommand.Previous -> "KEY_REWIND"
            RemoteCommand.FastForward -> "KEY_FF"
            RemoteCommand.Rewind -> "KEY_REWIND"
            is RemoteCommand.Numpad -> "KEY_${command.digit}"
            RemoteCommand.Backspace -> null // Was KEY_RETURN, but that acts as Back. Removed to avoid confusion.
            RemoteCommand.ChannelUp -> "KEY_CHUP"
            RemoteCommand.ChannelDown -> "KEY_CHDOWN"
            is RemoteCommand.OpenSearchUi -> "KEY_SEARCH"
            // Other commands like Text and Pointer are handled differently or not supported natively by simple keys.
            else -> null
        }
    }
}
