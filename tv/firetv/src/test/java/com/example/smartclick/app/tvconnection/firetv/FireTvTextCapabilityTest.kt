package com.example.smartclick.app.tvconnection.firetv

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FireTvTextCapabilityTest {
    @Test
    fun `text is not advertised when the ADB mapper has no text command`() {
        assertFalse(TvCapability.KEYBOARD_TEXT in FireTvDriver().capabilities)
        assertNull(FireTvKeyMapper.mapCommandToKeyCode(RemoteCommand.Text("hello")))
    }
}
