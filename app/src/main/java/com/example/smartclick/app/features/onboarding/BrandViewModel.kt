package com.example.smartclick.app.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartclick.app.domain.model.BrandProfile
import com.example.smartclick.app.domain.repository.IBrandProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FW-2: Cấp danh sách hãng cho [BrandScreen] từ `BrandProfile` (thay `SAMPLE_BRANDS` cứng).
 */
@HiltViewModel
class BrandViewModel @Inject constructor(
    private val brandProfileRepository: IBrandProfileRepository
) : ViewModel() {

    private val _brands = MutableStateFlow<List<BrandProfile>>(emptyList())
    val brands: StateFlow<List<BrandProfile>> = _brands.asStateFlow()

    init {
        viewModelScope.launch {
            _brands.value = brandProfileRepository.getBrands()
        }
    }
}
