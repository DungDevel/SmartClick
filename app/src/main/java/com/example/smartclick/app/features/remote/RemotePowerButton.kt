package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.connection.TvConnectionState

object RemotePowerButton {
    enum class PowerVisualState { CONNECTED, CONNECTING, WAKEABLE, UNAVAILABLE, FAILED }

    fun stateFor(connectionState: TvConnectionState, hasMac: Boolean): PowerVisualState {
        return when (connectionState) {
            is TvConnectionState.Connected -> PowerVisualState.CONNECTED
            is TvConnectionState.Connecting, is TvConnectionState.Reconnecting -> PowerVisualState.CONNECTING
            is TvConnectionState.Failed -> PowerVisualState.FAILED
            else -> if (hasMac) PowerVisualState.WAKEABLE else PowerVisualState.UNAVAILABLE
        }
    }

    fun alphaFor(state: PowerVisualState, isLocked: Boolean, pulseAlpha: Float): Float {
        return when (state) {
            PowerVisualState.CONNECTED -> if (isLocked) 0.3f else 1f
            PowerVisualState.CONNECTING -> pulseAlpha
            PowerVisualState.FAILED -> 1f
            PowerVisualState.WAKEABLE -> 1f
            PowerVisualState.UNAVAILABLE -> 0.4f
        }
    }
}
