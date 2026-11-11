package com.example.smartclick.app.tvconnection.samsung.dlna

interface SamsungDlnaTransport {
    /**
     * Executes an HTTP GET to fetch the Device Description XML.
     * @return The XML response body, or null if the request failed.
     */
    suspend fun fetchDeviceDescription(url: String): String?

    /**
     * Executes a UPnP SOAP action POST.
     * @return The response body if successful HTTP 200, or throws an exception on failure/SOAP fault.
     */
    suspend fun sendSoapAction(controlUrl: String, actionName: String, soapXml: String): String
}
