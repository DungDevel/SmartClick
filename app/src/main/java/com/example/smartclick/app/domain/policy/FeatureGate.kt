package com.example.smartclick.app.domain.policy

import com.example.smartclick.app.domain.model.SubscriptionPlan
import javax.inject.Inject

enum class PremiumFeature {
    CHANNELS,         // BR-07 (configurable via flag below)
    MULTI_DEVICE,     // BR-01 (Strict Premium)
    REMOVE_ADS,       // (Strict Premium)
    VOICE,            // Q3
    CAST              // Q3
}

class FeatureGate @Inject constructor() {

    // Configurable flags
    private val isChannelsFeatureFree = true // Q2: Channels feature should be free

    fun isFeatureAllowed(feature: PremiumFeature, plan: SubscriptionPlan): Boolean {
        if (plan == SubscriptionPlan.PREMIUM) return true

        return when (feature) {
            PremiumFeature.CHANNELS -> isChannelsFeatureFree
            PremiumFeature.MULTI_DEVICE -> false
            PremiumFeature.REMOVE_ADS -> false
            PremiumFeature.VOICE -> false
            PremiumFeature.CAST -> false
        }
    }
}
