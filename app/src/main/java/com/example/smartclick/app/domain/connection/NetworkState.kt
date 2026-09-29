package com.example.smartclick.app.domain.connection

/**
 * Represents the current WiFi/network connectivity state.
 * Used across ViewModels to gate scan and remote operations.
 */
sealed class NetworkState {
    /** WiFi is connected and ready for LAN communication. */
    data class WifiConnected(val ssid: String? = null) : NetworkState()

    /** Device has internet (cellular/ethernet) but NOT on WiFi — LAN scan won't work. */
    object ConnectedNoWifi : NetworkState()

    /** No network connectivity at all. */
    object Disconnected : NetworkState()
}

