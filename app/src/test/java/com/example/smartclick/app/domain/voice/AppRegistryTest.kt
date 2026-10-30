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

class AppRegistryTest {

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
