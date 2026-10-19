package com.example.smartclick.app.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.example.smartclick.app.domain.model.PricingPlan
import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import com.example.smartclick.R

@Singleton
class PlayBillingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ISubscriptionRepository, PurchasesUpdatedListener {

    companion object {
        private const val TAG = "PlayBillingRepo"
    }

    private val applicationScope = CoroutineScope(Dispatchers.IO)
    private val _currentPlan = MutableStateFlow(SubscriptionPlan.FREE)
    private val _billingMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private var productDetailsMap = emptyMap<String, ProductDetails>()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        connectToBillingService()
    }

    override val currentPlan: Flow<SubscriptionPlan> = _currentPlan

    override val isPremium: Flow<Boolean> = _currentPlan.map { it == SubscriptionPlan.PREMIUM }

    override val billingMessages: Flow<String> = _billingMessages

    override suspend fun availablePlans(): List<PricingPlan> {
        val annualDetails = productDetailsMap["annual"]
        val annualPhases = annualDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList
        val annualPrice = annualPhases?.firstOrNull()?.formattedPrice ?: "$19.99"

        val weeklyDetails = productDetailsMap["weekly"]
        val weeklyPhases = weeklyDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList
        val weeklyPrice = weeklyPhases?.firstOrNull()?.formattedPrice ?: "$2.99"

        val lifetimeDetails = productDetailsMap["lifetime"]
        val lifetimePrice = lifetimeDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$49.99"

        return listOf(
            PricingPlan("weekly", "Weekly", weeklyPrice, "per week"),
            PricingPlan("annual", "Annual", annualPrice, "per year", isBestValue = true),
            PricingPlan("lifetime", "Lifetime", lifetimePrice, "one-time")
        )
    }

    override suspend fun subscribe(plan: SubscriptionPlan) {
        // Obsolete? Use launchBillingFlow instead
    }

    override fun launchBillingFlow(activity: Any, planId: String) {
        val act = activity as? Activity ?: return
        if (!billingClient.isReady) {
            _billingMessages.tryEmit(context.getString(R.string.billing_not_ready))
            connectToBillingService()
            return
        }

        val productDetails = productDetailsMap[planId]
        if (productDetails == null) {
            _billingMessages.tryEmit(context.getString(R.string.billing_plan_not_found))
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                _billingMessages.tryEmit(context.getString(R.string.billing_config_error_no_token))
                return
            }
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val productDetailsParamsList = listOf(productDetailsParamsBuilder.build())
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(act, billingFlowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            handleBillingError(billingResult.responseCode, "launchBillingFlow")
        }
    }

    override fun restorePurchases() {
        if (!billingClient.isReady) {
            _billingMessages.tryEmit(context.getString(R.string.billing_not_connected_retrying))
            connectToBillingService()
            return
        }
        queryPurchases() // Re-fetch active purchases
        _billingMessages.tryEmit(context.getString(R.string.billing_restoring))
    }

    private fun connectToBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryPurchases()
                } else {
                    handleBillingError(billingResult.responseCode, "Connection")
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
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId("lifetime")
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                    val newMap = mutableMapOf<String, ProductDetails>()
                    (productDetailsList as List<*>).forEach { details ->
                        if (details is ProductDetails) {
                            newMap[details.productId] = details
                        }
                    }
                    productDetailsMap = newMap
                } else {
                    Log.e(TAG, "Lỗi queryProducts: ${billingResult.debugMessage}")
                }
            }
        }
    }

    private fun queryPurchases() {
        applicationScope.launch {
            // M3 Note: Play Billing Library requires you to query both INAPP and SUBS if you offer both.
            val subsParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            val inappParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchasesAsync(subsParams) { billingResult, subsPurchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingClient.queryPurchasesAsync(inappParams) { inappResult, inappPurchases ->
                        if (inappResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            val allPurchases = (subsPurchases + inappPurchases).distinct()
                            processPurchases(allPurchases)
                        } else {
                            Log.e(TAG, "Lỗi queryPurchases INAPP: ${inappResult.debugMessage}")
                            handleBillingError(inappResult.responseCode, "queryPurchases INAPP")
                        }
                    }
                } else {
                    Log.e(TAG, "Lỗi queryPurchases SUBS: ${billingResult.debugMessage}")
                    handleBillingError(billingResult.responseCode, "queryPurchases SUBS")
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        } else {
            handleBillingError(billingResult.responseCode, "onPurchasesUpdated")
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        var hasPremium = false
        var hasPending = false

        for (purchase in purchases) {
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    hasPremium = true
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    hasPending = true
                }
                Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                    // Do nothing
                }
            }
        }

        if (hasPending) {
            _billingMessages.tryEmit(context.getString(R.string.billing_processing_pending))
        }

        if (hasPremium) {
            _currentPlan.value = SubscriptionPlan.PREMIUM
        } else {
            _currentPlan.value = SubscriptionPlan.FREE
        }
    }

    private fun acknowledgePurchase(purchase: Purchase, retryCount: Int = 3) {
        applicationScope.launch {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            var currentAttempt = 0
            var success = false
            while (currentAttempt < retryCount && !success) {
                try {
                    val result = suspendCoroutine<BillingResult> { continuation ->
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                            continuation.resume(billingResult)
                        }
                    }
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Đã ghi nhận giao dịch thành công (Acknowledge) ${purchase.orderId}")
                        success = true
                    } else {
                        Log.e(TAG, "Lỗi khi ghi nhận giao dịch: ${result.debugMessage}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during acknowledgePurchase", e)
                }

                if (!success) {
                    currentAttempt++
                    if (currentAttempt < retryCount) {
                        kotlinx.coroutines.delay(2000L * currentAttempt)
                    }
                }
            }
        }
    }

    private fun handleBillingError(responseCode: Int, contextCaller: String) {
        val errorMessage = when (responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> context.getString(R.string.billing_error_canceled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> context.getString(R.string.billing_error_already_owned)
            BillingClient.BillingResponseCode.NETWORK_ERROR -> context.getString(R.string.billing_error_network)
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> context.getString(R.string.billing_error_service_unavailable)
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> context.getString(R.string.billing_error_billing_unavailable)
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> context.getString(R.string.billing_error_item_unavailable)
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> context.getString(R.string.billing_error_developer)
            BillingClient.BillingResponseCode.ERROR -> context.getString(R.string.billing_error_generic)
            else -> context.getString(R.string.billing_error_unknown, responseCode)
        }

        Log.e(TAG, "Billing Error in $contextCaller: $errorMessage code: $responseCode")
        _billingMessages.tryEmit(errorMessage)

        // Nếu trùng, kích hoạt tự động cập nhật lại các gói xem liệu đã được mua chưa
        if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryPurchases()
        }
    }
}
