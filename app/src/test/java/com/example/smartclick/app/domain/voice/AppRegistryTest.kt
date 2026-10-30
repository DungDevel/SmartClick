package com.example.smartclick.app.domain.voice

import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.model.voice.AppSymbol
import com.example.smartclick.app.data.catalog.CatalogEntry
import com.example.smartclick.app.data.catalog.ChannelCatalog
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.json.JSONArray
import java.io.File

class AppRegistryTest {

    @Test
    fun `all legacy voice symbols retain IDs through catalog for four protocols`() {
        val entries = ChannelCatalog.parse(File("src/main/assets/channels.json").readText()).getOrThrow()
        val actualRegistry = AppRegistry(mockk<ChannelCatalog>().also { catalog ->
            every { catalog.load() } returns Result.success(entries)
        })
        val legacyApps = JSONArray(File("src/test/resources/apps-legacy-fixture.json").readText())
        val protocols = listOf(
            TvProtocol.LG_WEBOS to "lg_app_id",
            TvProtocol.SAMSUNG_TIZEN to "samsung_app_id",
            TvProtocol.ROKU_ECP to "roku_app_id",
            TvProtocol.ANDROID_TV_V2 to "android_tv_app_id"
        )
        for (i in 0 until legacyApps.length()) {
            val app = legacyApps.getJSONObject(i)
            val symbol = AppSymbol.valueOf(app.getString("symbol"))
            for ((protocol, legacyKey) in protocols) {
                val expected = app.optString(legacyKey).takeIf(String::isNotBlank)
                assertEquals("${symbol.name} / $protocol", expected, actualRegistry.getAppId(symbol, protocol))
            }
        }
        for (protocol in protocols.map { it.first }) {
            assertNull(actualRegistry.getAppId(AppSymbol.UNKNOWN, protocol))
        }
    }

    private lateinit var registry: AppRegistry

    @Before
    fun setup() {
        val catalog = mockk<ChannelCatalog>()
        every { catalog.load() } returns Result.success(listOf(
            CatalogEntry("app:youtube", "YouTube", "apps", mapOf(
                "lgwebos" to "youtube.logan", "samsung" to "111299001912", "roku" to "837",
                "androidtv" to "com.google.android.youtube.tv"
            ), emptyList(), "app"),
            CatalogEntry("app:browser", "Browser", "apps", mapOf("samsung" to "org.tizen.browser"), emptyList(), "app")
        ))
        registry = AppRegistry(catalog)
    }

    @Test
    fun `getAppId correctly maps YOUTUBE across protocols`() {
        assertEquals("youtube.logan", registry.getAppId(AppSymbol.YOUTUBE, TvProtocol.LG_WEBOS))
        assertEquals("111299001912", registry.getAppId(AppSymbol.YOUTUBE, TvProtocol.SAMSUNG_TIZEN))
        assertEquals("837", registry.getAppId(AppSymbol.YOUTUBE, TvProtocol.ROKU_ECP))
        assertEquals("com.google.android.youtube.tv", registry.getAppId(AppSymbol.YOUTUBE, TvProtocol.ANDROID_TV_V2))
    }

    @Test
    fun `getAppId returns null for UNKNOWN app`() {
        assertNull(registry.getAppId(AppSymbol.UNKNOWN, TvProtocol.LG_WEBOS))
    }

    @Test
    fun `getAppId returns null for BROWSER on Roku`() {
        assertNull(registry.getAppId(AppSymbol.BROWSER, TvProtocol.ROKU_ECP))
    }
}
