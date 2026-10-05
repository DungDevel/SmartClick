package com.example.smartclick.app.tvconnection.samsung

import com.example.smartclick.app.domain.model.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SamsungKeyMapperTest {

    @Test
    fun testMapCommand_ValidCommands() {
        assertEquals("KEY_VOLDOWN", SamsungKeyMapper.mapCommand(RemoteCommand.VolumeDown))
        assertEquals("KEY_VOLUP", SamsungKeyMapper.mapCommand(RemoteCommand.VolumeUp))
        assertEquals("KEY_MUTE", SamsungKeyMapper.mapCommand(RemoteCommand.Mute))
        assertEquals("KEY_POWER", SamsungKeyMapper.mapCommand(RemoteCommand.Power))
        assertEquals("KEY_HOME", SamsungKeyMapper.mapCommand(RemoteCommand.Home))
        assertEquals("KEY_RETURN", SamsungKeyMapper.mapCommand(RemoteCommand.Back))
        assertEquals("KEY_ENTER", SamsungKeyMapper.mapCommand(RemoteCommand.Ok))
        assertEquals("KEY_UP", SamsungKeyMapper.mapCommand(RemoteCommand.DPadUp))
        assertEquals("KEY_DOWN", SamsungKeyMapper.mapCommand(RemoteCommand.DPadDown))
        assertEquals("KEY_LEFT", SamsungKeyMapper.mapCommand(RemoteCommand.DPadLeft))
        assertEquals("KEY_RIGHT", SamsungKeyMapper.mapCommand(RemoteCommand.DPadRight))
        assertEquals("KEY_CHUP", SamsungKeyMapper.mapCommand(RemoteCommand.ChannelUp))
        assertEquals("KEY_CHDOWN", SamsungKeyMapper.mapCommand(RemoteCommand.ChannelDown))
        assertEquals("KEY_SEARCH", SamsungKeyMapper.mapCommand(RemoteCommand.OpenSearchUi))
    }

    @Test
    fun testMapCommand_UnsupportedCommands() {
        assertNull(SamsungKeyMapper.mapCommand(RemoteCommand.PointerClick))
        assertNull(SamsungKeyMapper.mapCommand(RemoteCommand.Backspace))
    }
}
