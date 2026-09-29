package com.example.smartclick.app.data.brandprofile

import com.example.smartclick.app.domain.model.ProbePlan
import com.example.smartclick.app.domain.model.TvProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrandProfileParserTest {

    @Test
    fun `parse valid json parses brands and tiers correctly`() {
        val json = """
            {
              "schemaVersion": 1,
              "brands": [
                { "id": "samsung", "displayName": "Samsung",
                  "probePlan": [
                    { "tier": 1, "protocols": ["SAMSUNG_TIZEN"], "timeoutMs": 4000 },
                    { "tier": 2, "protocols": ["SAMSUNG_LEGACY", "DIAL"], "timeoutMs": 6000 }
                  ]
                },
                { "id": "others", "probePlan": "ALL" }
              ],
              "protocolFlags": {
                "ROKU_ECP": { "enabled": false, "minAppVersion": 2 }
              }
            }
        """.trimIndent()

        val catalog = BrandProfileParser.parse(json)

        assertEquals(1, catalog.schemaVersion)
        assertEquals(2, catalog.brands.size)

        val samsung = catalog.brands[0]
        assertEquals("samsung", samsung.id)
        assertEquals("Samsung", samsung.displayName)

        val samsungPlan = samsung.probePlan as ProbePlan.Tiered
        assertEquals(2, samsungPlan.tiers.size)
        // Tier 1
        assertEquals(1, samsungPlan.tiers[0].tier)
        assertEquals(listOf(TvProtocol.SAMSUNG_TIZEN), samsungPlan.tiers[0].protocols)
        assertEquals(4000L, samsungPlan.tiers[0].timeoutMs)
        // Tier 2 - DIAL alias resolved to DIAL_ONLY
        assertEquals(2, samsungPlan.tiers[1].tier)
        assertEquals(listOf(TvProtocol.SAMSUNG_LEGACY, TvProtocol.DIAL_ONLY), samsungPlan.tiers[1].protocols)

        val others = catalog.brands[1]
        assertEquals("others", others.id)
        assertEquals("others", others.displayName) // fallbacks to id
        assertTrue(others.probePlan is ProbePlan.All)

        val flag = catalog.protocolFlags[TvProtocol.ROKU_ECP]
        assertNotNull(flag)
        assertEquals(false, flag?.enabled)
        assertEquals(2, flag?.minAppVersion)
    }

    @Test
    fun `parse skips unknown or empty protocols and drops empty tiers`() {
        val json = """
            {
              "brands": [
                { "id": "test",
                  "probePlan": [
                    { "tier": 1, "protocols": ["ANDROID_TV_V2", "FUTURE_MAGIC"] },
                    { "tier": 2, "protocols": ["UNKNOWN_ALIAS"] }
                  ]
                }
              ]
            }
        """.trimIndent()

        val catalog = BrandProfileParser.parse(json)
        val plan = catalog.brands[0].probePlan as ProbePlan.Tiered

        // Tier 1 keeps the known protocol, drops the unknown
        assertEquals(1, plan.tiers.size)
        assertEquals(1, plan.tiers[0].tier)
        assertEquals(listOf(TvProtocol.ANDROID_TV_V2), plan.tiers[0].protocols)
        assertEquals(5000L, plan.tiers[0].timeoutMs) // Default timeout

        // Tier 2 was dropped entirely because all its protocols were unknown
    }

    @Test
    fun `parse sorts tiers ascending and skips brands without id`() {
        val json = """
            {
              "brands": [
                { "displayName": "No Id Brand", "probePlan": "ALL" },
                { "id": "sony",
                  "probePlan": [
                    { "tier": 3, "protocols": ["DIAL_ONLY"] },
                    { "tier": 1, "protocols": ["ANDROID_TV_V2"] },
                    { "tier": 2, "protocols": ["SONY_BRAVIA"] }
                  ]
                }
              ]
            }
        """.trimIndent()

        val catalog = BrandProfileParser.parse(json)

        assertEquals(1, catalog.brands.size)
        assertEquals("sony", catalog.brands[0].id)

        val plan = catalog.brands[0].probePlan as ProbePlan.Tiered
        assertEquals(listOf(1, 2, 3), plan.tiers.map { it.tier })
    }

    @Test
    fun `parse handles missing probePlan and missing protocolFlags`() {
        val catalog = BrandProfileParser.parse("""{ "brands": [ { "id": "bare" } ] }""")

        val plan = catalog.brands[0].probePlan as ProbePlan.Tiered
        assertTrue(plan.tiers.isEmpty())
        assertTrue(catalog.protocolFlags.isEmpty())
        assertEquals(1, catalog.schemaVersion) // default when absent
    }

    @Test
    fun `brandById is case insensitive and unknown id returns null`() {
        val catalog = BrandProfileParser.parse("""{ "brands": [ { "id": "apple_tv", "displayName": "Apple TV" } ] }""")

        assertEquals("Apple TV", catalog.brandById("APPLE_TV")?.displayName)
        assertEquals("Apple TV", catalog.brandById("apple_tv")?.displayName)
        assertEquals(null, catalog.brandById("nope"))
    }

    @Test
    fun `isProtocolEnabled honours kill switch and minAppVersion`() {
        val json = """
            {
              "brands": [],
              "protocolFlags": {
                "VIDAA_MQTT": { "enabled": false },
                "ROKU_ECP": { "enabled": true, "minAppVersion": 5 }
              }
            }
        """.trimIndent()

        val catalog = BrandProfileParser.parse(json)

        // Kill-switched off regardless of app version.
        assertEquals(false, catalog.isProtocolEnabled(TvProtocol.VIDAA_MQTT, appVersion = 99))
        // Gated behind a newer app version.
        assertEquals(false, catalog.isProtocolEnabled(TvProtocol.ROKU_ECP, appVersion = 4))
        assertEquals(true, catalog.isProtocolEnabled(TvProtocol.ROKU_ECP, appVersion = 5))
        // No flag at all means enabled.
        assertEquals(true, catalog.isProtocolEnabled(TvProtocol.LG_WEBOS, appVersion = 1))
    }

    /**
     * Bảo vệ asset thật: JSON đóng gói phải parse được và mọi hãng phải có kế hoạch quét
     * dùng được (nếu gõ sai tên giao thức, tier sẽ bị bỏ và test này fail).
     */
    @Test
    fun `shipped brand_profiles asset parses with a usable plan for every brand`() {
        val assetFile = locateAsset()
        assertNotNull("Không tìm thấy assets/brand_profiles.json", assetFile)

        val catalog = BrandProfileParser.parse(assetFile!!.readText())

        assertEquals(1, catalog.schemaVersion)
        assertEquals(16, catalog.brands.size)
        assertTrue(catalog.brandById("others")?.probePlan is ProbePlan.All)

        catalog.brands.forEach { brand ->
            when (val plan = brand.probePlan) {
                is ProbePlan.All -> Unit
                is ProbePlan.Tiered -> {
                    assertTrue("${brand.id} không có tier nào", plan.tiers.isNotEmpty())
                    plan.tiers.forEach { tier ->
                        assertTrue("${brand.id} tier ${tier.tier} rỗng", tier.protocols.isNotEmpty())
                        assertTrue("${brand.id} tier ${tier.tier} timeout sai", tier.timeoutMs > 0)
                    }
                }
            }
        }
    }

    private fun locateAsset(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf("src/main/assets", "app/src/main/assets").forEach { rel ->
                val candidate = File(dir, "$rel/brand_profiles.json")
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }
}
