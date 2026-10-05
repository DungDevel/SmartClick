package com.example.smartclick.app.domain.repository

import com.example.smartclick.app.domain.model.BrandProfile
import com.example.smartclick.app.domain.model.BrandProfileCatalog

/**
 * FW-2: Nguồn duy nhất cho danh sách hãng và kế hoạch quét (thay `SAMPLE_BRANDS` cứng).
 */
interface IBrandProfileRepository {
    /** Toàn bộ danh mục (đã cache sau lần đọc đầu). */
    suspend fun getCatalog(): BrandProfileCatalog

    /** Danh sách hãng để hiển thị ở BrandScreen. */
    suspend fun getBrands(): List<BrandProfile>

    /** Profile của một hãng theo id; null nếu không có. */
    suspend fun getProfile(brandId: String): BrandProfile?
}
