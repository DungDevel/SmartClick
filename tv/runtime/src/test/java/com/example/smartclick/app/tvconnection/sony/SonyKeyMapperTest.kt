package com.example.smartclick.app.tvconnection.sony

import com.example.smartclick.app.domain.model.RemoteCommand
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyKeyMapperTest {

    @Test
    fun `mapPowerCommandToSoap`() {
        val soap = SonyKeyMapper.mapCommandToIrcc(RemoteCommand.Power)
        assertNotNull(soap)
        assertTrue(soap!!.contains("X_SendIRCC"))
        assertTrue(soap.contains("AAAAAQAAAAEAAAAVAw=="))
    }

    @Test
    fun `mapVolumeUpCommandToSoap`() {
        val soap = SonyKeyMapper.mapCommandToIrcc(RemoteCommand.VolumeUp)
        assertNotNull(soap)
        assertTrue(soap!!.contains("AAAAAQAAAAEAAAASAw=="))
    }
}
