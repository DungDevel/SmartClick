package com.example.smartclick.app.data.repository

import android.content.Context
import com.android.billingclient.api.*
import com.example.smartclick.app.domain.model.PricingPlan
import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
class PlayBillingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ISubscriptionRepository, PurchasesUpdatedListener {

    private val applicationScope = CoroutineScope(Dispatchers.IO)
    private val _currentPlan = MutableStateFlow(SubscriptionPlan.FREE)

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        connectToBillingService()
    }

    override val currentPlan: Flow<SubscriptionPlan> = _currentPlan

    override val isPremium: Flow<Boolean> = _currentPlan.map { it == SubscriptionPlan.PREMIUM }

    override suspend fun availablePlans(): List<PricingPlan> {
        // Will fetch from Play Console in real environment
        return listOf(
            PricingPlan("premium_monthly", "Monthly", "$4.99", "per month"),
            PricingPlan("premium_lifetime", "Lifetime", "$29.99", "one time", isBestValue = true)
        )
    }

    override suspend fun subscribe(plan: SubscriptionPlan) {
        // Trigger purchase flow matching the plan
        // This usually involves calling billingClient.launchBillingFlow(activity, params)
        // For now, in a non-UI repo, we'll just mock success if this is test implementation
    }

    private fun connectToBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to reconnect
                applicationScope.launch {
                    kotlinx.coroutines.delay(2000)
                    connectToBillingService()
                }
            }
        })
    }

    private fun queryPurchases() {
        applicationScope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    processPurchases(purchases)
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val hasPremium = purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        _currentPlan.value = if (hasPremium) SubscriptionPlan.PREMIUM else SubscriptionPlan.FREE
    }
}
