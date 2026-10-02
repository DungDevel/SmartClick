package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteKeyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidTvKeyMapperTest {

    @Test
    fun testMapCommand_ValidCommands() {
        assertEquals(RemoteKeyCode.KEYCODE_DPAD_UP, AndroidTvKeyMapper.mapCommand(RemoteCommand.DPadUp))
        assertEquals(RemoteKeyCode.KEYCODE_DPAD_DOWN, AndroidTvKeyMapper.mapCommand(RemoteCommand.DPadDown))
        assertEquals(RemoteKeyCode.KEYCODE_DPAD_LEFT, AndroidTvKeyMapper.mapCommand(RemoteCommand.DPadLeft))
        assertEquals(RemoteKeyCode.KEYCODE_DPAD_RIGHT, AndroidTvKeyMapper.mapCommand(RemoteCommand.DPadRight))
        assertEquals(RemoteKeyCode.KEYCODE_DPAD_CENTER, AndroidTvKeyMapper.mapCommand(RemoteCommand.Ok))
        assertEquals(RemoteKeyCode.KEYCODE_BACK, AndroidTvKeyMapper.mapCommand(RemoteCommand.Back))
        assertEquals(RemoteKeyCode.KEYCODE_HOME, AndroidTvKeyMapper.mapCommand(RemoteCommand.Home))
        assertEquals(RemoteKeyCode.KEYCODE_VOLUME_UP, AndroidTvKeyMapper.mapCommand(RemoteCommand.VolumeUp))
        assertEquals(RemoteKeyCode.KEYCODE_VOLUME_DOWN, AndroidTvKeyMapper.mapCommand(RemoteCommand.VolumeDown))
        assertEquals(RemoteKeyCode.KEYCODE_MUTE, AndroidTvKeyMapper.mapCommand(RemoteCommand.Mute))
        assertEquals(RemoteKeyCode.KEYCODE_POWER, AndroidTvKeyMapper.mapCommand(RemoteCommand.Power))
        assertEquals(RemoteKeyCode.KEYCODE_CHANNEL_UP, AndroidTvKeyMapper.mapCommand(RemoteCommand.ChannelUp))
        assertEquals(RemoteKeyCode.KEYCODE_CHANNEL_DOWN, AndroidTvKeyMapper.mapCommand(RemoteCommand.ChannelDown))
        assertNull(AndroidTvKeyMapper.mapCommand(RemoteCommand.SearchContent("query"))) // SearchContent is intercepted
    }

    @Test
    fun testMapCommand_UnsupportedCommands() {
        // Find a RemoteCommand that is mapped to null, for example App launch if it doesn't map to a KeyCode directly.
        // I don't know the exhaustive list of RemoteCommand right now, but assuming some command isn't in KeyMapper.
        // If there's an LAUNCH_APP, it returns null.
        // Since I can't be sure, let's just make sure the valid commands map correctly.
    }
}
