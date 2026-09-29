package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteKeyCode

object AndroidTvKeyMapper {
    fun mapCommand(command: RemoteCommand): RemoteKeyCode? {
        return when (command) {
            is RemoteCommand.Power -> RemoteKeyCode.KEYCODE_POWER
            is RemoteCommand.VolumeUp -> RemoteKeyCode.KEYCODE_VOLUME_UP
            is RemoteCommand.VolumeDown -> RemoteKeyCode.KEYCODE_VOLUME_DOWN
            is RemoteCommand.Mute -> RemoteKeyCode.KEYCODE_MUTE
            is RemoteCommand.DPadUp -> RemoteKeyCode.KEYCODE_DPAD_UP
            is RemoteCommand.DPadDown -> RemoteKeyCode.KEYCODE_DPAD_DOWN
            is RemoteCommand.DPadLeft -> RemoteKeyCode.KEYCODE_DPAD_LEFT
            is RemoteCommand.DPadRight -> RemoteKeyCode.KEYCODE_DPAD_RIGHT
            is RemoteCommand.Ok -> RemoteKeyCode.KEYCODE_DPAD_CENTER
            is RemoteCommand.Back -> RemoteKeyCode.KEYCODE_BACK
            is RemoteCommand.Home -> RemoteKeyCode.KEYCODE_HOME
            is RemoteCommand.Menu -> RemoteKeyCode.KEYCODE_MENU
            is RemoteCommand.Input -> RemoteKeyCode.KEYCODE_TV_INPUT
            is RemoteCommand.PlayPause -> RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE
            is RemoteCommand.Next -> RemoteKeyCode.KEYCODE_MEDIA_NEXT
            is RemoteCommand.Previous -> RemoteKeyCode.KEYCODE_MEDIA_PREVIOUS
            is RemoteCommand.FastForward -> RemoteKeyCode.KEYCODE_MEDIA_FAST_FORWARD
            is RemoteCommand.Rewind -> RemoteKeyCode.KEYCODE_MEDIA_REWIND
            is RemoteCommand.Backspace -> RemoteKeyCode.KEYCODE_DEL
            is RemoteCommand.Numpad -> when (command.digit) {
                0 -> RemoteKeyCode.KEYCODE_0
                1 -> RemoteKeyCode.KEYCODE_1
                2 -> RemoteKeyCode.KEYCODE_2
                3 -> RemoteKeyCode.KEYCODE_3
                4 -> RemoteKeyCode.KEYCODE_4
                5 -> RemoteKeyCode.KEYCODE_5
                6 -> RemoteKeyCode.KEYCODE_6
                7 -> RemoteKeyCode.KEYCODE_7
                8 -> RemoteKeyCode.KEYCODE_8
                9 -> RemoteKeyCode.KEYCODE_9
                else -> null
            }
            // Text, PointerMove, PointerClick need special handling
            is RemoteCommand.Text,
            is RemoteCommand.PointerMove,
            is RemoteCommand.PointerClick -> null
        }
    }
}
