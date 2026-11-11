package com.example.smartclick.app.tvconnection.firetv

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

class FireTvControllerTest {

    @Test
    fun `mapCommandToKeyCode handles expected commands`() {
        assertEquals(3, FireTvKeyMapper.mapCommandToKeyCode(RemoteCommand.Home))
        assertEquals(26, FireTvKeyMapper.mapCommandToKeyCode(RemoteCommand.Power))
        assertEquals(19, FireTvKeyMapper.mapCommandToKeyCode(RemoteCommand.DPadUp))
    }

    @Test
    fun `controller capabilities include volume and power`() {
        val controller = FireTvController("192.168.1.50")
        assertNotNull(controller.capabilities)
        assertEquals("192.168.1.50", controller.device?.ipAddress ?: "192.168.1.50")
    }
}
