package com.example.smartclick.app.tvconnection.discovery

import android.content.Context
import com.example.smartclick.app.domain.connection.IDiscoveryProbe
import com.example.smartclick.app.domain.model.TvProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * FW-3: Tạo các [IDiscoveryProbe] phù hợp cho một giao thức.
 */
class ProbeFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Trả về danh sách các Probe cần thiết để quét giao thức tương ứng.
     */
    fun createProbesForProtocol(protocol: TvProtocol): List<IDiscoveryProbe> {
        return when (protocol) {
            TvProtocol.ROKU_ECP -> listOf(
                SsdpProbe(protocol, context, "roku:ecp")
            )
            TvProtocol.ANDROID_TV_V2 -> listOf(
                MdnsProbe(protocol, context, "_androidtvremote2._tcp")
            )
            TvProtocol.ANDROID_TV_V1 -> listOf(
                MdnsProbe(protocol, context, "_androidtvremote._tcp")
            )
            TvProtocol.FIRE_TV -> listOf(
                MdnsProbe(protocol, context, "_amzn-wplay._tcp")
            )
            TvProtocol.SONY_BRAVIA -> listOf(
                PortProbe(protocol, context, listOf(80, 52323))
            )
            TvProtocol.SAMSUNG_TIZEN -> listOf(
                SsdpProbe(protocol, context, "urn:samsung.com:device:RemoteControlReceiver:1"),
                PortProbe(protocol, context, listOf(8001, 8002))
            )
            TvProtocol.SAMSUNG_LEGACY -> listOf(
                SsdpProbe(protocol, context, "urn:samsung.com:device:RemoteControlReceiver:1")
            )
            TvProtocol.LG_WEBOS -> listOf(
                SsdpProbe(protocol, context, "urn:lge-com:service:webos-second-screen:1")
            )
            TvProtocol.LG_NETCAST -> listOf(
                SsdpProbe(protocol, context, "urn:schemas-upnp-org:device:MediaRenderer:1")
            )
            TvProtocol.DIAL_ONLY -> listOf(
                SsdpProbe(protocol, context, "urn:dial-multiscreen-org:service:dial:1")
            )
            TvProtocol.VIZIO_SMARTCAST -> listOf(
                MdnsProbe(protocol, context, "_googlerpc._tcp"),
                MdnsProbe(protocol, context, "_airplay._tcp") // fallback
            )
            TvProtocol.VIDAA_MQTT -> listOf(
                SsdpProbe(protocol, context, "urn:hisense-com:service:remote-control:1")
            )
            TvProtocol.APPLE_COMPANION -> listOf(
                MdnsProbe(protocol, context, "_companion-link._tcp"),
                MdnsProbe(protocol, context, "_airplay._tcp")
            )
            else -> emptyList()
        }
    }

    /**
     * Dành cho mục "Others" (ProbePlan.All).
     */
    fun createAllProbes(): List<IDiscoveryProbe> {
        return listOf(
            SsdpProbe(TvProtocol.UNKNOWN, context, "ssdp:all"),
            MdnsProbe(TvProtocol.UNKNOWN, context, "_androidtvremote2._tcp"),
            MdnsProbe(TvProtocol.UNKNOWN, context, "_androidtvremote._tcp"),
            MdnsProbe(TvProtocol.UNKNOWN, context, "_airplay._tcp"),
            MdnsProbe(TvProtocol.UNKNOWN, context, "_amzn-wplay._tcp"),
            PortProbe(TvProtocol.UNKNOWN, context, listOf(8060, 8001, 8002, 3000, 3001, 6466, 6467, 7345, 9000, 36669, 8080, 5555))
        )
    }
}
