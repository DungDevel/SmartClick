package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.domain.model.TvDevice

sealed class TvConnectionState {
    object Idle : TvConnectionState()
    data class Connecting(val progress: ConnectProgress) : TvConnectionState()
    data class Connected(val device: TvDevice) : TvConnectionState()
    data class Failed(val reason: String) : TvConnectionState()
    data class Reconnecting(val attempt: Int) : TvConnectionState()
}

