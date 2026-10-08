package com.example.smartclick.app.tvconnection.samsung.dlna

object UpnpSoapModels {

    fun createSetAVTransportURIRequest(mediaUrl: String, didlLiteXml: String): String {
        return buildSoapEnvelope("SetAVTransportURI", """
            <InstanceID>0</InstanceID>
            <CurrentURI>${escapeXml(mediaUrl)}</CurrentURI>
            <CurrentURIMetaData>${escapeXml(didlLiteXml)}</CurrentURIMetaData>
        """.trimIndent())
    }

    fun createPlayRequest(): String {
        return buildSoapEnvelope("Play", """
            <InstanceID>0</InstanceID>
            <Speed>1</Speed>
        """.trimIndent())
    }

    fun createPauseRequest(): String {
        return buildSoapEnvelope("Pause", """
            <InstanceID>0</InstanceID>
        """.trimIndent())
    }

    fun createStopRequest(): String {
        return buildSoapEnvelope("Stop", """
            <InstanceID>0</InstanceID>
        """.trimIndent())
    }

    fun createSeekRequest(timeTargetFormatted: String): String {
        return buildSoapEnvelope("Seek", """
            <InstanceID>0</InstanceID>
            <Unit>REL_TIME</Unit>
            <Target>${escapeXml(timeTargetFormatted)}</Target>
        """.trimIndent())
    }

    fun createDidlLite(title: String, mediaUrl: String, mimeType: String, objectClass: String): String {
        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                <item id="1000" parentID="0" restricted="0">
                    <dc:title>${escapeXml(title)}</dc:title>
                    <res protocolInfo="http-get:*:${escapeXml(mimeType)}:*">${escapeXml(mediaUrl)}</res>
                    <upnp:class>${escapeXml(objectClass)}</upnp:class>
                </item>
            </DIDL-Lite>
        """.trimIndent().replace("\n", "").replace("> <", "><")
    }

    private fun buildSoapEnvelope(actionName: String, bodyHtml: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/" xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                <s:Body>
                    <u:$actionName xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        $bodyHtml
                    </u:$actionName>
                </s:Body>
            </s:Envelope>
        """.trimIndent().replace("\n", "")
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

