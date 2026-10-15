package com.example.smartclick.app.tvconnection.firetv

import com.example.smartclick.app.domain.model.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FireTvKeyMapperTest {

    @Test
    fun `mapHomeCommandToKeyCode`() {
        val code = FireTvKeyMapper.mapCommandToKeyCode(RemoteCommand.Home)
        assertEquals(3, code)
    }

    @Test
    fun `mapPowerCommandToKeyCode`() {
        val code = FireTvKeyMapper.mapCommandToKeyCode(RemoteCommand.Power)
        assertEquals(26, code)
    }
}
