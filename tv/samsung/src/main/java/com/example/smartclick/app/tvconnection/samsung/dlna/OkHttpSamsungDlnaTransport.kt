package com.example.smartclick.app.tvconnection.samsung.dlna

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OkHttpSamsungDlnaTransport(
    private val client: OkHttpClient
) : SamsungDlnaTransport {

    override suspend fun fetchDeviceDescription(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun sendSoapAction(controlUrl: String, actionName: String, soapXml: String): String = withContext(Dispatchers.IO) {
        val mediaType = "text/xml; charset=\"utf-8\"".toMediaType()
        val requestBody = soapXml.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(controlUrl)
            .post(requestBody)
            .header("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$actionName\"")
            .build()

        val response = client.newCall(request).execute()
        val responseStr = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            // Attempt to parse SOAP Fault if available
            throw Exception("HTTP ${response.code}: $responseStr")
        }

        responseStr
    }
}
