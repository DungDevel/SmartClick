package com.example.smartclick.app.domain.model

/** Trạng thái đăng ký hiện tại: Free hoặc Premium. Dùng cho logic nghiệp vụ (PairingLimitPolicy, FeatureGate). */
enum class SubscriptionPlan {
    FREE,
    PREMIUM
}

/** Gói đăng ký để hiển thị trên màn hình Premium/Paywall. Giá thực sẽ được Play Billing trả về ở P5. */
data class PricingPlan(
    val id: String,
    val displayName: String,
    val price: String,
    val period: String,
    val trialDays: Int = 0,
    val isBestValue: Boolean = false
)
