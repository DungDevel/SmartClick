package com.example.smartclick.app.tvconnection.wifi

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Provider

/**
 * FW-4: Driver cụ thể cho Roku ECP.
 */
class RokuDriver @Inject constructor(
    private val rokuControllerProvider: Provider<RokuController>,
    private val httpClient: OkHttpClient
) : IProtocolDriver {
    override val protocol: TvProtocol = TvProtocol.ROKU_ECP

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.KEYBOARD_TEXT,
        TvCapability.INSTALLED_APPS, // Roku hỗ trợ launch app và cài đặt app list
        TvCapability.CHANNEL_CONTROL,
        TvCapability.SEARCH_CONTENT
    )

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? = withContext(Dispatchers.IO) {
        if (candidate.protocolHint != TvProtocol.UNKNOWN && candidate.protocolHint != TvProtocol.ROKU_ECP) {
            return@withContext null
        }

        try {
            val request = Request.Builder()
                .url("http://${candidate.ipAddress}:8060/query/device-info")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }

                val xmlData = response.body?.string() ?: return@withContext null

                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val parser = factory.newPullParser()
                parser.setInput(StringReader(xmlData))

                var eventType = parser.eventType
                var serialNumber: String? = null
                var friendlyName: String? = null
                var defaultDeviceName: String? = null
                var currentTag = ""

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> currentTag = parser.name
                        XmlPullParser.TEXT -> {
                            val text = parser.text.trim()
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "serial-number" -> serialNumber = text
                                    "friendly-device-name" -> friendlyName = text
                                    "default-device-name" -> defaultDeviceName = text
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> currentTag = ""
                    }
                    eventType = parser.next()
                }

                if (serialNumber != null) {
                    val displayName = friendlyName ?: defaultDeviceName ?: "Roku TV"
                    return@withContext Fingerprint(
                        protocol = TvProtocol.ROKU_ECP,
                        stableId = serialNumber,
                        displayName = displayName,
                        confidence = Fingerprint.Confidence.HIGH,
                        macAddress = ""
                    )
                }
            }
        } catch (e: Exception) {
            // Request failed or parsing failed
        }

        // Cố gắng fallback nếu request lỗi nhưng có hint từ SSDP/mDNS
        if (candidate.protocolHint == TvProtocol.ROKU_ECP) {
            val fallbackId = candidate.usn ?: candidate.serviceName ?: return@withContext null
            return@withContext Fingerprint(
                protocol = TvProtocol.ROKU_ECP,
                stableId = "roku-$fallbackId",
                displayName = "Roku TV",
                confidence = Fingerprint.Confidence.LOW,
                macAddress = ""
            )
        }
        null
    }

    override fun pair(device: TvDevice): Flow<PairingStep> {
        return flowOf(PairingStep.NotRequired, PairingStep.Succeeded(null))
    }

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        return rokuControllerProvider.get()
    }
}
