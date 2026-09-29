package com.example.smartclick.app.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartclick.app.domain.model.PricingPlan
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state cho màn hình Premium/Paywall. */
data class PremiumUiState(
    val plans: List<PricingPlan> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val subscriptionRepository: ISubscriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        loadPlans()
    }

    private fun loadPlans() {
        viewModelScope.launch {
            _uiState.value = PremiumUiState(isLoading = true)
            try {
                val plans = subscriptionRepository.availablePlans()
                _uiState.value = PremiumUiState(plans = plans, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = PremiumUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun onStartFreeTrial() {
        // TODO(P5-2): Kết nối Google Play BillingClient để mua gói thật.
    }

    fun onRestorePurchase() {
        // TODO(P5-2): Gọi queryPurchasesAsync để khôi phục giao dịch.
    }

    fun retry() {
        loadPlans()
    }
}
