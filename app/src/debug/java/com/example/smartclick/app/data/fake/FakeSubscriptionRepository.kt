package com.example.smartclick.app.data.fake

import com.example.smartclick.app.domain.model.PricingPlan
import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSubscriptionRepository @Inject constructor() : ISubscriptionRepository {
    private val _currentPlan = MutableStateFlow(SubscriptionPlan.FREE)
    override val currentPlan: Flow<SubscriptionPlan> = _currentPlan

    override val isPremium: Flow<Boolean> = _currentPlan.map { it == SubscriptionPlan.PREMIUM }

    private val _billingMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val billingMessages: Flow<String> = _billingMessages

    override suspend fun availablePlans(): List<PricingPlan> = listOf(
        PricingPlan(
            id = "weekly",
            displayName = "Weekly",
            price = "$2.99",
            period = "/week",
            trialDays = 3
        ),
        PricingPlan(
            id = "annual",
            displayName = "Annual",
            price = "$19.99",
            period = "/year",
            trialDays = 7,
            isBestValue = true
        ),
        PricingPlan(
            id = "lifetime",
            displayName = "Lifetime",
            price = "$39.99",
            period = "once"
        )
    )

    override suspend fun subscribe(plan: SubscriptionPlan) {
        _currentPlan.value = plan
    }

    override fun launchBillingFlow(activity: Any, planId: String) {
        // Fake billing success
        _billingMessages.tryEmit("Lập hóa đơn thành công (Fake)")
        _currentPlan.value = SubscriptionPlan.PREMIUM
    }

    override fun restorePurchases() {
        // Fake restore
        _billingMessages.tryEmit("Đã khôi phục giao dịch (Fake)")
        _currentPlan.value = SubscriptionPlan.PREMIUM
    }
}
