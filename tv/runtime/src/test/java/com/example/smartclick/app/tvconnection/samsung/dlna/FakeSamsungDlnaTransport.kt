package com.example.smartclick.app.tvconnection.samsung.dlna

class FakeSamsungDlnaTransport(
    private val deviceDescriptionXml: String,
    private val soapResponses: Map<String, String> = emptyMap(),
    private val shouldFail: Boolean = false
) : SamsungDlnaTransport {

    var fetchedDeviceDescriptionUrl: String? = null
    val soapActionsCalled = mutableListOf<Pair<String, String>>()

    override suspend fun fetchDeviceDescription(url: String): String? {
        if (shouldFail) throw Exception("Network fail")
        fetchedDeviceDescriptionUrl = url
        return deviceDescriptionXml
    }

    override suspend fun sendSoapAction(controlUrl: String, actionName: String, soapXml: String): String {
        if (shouldFail) throw Exception("Network fail")
        soapActionsCalled.add(actionName to soapXml)
        return soapResponses[actionName] ?: "<s:Envelope><s:Body><u:${actionName}Response/></s:Body></s:Envelope>"
    }
}
