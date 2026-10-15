package com.example.smartclick.app.tvconnection.vizio

import com.example.smartclick.app.domain.model.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VizioKeyMapperTest {

    @Test
    fun `mapPowerCommandToPayload`() {
        val mapping = VizioKeyMapper.mapCommandToPayload(RemoteCommand.Power)
        assertNotNull(mapping)
        assertEquals(3, mapping!!.first)
        assertEquals(2, mapping.second)
    }

    @Test
    fun `mapVolumeUpCommandToPayload`() {
        val mapping = VizioKeyMapper.mapCommandToPayload(RemoteCommand.VolumeUp)
        assertNotNull(mapping)
        assertEquals(3, mapping!!.first)
        assertEquals(5, mapping.second)
    }
}
