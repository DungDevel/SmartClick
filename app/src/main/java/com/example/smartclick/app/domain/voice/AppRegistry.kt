package com.example.smartclick.app.domain.voice

import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.model.voice.AppSymbol
import com.example.smartclick.app.data.catalog.ChannelCatalog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRegistry @Inject constructor(private val catalog: ChannelCatalog) {

    fun getAppId(symbol: AppSymbol, protocol: TvProtocol): String? {
        val protocolKey = when (protocol) {
            TvProtocol.LG_WEBOS, TvProtocol.LG_NETCAST -> "lgwebos"
            TvProtocol.SAMSUNG_TIZEN, TvProtocol.SAMSUNG_LEGACY -> "samsung"
            TvProtocol.ROKU_ECP -> "roku"
            TvProtocol.ANDROID_TV_V2, TvProtocol.ANDROID_TV_V1 -> "androidtv"
            else -> return null
        }
        val entryId = "app:${symbol.name.lowercase()}"
        return catalog.load().getOrNull()
            ?.firstOrNull { it.id == entryId && it.type == "app" }
            ?.protocolIds?.get(protocolKey)
    }
}
