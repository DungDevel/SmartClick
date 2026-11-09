package com.example.smartclick.app.tvconnection.dial

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.Fingerprint
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.tvconnection.utils.NetworkSecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Provider

class DialDriver @Inject constructor(
    private val dialControllerProvider: Provider<DialController>,
    private val httpClient: OkHttpClient
) : IProtocolDriver {

    override val protocol: TvProtocol = TvProtocol.DIAL_ONLY

    override val capabilities: Set<TvCapability> = emptySet()

    private val redirectDisabledClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun fingerprint(candidate: RawCandidate): Fingerprint? = withContext(Dispatchers.IO) {
        if (candidate.protocolHint != TvProtocol.UNKNOWN && candidate.protocolHint != TvProtocol.DIAL_ONLY) {
            return@withContext null
        }

        if (!NetworkSecurityUtils.isPrivateLanAddress(candidate.ipAddress)) {
            return@withContext null
        }

        val location = candidate.location
        var displayName = "DIAL Device"
        var stableId = "dial-${candidate.ipAddress}"

        if (location != null && NetworkSecurityUtils.isPrivateLanAddress(extractHostFromUrl(location) ?: "")) {
            try {
                val req = Request.Builder().url(location).build()
                redirectDisabledClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
                            val parser = factory.newPullParser()
                            parser.setInput(StringReader(body))
                            var eventType = parser.eventType
                            var currentTag = ""
                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                when (eventType) {
                                    XmlPullParser.START_TAG -> currentTag = parser.name
                                    XmlPullParser.TEXT -> {
                                        val text = parser.text.trim()
                                        if (text.isNotEmpty() && currentTag.equals("friendlyName", ignoreCase = true)) {
                                            displayName = text
                                        }
                                        if (text.isNotEmpty() && currentTag.equals("UDN", ignoreCase = true)) {
                                            stableId = "dial-$text"
                                        }
                                    }
                                    XmlPullParser.END_TAG -> currentTag = ""
                                }
                                eventType = parser.next()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore fingerprint fetch failure
            }
        }

        if (candidate.protocolHint == TvProtocol.DIAL_ONLY || location != null) {
            return@withContext Fingerprint(
                protocol = TvProtocol.DIAL_ONLY,
                stableId = stableId,
                displayName = displayName,
                confidence = if (location != null) Fingerprint.Confidence.HIGH else Fingerprint.Confidence.MEDIUM,
                macAddress = candidate.attributes["mac"] ?: ""
            )
        }

        null
    }

    override fun pair(device: TvDevice): Flow<PairingStep> {
        return flowOf(PairingStep.NotRequired, PairingStep.Succeeded(null))
    }

    override fun createController(device: TvDevice, credentials: Credentials?): ITvController {
        return dialControllerProvider.get()
    }

    private fun extractHostFromUrl(url: String): String? {
        val afterScheme = url.removePrefix("http://").removePrefix("https://")
        val hostPort = afterScheme.substringBefore("/")
        return hostPort.substringBefore(":").ifBlank { null }
    }
}
