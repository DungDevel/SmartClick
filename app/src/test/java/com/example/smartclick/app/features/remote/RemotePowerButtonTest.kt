package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.connection.ConnectProgress
import com.example.smartclick.app.domain.connection.TvConnectionState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePowerButtonTest {

    @Test
    fun `stateFor returns CONNECTED when state is Connected`() {
        val connected = TvConnectionState.Connected(mockk(relaxed = true))
        assertEquals(RemotePowerButton.PowerVisualState.CONNECTED, RemotePowerButton.stateFor(connected, false))
        assertEquals(RemotePowerButton.PowerVisualState.CONNECTED, RemotePowerButton.stateFor(connected, true))
    }

    @Test
    fun `stateFor returns CONNECTING when state is Connecting or Reconnecting`() {
        val connecting = TvConnectionState.Connecting(ConnectProgress.CONNECTING_SOCKET)
        val reconnecting = TvConnectionState.Reconnecting(1)
        assertEquals(RemotePowerButton.PowerVisualState.CONNECTING, RemotePowerButton.stateFor(connecting, false))
        assertEquals(RemotePowerButton.PowerVisualState.CONNECTING, RemotePowerButton.stateFor(reconnecting, false))
        assertEquals(RemotePowerButton.PowerVisualState.CONNECTING, RemotePowerButton.stateFor(reconnecting, true))
    }

    @Test
    fun `stateFor returns WAKEABLE when state is Idle and hasMac is true`() {
        val idle = TvConnectionState.Idle
        assertEquals(RemotePowerButton.PowerVisualState.WAKEABLE, RemotePowerButton.stateFor(idle, true))
    }

    @Test
    fun `stateFor returns FAILED when state is Failed`() {
        val failed = TvConnectionState.Failed("Error")
        assertEquals(RemotePowerButton.PowerVisualState.FAILED, RemotePowerButton.stateFor(failed, true))
        assertEquals(RemotePowerButton.PowerVisualState.FAILED, RemotePowerButton.stateFor(failed, false))
    }

    @Test
    fun `stateFor returns UNAVAILABLE when state is Idle and hasMac is false`() {
        val idle = TvConnectionState.Idle
        assertEquals(RemotePowerButton.PowerVisualState.UNAVAILABLE, RemotePowerButton.stateFor(idle, false))
    }

    @Test
    fun `alphaFor returns 1f for CONNECTED when unlocked`() {
        assertEquals(1f, RemotePowerButton.alphaFor(RemotePowerButton.PowerVisualState.CONNECTED, false, 0.5f))
    }

    @Test
    fun `alphaFor returns 0_3f for CONNECTED when locked`() {
        assertEquals(0.3f, RemotePowerButton.alphaFor(RemotePowerButton.PowerVisualState.CONNECTED, true, 0.5f))
    }

    @Test
    fun `alphaFor returns pulseAlpha for CONNECTING`() {
        assertEquals(0.5f, RemotePowerButton.alphaFor(RemotePowerButton.PowerVisualState.CONNECTING, false, 0.5f))
        assertEquals(0.8f, RemotePowerButton.alphaFor(RemotePowerButton.PowerVisualState.CONNECTING, true, 0.8f))
    }

    @Test
    fun `alphaFor returns 1f for WAKEABLE`() {
        assertEquals(1f, RemotePowerButton.alphaFor(RemotePowerButton.PowerVisualState.WAKEABLE, false, 0.5f))
        assertEquals(1f, RemotePowerButton.alphaFor(RemotePowerButton.PowerVisualState.WAKEABLE, true, 0.5f))
    }

    @Test
    fun `alphaFor returns 0_4f for UNAVAILABLE`() {
        assertEquals(0.4f, RemotePowerButton.alphaFor(RemotePowerButton.PowerVisualState.UNAVAILABLE, false, 0.5f))
        assertEquals(0.4f, RemotePowerButton.alphaFor(RemotePowerButton.PowerVisualState.UNAVAILABLE, true, 0.5f))
    }
}
