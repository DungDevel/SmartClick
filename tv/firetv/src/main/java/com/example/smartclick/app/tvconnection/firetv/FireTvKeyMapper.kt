package com.example.smartclick.app.tvconnection.firetv

import com.example.smartclick.app.domain.model.RemoteCommand

object FireTvKeyMapper {
    fun mapCommandToKeyCode(command: RemoteCommand): Int? {
        return when (command) {
            RemoteCommand.Power, RemoteCommand.PowerOff, RemoteCommand.PowerOn -> 26
            RemoteCommand.Home -> 3
            RemoteCommand.Back -> 4
            RemoteCommand.DPadUp -> 19
            RemoteCommand.DPadDown -> 20
            RemoteCommand.DPadLeft -> 21
            RemoteCommand.DPadRight -> 22
            RemoteCommand.Ok -> 66
            RemoteCommand.VolumeUp -> 24
            RemoteCommand.VolumeDown -> 25
            RemoteCommand.Mute, RemoteCommand.MuteOn, RemoteCommand.MuteOff -> 164
            RemoteCommand.Menu -> 82
            RemoteCommand.PlayPause -> 85
            RemoteCommand.Rewind -> 89
            RemoteCommand.FastForward -> 90
            else -> null
        }
    }
}
