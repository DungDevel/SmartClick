package com.example.smartclick.app.data.brandprofile

import com.example.smartclick.app.domain.model.BrandProfile
import com.example.smartclick.app.domain.model.BrandProfileCatalog
import com.example.smartclick.app.domain.model.ProbePlan
import com.example.smartclick.app.domain.model.ProbeTier
import com.example.smartclick.app.domain.model.ProtocolFlag
import com.example.smartclick.app.domain.model.TvProtocol
import org.json.JSONObject

/**
 * FW-2: Parser thuần cho `brand_profiles.json` (mục 4.5).
 *
 * Không phụ thuộc Android/Context nên unit test được. Bỏ qua các giao thức không khớp
 * `TvProtocol` (forward-compat: cấu hình từ xa thêm giao thức mới không làm sập app cũ) —
 * đây cũng là chỗ "giao thức chưa có driver bị bỏ qua" bắt đầu.
 */
object BrandProfileParser {

    private const val DEFAULT_TIER_TIMEOUT_MS = 5_000L

    fun parse(json: String): BrandProfileCatalog {
        val root = JSONObject(json)
        val schemaVersion = root.optInt("schemaVersion", 1)

        val brands = mutableListOf<BrandProfile>()
        val brandsArray = root.optJSONArray("brands")
        if (brandsArray != null) {
            for (i in 0 until brandsArray.length()) {
                val obj = brandsArray.optJSONObject(i) ?: continue
                parseBrand(obj)?.let { brands.add(it) }
            }
        }

        val protocolFlags = mutableMapOf<TvProtocol, ProtocolFlag>()
        val flagsObj = root.optJSONObject("protocolFlags")
        if (flagsObj != null) {
            for (key in flagsObj.keys()) {
                val protocol = parseProtocol(key) ?: continue
                val flagObj = flagsObj.optJSONObject(key) ?: continue
                protocolFlags[protocol] = ProtocolFlag(
                    enabled = flagObj.optBoolean("enabled", true),
                    minAppVersion = flagObj.optInt("minAppVersion", 1)
                )
            }
        }

        return BrandProfileCatalog(schemaVersion, brands, protocolFlags)
    }

    private fun parseBrand(obj: JSONObject): BrandProfile? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val displayName = obj.optString("displayName").takeIf { it.isNotBlank() } ?: id

        val planNode = obj.opt("probePlan")
        val probePlan: ProbePlan = when {
            planNode is String && planNode.equals("ALL", ignoreCase = true) -> ProbePlan.All
            planNode is org.json.JSONArray -> {
                val tiers = mutableListOf<ProbeTier>()
                for (i in 0 until planNode.length()) {
                    val tierObj = planNode.optJSONObject(i) ?: continue
                    parseTier(tierObj)?.let { tiers.add(it) }
                }
                ProbePlan.Tiered(tiers.sortedBy { it.tier })
            }
            else -> ProbePlan.Tiered(emptyList())
        }

        return BrandProfile(id = id, displayName = displayName, probePlan = probePlan)
    }

    private fun parseTier(obj: JSONObject): ProbeTier? {
        val tier = obj.optInt("tier", 1)
        val timeoutMs = obj.optLong("timeoutMs", DEFAULT_TIER_TIMEOUT_MS)
        val protocolsArray = obj.optJSONArray("protocols") ?: return null

        val protocols = mutableListOf<TvProtocol>()
        for (i in 0 until protocolsArray.length()) {
            parseProtocol(protocolsArray.optString(i))?.let { protocols.add(it) }
        }
        // Tier chỉ toàn giao thức không nhận ra thì bỏ hẳn.
        if (protocols.isEmpty()) return null
        return ProbeTier(tier = tier, protocols = protocols, timeoutMs = timeoutMs)
    }

    /** Chấp nhận "DIAL" như bí danh của DIAL_ONLY; token lạ trả null (bị bỏ qua). */
    private fun parseProtocol(raw: String?): TvProtocol? {
        val token = raw?.trim()?.uppercase() ?: return null
        if (token.isEmpty()) return null
        val normalized = if (token == "DIAL") TvProtocol.DIAL_ONLY.name else token
        return runCatching { TvProtocol.valueOf(normalized) }.getOrNull()
    }
}
