package com.example.smartclick.app.domain.connection

sealed class ConnectOutcome {
    object Connected : ConnectOutcome()
    object PaywallRequired : ConnectOutcome()
    data class Failed(val reason: String) : ConnectOutcome()
}
