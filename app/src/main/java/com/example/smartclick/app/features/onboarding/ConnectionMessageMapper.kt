package com.example.smartclick.app.features.onboarding

import com.example.smartclick.R
import com.example.smartclick.app.domain.connection.PairingFailure

internal object ConnectionMessageMapper {

    fun connectionFailure(reason: String): Int {
        val normalized = reason.lowercase()
        return when {
            "certificate-pinned" in normalized || "pinned certificate" in normalized || "pinned client" in normalized || "certificate pin" in normalized ->
                R.string.connection_error_certificate
            "no driver found" in normalized -> R.string.connection_error_no_driver
            "unsupported" in normalized -> R.string.connection_error_unsupported
            "pairing" in normalized || "authentication" in normalized || "unauthorized" in normalized || "auth" in normalized ->
                R.string.connection_error_pairing
            "network" in normalized || "timeout" in normalized || "timed out" in normalized || "socket" in normalized || "connection" in normalized ->
                R.string.connection_error_network
            else -> R.string.connection_error_generic
        }
    }

    fun pairingFailure(reason: PairingFailure): Int = when (reason) {
        PairingFailure.RejectedOnTv -> R.string.pairing_rejected
        PairingFailure.TimedOut -> R.string.pairing_timeout
        is PairingFailure.WrongPin -> R.string.pairing_wrong_pin
        is PairingFailure.NetworkControlDisabled -> R.string.pairing_network_control_disabled
        is PairingFailure.Network -> connectionFailure(reason.message).let { mapped ->
            if (mapped == R.string.connection_error_generic) R.string.connection_error_network else mapped
        }
        is PairingFailure.Unknown -> connectionFailure(reason.message)
        PairingFailure.Cancelled -> R.string.pairing_cancelled
    }
}
