package com.example.smartclick.app.tvconnection.vizio

import com.example.smartclick.app.domain.model.RemoteCommand

object VizioKeyMapper {
    fun mapCommandToPayload(command: RemoteCommand): Pair<Int, Int>? {
        return when (command) {
            RemoteCommand.Power, RemoteCommand.PowerOff, RemoteCommand.PowerOn -> Pair(3, 2)
            RemoteCommand.VolumeUp -> Pair(3, 5)
            RemoteCommand.VolumeDown -> Pair(3, 6)
            RemoteCommand.Mute, RemoteCommand.MuteOn, RemoteCommand.MuteOff -> Pair(3, 3)
            RemoteCommand.Input -> Pair(3, 8)
            RemoteCommand.Home -> Pair(3, 4)
            RemoteCommand.Back -> Pair(3, 7)
            RemoteCommand.DPadUp -> Pair(3, 8)
            RemoteCommand.DPadDown -> Pair(3, 9)
            RemoteCommand.DPadLeft -> Pair(3, 10)
            RemoteCommand.DPadRight -> Pair(3, 11)
            RemoteCommand.Ok -> Pair(3, 12)
            else -> null
        }
    }
}
