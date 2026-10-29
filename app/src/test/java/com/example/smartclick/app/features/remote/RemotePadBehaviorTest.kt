package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePadBehaviorTest {
    @Test
    fun `pointer capability selects mouse while missing pointer selects circular dpad`() {
        assertEquals(RemotePadLayout.POINTER, RemotePadBehavior.layoutFor(setOf(TvCapability.POINTER)))
        assertEquals(RemotePadLayout.CIRCULAR_DPAD, RemotePadBehavior.layoutFor(setOf(TvCapability.DPAD)))
    }

    @Test
    fun `each direction and center maps to its remote command`() {
        assertEquals(RemoteCommand.DPadUp, RemotePadBehavior.commandFor(RemotePadAction.UP))
        assertEquals(RemoteCommand.DPadDown, RemotePadBehavior.commandFor(RemotePadAction.DOWN))
        assertEquals(RemoteCommand.DPadLeft, RemotePadBehavior.commandFor(RemotePadAction.LEFT))
        assertEquals(RemoteCommand.DPadRight, RemotePadBehavior.commandFor(RemotePadAction.RIGHT))
        assertEquals(RemoteCommand.Ok, RemotePadBehavior.commandFor(RemotePadAction.OK))
    }

    @Test
    fun `navigation is disabled when disconnected locked or lacking dpad capability`() {
        assertFalse(RemotePadBehavior.navigationEnabled(false, false, setOf(TvCapability.DPAD)))
        assertFalse(RemotePadBehavior.navigationEnabled(true, true, setOf(TvCapability.DPAD)))
        assertFalse(RemotePadBehavior.navigationEnabled(true, false, emptySet()))
        assertTrue(RemotePadBehavior.navigationEnabled(true, false, setOf(TvCapability.DPAD)))
    }

    @Test
    fun `disabled pad does not dispatch and enabled pad dispatches the selected action`() {
        val received = mutableListOf<RemoteCommand>()
        RemotePadBehavior.dispatchIfEnabled(RemotePadAction.RIGHT, enabled = false) {
            received.add(it)
            Unit
        }
        assertTrue(received.isEmpty())

        RemotePadBehavior.dispatchIfEnabled(RemotePadAction.OK, enabled = true) {
            received.add(it)
            Unit
        }
        assertEquals(listOf(RemoteCommand.Ok), received)
    }
}
