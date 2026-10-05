package com.example.smartclick.app.domain.model

/**
 * FW-2: Danh mục hãng → thứ tự thử giao thức (mục 4.5).
 *
 * Đọc từ `assets/brand_profiles.json`; sau này có thể ghi đè bằng cấu hình từ xa (N4).
 * Đây là **dữ liệu**, không hardcode trong UI — đổi profile không cần sửa code.
 */
data class BrandProfileCatalog(
    val schemaVersion: Int,
    val brands: List<BrandProfile>,
    /** Cờ bật/tắt từng giao thức từ xa (kill-switch cho driver hỏng). */
    val protocolFlags: Map<TvProtocol, ProtocolFlag>
) {
    fun brandById(id: String): BrandProfile? = brands.firstOrNull { it.id.equals(id, ignoreCase = true) }

    /** Giao thức bị tắt qua cờ (enabled=false) hoặc yêu cầu app mới hơn hiện tại. */
    fun isProtocolEnabled(protocol: TvProtocol, appVersion: Int): Boolean {
        val flag = protocolFlags[protocol] ?: return true
        return flag.enabled && appVersion >= flag.minAppVersion
    }
}

data class BrandProfile(
    val id: String,
    val displayName: String,
    val probePlan: ProbePlan
)

/**
 * Kế hoạch quét cho một hãng: hoặc thử **tất cả** (Other), hoặc theo từng tier ưu tiên.
 */
sealed class ProbePlan {
    /** Other: mDNS + SSDP ssdp:all + dò cổng, rồi nhận dạng. */
    object All : ProbePlan()

    /** Danh sách tier theo ưu tiên; tier nhỏ chạy trước. */
    data class Tiered(val tiers: List<ProbeTier>) : ProbePlan()
}

/**
 * Một nhóm giao thức thử song song. Tier sau chạy khi hết [timeoutMs] của tier trước
 * hoặc ở nền khi tier trước đã có kết quả (mục 4.5).
 */
data class ProbeTier(
    val tier: Int,
    val protocols: List<TvProtocol>,
    val timeoutMs: Long
)

data class ProtocolFlag(
    val enabled: Boolean,
    val minAppVersion: Int = 1
)
