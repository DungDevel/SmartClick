package com.example.smartclick.app.tvconnection.samsung.dlna

import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

data class UpnpDevice(
    val deviceType: String,
    val friendlyName: String,
    val avTransportControlUrl: String?
)

object UpnpDeviceParser {
    fun parseDeviceDescription(xml: String): UpnpDevice? {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false // DO NOT use namespace aware to simplify tag matching
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))
            doc.documentElement.normalize()

            val deviceNodes = doc.getElementsByTagName("device")
            if (deviceNodes.length == 0) return null

            val rootDevice = deviceNodes.item(0) as Element
            val deviceType = getChildNodeText(rootDevice, "deviceType")
            val friendlyName = getChildNodeText(rootDevice, "friendlyName")

            var avTransportControlUrl: String? = null
            val serviceNodes = doc.getElementsByTagName("service")
            for (i in 0 until serviceNodes.length) {
                val serviceParams = serviceNodes.item(i) as Element
                val serviceType = getChildNodeText(serviceParams, "serviceType")
                if (serviceType?.contains("AVTransport") == true) {
                    avTransportControlUrl = getChildNodeText(serviceParams, "controlURL")
                    break
                }
            }

            if (deviceType?.contains("MediaRenderer") == true) {
                return UpnpDevice(
                    deviceType = deviceType,
                    friendlyName = friendlyName ?: "Unknown",
                    avTransportControlUrl = avTransportControlUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getChildNodeText(parent: Element, tagName: String): String? {
        val nodes = parent.getElementsByTagName(tagName)
        if (nodes.length > 0) {
            val node = nodes.item(0)
            return node.textContent?.trim()
        }
        return null
    }

    fun resolveControlUrl(locationUrl: String, controlUrlPath: String): String {
        return try {
            java.net.URI(locationUrl).resolve(controlUrlPath).toString()
        } catch (e: Exception) {
            val base = if (locationUrl.endsWith("/")) locationUrl.dropLast(1) else locationUrl
            val path = if (controlUrlPath.startsWith("/")) controlUrlPath else "/$controlUrlPath"
            val uriSlashIndex = base.indexOf("/", base.indexOf("://") + 3)
            val hostPart = if (uriSlashIndex != -1) base.substring(0, uriSlashIndex) else base
            hostPart + path
        }
    }
}


