package com.example.smartclick.app.domain.voice

import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.model.voice.AppSymbol
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRegistry @Inject constructor() {

    fun getAppId(symbol: AppSymbol, protocol: TvProtocol): String? {
        return when (protocol) {
            TvProtocol.LG_WEBOS, TvProtocol.LG_NETCAST -> getLgAppId(symbol)
            TvProtocol.SAMSUNG_TIZEN, TvProtocol.SAMSUNG_LEGACY -> getSamsungAppId(symbol)
            TvProtocol.ROKU_ECP -> getRokuAppId(symbol)
            TvProtocol.ANDROID_TV_V2, TvProtocol.ANDROID_TV_V1 -> getAndroidTvAppId(symbol)
            else -> getGenericAppId(symbol) // Optional fallback
        }
    }

    private fun getLgAppId(symbol: AppSymbol): String? = when (symbol) {
        AppSymbol.YOUTUBE -> "youtube.logan" // Older LG might use youtube.leanback.v4
        AppSymbol.NETFLIX -> "netflix"
        AppSymbol.PRIME_VIDEO -> "amazon"
        AppSymbol.HULU -> "hulu"
        AppSymbol.DISNEY_PLUS -> "disney"
        AppSymbol.SPOTIFY -> "spotify"
        AppSymbol.APPLE_TV -> "apple.app"
        AppSymbol.HBO_MAX -> "hbo.max"
        AppSymbol.BROWSER -> "com.webos.app.browser"
        AppSymbol.UNKNOWN -> null
    }

    private fun getSamsungAppId(symbol: AppSymbol): String? = when (symbol) {
        AppSymbol.YOUTUBE -> "111299001912"
        AppSymbol.NETFLIX -> "11101200001"
        AppSymbol.PRIME_VIDEO -> "3201512006785"
        AppSymbol.HULU -> "3201601007625"
        AppSymbol.DISNEY_PLUS -> "3201901017640"
        AppSymbol.SPOTIFY -> "3201606009684"
        AppSymbol.APPLE_TV -> "3201807016597"
        AppSymbol.HBO_MAX -> "3201601007230"
        AppSymbol.BROWSER -> "org.tizen.browser" // or similar
        AppSymbol.UNKNOWN -> null
    }

    // Roku uses numeric IDs for standard edge apps
    private fun getRokuAppId(symbol: AppSymbol): String? = when (symbol) {
        AppSymbol.YOUTUBE -> "837"
        AppSymbol.NETFLIX -> "12"
        AppSymbol.PRIME_VIDEO -> "13"
        AppSymbol.HULU -> "2285"
        AppSymbol.DISNEY_PLUS -> "291097"
        AppSymbol.SPOTIFY -> "19977"
        AppSymbol.APPLE_TV -> "551012"
        AppSymbol.HBO_MAX -> "61322"
        AppSymbol.BROWSER -> null // No native browser on roku
        AppSymbol.UNKNOWN -> null
    }

    // Android TV Intents are usually deep links or Android package names
    private fun getAndroidTvAppId(symbol: AppSymbol): String? = when (symbol) {
        AppSymbol.YOUTUBE -> "com.google.android.youtube.tv"
        AppSymbol.NETFLIX -> "com.netflix.ninja"
        AppSymbol.PRIME_VIDEO -> "com.amazon.amazonvideo.livingroom"
        AppSymbol.HULU -> "com.hulu.livingroomplus"
        AppSymbol.DISNEY_PLUS -> "com.disney.disneyplus"
        AppSymbol.SPOTIFY -> "com.spotify.tv.android"
        AppSymbol.APPLE_TV -> "com.apple.atve.androidtv.appletv"
        AppSymbol.HBO_MAX -> "com.hbo.hbonow"
        AppSymbol.BROWSER -> "com.android.chrome" // Not common on ATV but possible
        AppSymbol.UNKNOWN -> null
    }

    private fun getGenericAppId(symbol: AppSymbol): String? = null
}
