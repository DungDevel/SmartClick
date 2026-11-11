package com.example.smartclick.app.tvconnection.lg

import com.example.smartclick.app.domain.model.RemoteCommand

object LgKeyMapper {
    fun mapCommand(command: RemoteCommand): String? {
        return when (command) {
            RemoteCommand.VolumeUp -> "ssap://audio/volumeUp"
            RemoteCommand.VolumeDown -> "ssap://audio/volumeDown"
            RemoteCommand.PlayPause -> "ssap://media.controls/play"
            RemoteCommand.Rewind -> "ssap://media.controls/rewind"
            RemoteCommand.FastForward -> "ssap://media.controls/fastForward"
            RemoteCommand.Power,
            RemoteCommand.PowerOn,
            RemoteCommand.PowerOff -> "ssap://system/turnOff"
            RemoteCommand.Home -> "ssap://com.webos.applicationManager/launch?id=com.webos.app.home"
            RemoteCommand.ChannelUp -> "ssap://tv/channelUp"
            RemoteCommand.ChannelDown -> "ssap://tv/channelDown"
            else -> null // Other keys require Pointer Input Socket
        }
    }
}
