package com.example.smartclick.app.domain.policy

import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.model.TvDevice

sealed class PairingResult {
    object Allowed : PairingResult()
    object LimitExceeded : PairingResult()
}

class PairingLimitPolicy @javax.inject.Inject constructor() {
    fun evaluate(
        currentPlan: SubscriptionPlan,
        pairedDevices: List<TvDevice>
    ): PairingResult {
        return if (currentPlan == SubscriptionPlan.PREMIUM) {
            PairingResult.Allowed
        } else {
            // BR-01: Limit 1 connected/paired TV for free users
            if (pairedDevices.isNotEmpty()) PairingResult.LimitExceeded else PairingResult.Allowed
        }
    }
}
