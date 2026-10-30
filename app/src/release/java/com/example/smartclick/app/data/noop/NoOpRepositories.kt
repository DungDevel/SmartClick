package com.example.smartclick.app.data.noop

import com.example.smartclick.app.domain.model.*
import com.example.smartclick.app.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NoOpAuthRepository : IAuthRepository {
    override val currentUser: Flow<UserAccount?> = flowOf(null)
    override suspend fun login(userId: String) {}
    override suspend fun logout() {}
}

class NoOpSubscriptionRepository : ISubscriptionRepository {
    override val isPremium: Flow<Boolean> = flowOf(false)
    override val currentPlan: Flow<SubscriptionPlan> = flowOf(SubscriptionPlan.FREE)
    override val billingMessages: Flow<String> = kotlinx.coroutines.flow.emptyFlow()
    override suspend fun availablePlans(): List<PricingPlan> = emptyList()
    override suspend fun subscribe(plan: SubscriptionPlan) {}
    override fun launchBillingFlow(activity: Any, planId: String) {}
    override fun restorePurchases() {}
}

class NoOpMediaRepository : IMediaRepository {
    override fun getLocalMedia(): Flow<List<MediaItem>> = flowOf(emptyList())
}
