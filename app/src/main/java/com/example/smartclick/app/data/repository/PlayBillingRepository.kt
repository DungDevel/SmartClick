package com.example.smartclick.app.data.repository

import android.app.Activity
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

    private var productDetailsMap = emptyMap<String, ProductDetails>()

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
        val annualDetails = productDetailsMap["annual"]
        val annualPrice = annualDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "$19.99"
        val weeklyDetails = productDetailsMap["weekly"]
        val weeklyPrice = weeklyDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "$2.99"

        return listOf(
            PricingPlan("weekly", "Weekly", weeklyPrice, "per week"),
            PricingPlan("annual", "Annual", annualPrice, "per year", isBestValue = true)
        )
    }

    override suspend fun subscribe(plan: SubscriptionPlan) {
        // Obsolete? Use launchBillingFlow instead
    }

    override fun launchBillingFlow(activity: Any, planId: String) {
        val act = activity as? Activity ?: return
        val productDetails = productDetailsMap[planId] ?: return
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(act, billingFlowParams)
    }

    override fun restorePurchases() {
        queryPurchases() // Re-fetch active purchases
    }

    private fun connectToBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                applicationScope.launch {
                    kotlinx.coroutines.delay(2000)
                    connectToBillingService()
                }
            }
        })
    }

    private fun queryProducts() {
        applicationScope.launch {
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId("annual")
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId("weekly")
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    productDetailsMap = productDetailsList.associateBy { it.productId }
                }
            }
        }
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

        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }.forEach { purchase ->
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { }
        }

        _currentPlan.value = if (hasPremium) SubscriptionPlan.PREMIUM else SubscriptionPlan.FREE
    }
}
