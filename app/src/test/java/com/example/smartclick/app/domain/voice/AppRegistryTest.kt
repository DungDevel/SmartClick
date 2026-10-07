package com.example.smartclick.app.domain.voice

import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.model.voice.AppSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AppRegistryTest {

    private lateinit var registry: AppRegistry

    @Before
    fun setup() {
        registry = AppRegistry()
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
