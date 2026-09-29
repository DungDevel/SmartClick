package com.example.smartclick.app.domain.repository

import com.example.smartclick.app.domain.model.PricingPlan
import com.example.smartclick.app.domain.model.SubscriptionPlan
import kotlinx.coroutines.flow.Flow

interface ISubscriptionRepository {
    val currentPlan: Flow<SubscriptionPlan>

    /** Tiện ích: true khi người dùng đang có gói PREMIUM. */
    val isPremium: Flow<Boolean>

    /** Danh sách gói hiển thị trên Paywall. Play Billing sẽ thay thế ở P5. */
    suspend fun availablePlans(): List<PricingPlan>

    suspend fun subscribe(plan: SubscriptionPlan)
}
