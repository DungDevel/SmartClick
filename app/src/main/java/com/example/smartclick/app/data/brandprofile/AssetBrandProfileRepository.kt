package com.example.smartclick.app.data.brandprofile

import android.content.Context
import android.util.Log
import com.example.smartclick.app.domain.model.BrandProfile
import com.example.smartclick.app.domain.model.BrandProfileCatalog
import com.example.smartclick.app.domain.repository.IBrandProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FW-2: Đọc `assets/brand_profiles.json`, parse bằng [BrandProfileParser], cache kết quả.
 *
 * Khi bổ sung cấu hình từ xa (N4), chỉ cần thay lớp này để hợp nhất profile tải về với
 * profile đóng gói — phần còn lại của app không đổi.
 */
@Singleton
class AssetBrandProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : IBrandProfileRepository {

    private val mutex = Mutex()
    @Volatile private var cached: BrandProfileCatalog? = null

    override suspend fun getCatalog(): BrandProfileCatalog {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: loadFromAssets().also { cached = it }
        }
    }

    override suspend fun getBrands(): List<BrandProfile> = getCatalog().brands

    override suspend fun getProfile(brandId: String): BrandProfile? = getCatalog().brandById(brandId)

    private suspend fun loadFromAssets(): BrandProfileCatalog = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            BrandProfileParser.parse(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $ASSET_NAME, using empty catalog", e)
            BrandProfileCatalog(schemaVersion = 0, brands = emptyList(), protocolFlags = emptyMap())
        }
    }

    companion object {
        private const val TAG = "BrandProfileRepo"
        private const val ASSET_NAME = "brand_profiles.json"
    }
}
