package com.example.smartclick.app.tvconnection.sony

import com.example.smartclick.app.domain.model.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyBraviaControllerTest {

    @Test
    fun `SonyKeyMapper maps power and volume correctly`() {
        val powerSoap = SonyKeyMapper.mapCommandToIrcc(RemoteCommand.Power)
        assertNotNull(powerSoap)
        assertTrue(powerSoap!!.contains("X_SendIRCC"))

        val volUpSoap = SonyKeyMapper.mapCommandToIrcc(RemoteCommand.VolumeUp)
        assertNotNull(volUpSoap)
        assertTrue(volUpSoap!!.contains("AAAAAQAAAAEAAAASAw=="))
    }
}
