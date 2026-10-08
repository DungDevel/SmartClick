package com.example.smartclick.app.tvconnection.samsung.dlna

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.cast.CastSessionInfo
import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.model.CastMediaType
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.tvconnection.cast.LocalMediaServer
import com.example.smartclick.app.tvconnection.cast.CastMediaSession
import com.example.smartclick.app.tvconnection.discovery.SsdpProbe
import com.example.smartclick.app.tvconnection.utils.NetworkSecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

class SamsungDlnaCastController(
    private val context: Context,
    private val transport: SamsungDlnaTransport,
    private val localMediaServer: LocalMediaServer,
    private val targetIp: String
) : ICastController {

    private val _castPlaybackState = MutableStateFlow<CastPlaybackState>(CastPlaybackState.IDLE)
    override val castPlaybackState: StateFlow<CastPlaybackState> = _castPlaybackState.asStateFlow()

    private val _castSessionInfo = MutableStateFlow<CastSessionInfo>(CastSessionInfo())
    override val castSessionInfo: StateFlow<CastSessionInfo> = _castSessionInfo.asStateFlow()

    private var currentControlUrl: String? = null
    private var currentMediaSession: CastMediaSession? = null

    override suspend fun castMedia(
        url: String,
        mimeType: String,
        type: CastMediaType,
        title: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Re-discover Control URL if not present. In MVP, we might just discover it on the fly.
            val controlUrl = discoverAndResolveControlUrl()
                ?: return@withContext Result.failure(CastError.DeviceNotFound())

            currentControlUrl = controlUrl

            // Clean up previous registration
            currentMediaSession?.let {
                localMediaServer.unregisterMedia(it.sessionId)
            }

            val uri = Uri.parse(url)
            // Use existing LocalMediaServer API
            val mediaSession = localMediaServer.registerMedia(uri, mimeType, title)
            currentMediaSession = mediaSession

            val objectClass = when (type) {
                CastMediaType.VIDEO -> "object.item.videoItem"
                CastMediaType.AUDIO -> "object.item.audioItem"
                CastMediaType.PHOTO -> "object.item.imageItem"
            }

            val didlLiteXml = UpnpSoapModels.createDidlLite(title, mediaSession.mediaUrl, mimeType, objectClass)
            val soapRequest = UpnpSoapModels.createSetAVTransportURIRequest(mediaSession.mediaUrl, didlLiteXml)

            try {
                transport.sendSoapAction(controlUrl, "SetAVTransportURI", soapRequest)
            } catch (e: Exception) {
                return@withContext mapSoapException(e)
            }

            _castSessionInfo.value = CastSessionInfo(currentUrl = mediaSession.mediaUrl)
            _castPlaybackState.value = CastPlaybackState.BUFFERING

            // Auto play
            controlCast(CastCommand.Play)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(CastError.MediaRegistrationFailed(e))
        }
    }

    override suspend fun controlCast(command: CastCommand): Result<Unit> = withContext(Dispatchers.IO) {
        val controlUrl = currentControlUrl ?: return@withContext Result.failure(CastError.DeviceNotFound())

        try {
            when (command) {
                is CastCommand.Play -> {
                    val soap = UpnpSoapModels.createPlayRequest()
                    transport.sendSoapAction(controlUrl, "Play", soap)
                    Result.success(Unit)
                }
                is CastCommand.Pause -> {
                    val soap = UpnpSoapModels.createPauseRequest()
                    transport.sendSoapAction(controlUrl, "Pause", soap)
                    Result.success(Unit)
                }
                is CastCommand.Stop -> {
                    val soap = UpnpSoapModels.createStopRequest()
                    transport.sendSoapAction(controlUrl, "Stop", soap)
                    _castPlaybackState.value = CastPlaybackState.IDLE
                    _castSessionInfo.value = CastSessionInfo()

                    currentMediaSession?.let {
                        localMediaServer.unregisterMedia(it.sessionId)
                        currentMediaSession = null
                    }
                    Result.success(Unit)
                }
                is CastCommand.SeekTo -> {
                    val target = formatRelTime(command.positionMs)
                    val soap = UpnpSoapModels.createSeekRequest(target)
                    try {
                        transport.sendSoapAction(controlUrl, "Seek", soap)
                        Result.success(Unit)
                    } catch (e: Exception) {
                        // Determine if it is unsupported
                        if (e.message?.contains("710") == true || e.message?.contains("711") == true || e.message?.contains("Seek mode not supported") == true) {
                            Result.failure(CastError.SeekNotSupported())
                        } else {
                            mapSoapException(e)
                        }
                    }
                }
                is CastCommand.SeekBy -> {
                    Result.failure(CastError.UnsupportedCommand("SeekBy")) // Need GetPositionInfo first to support relative SeekBy
                }
                else -> {
                    Result.failure(CastError.UnsupportedCommand(command.javaClass.simpleName))
                }
            }
        } catch (e: Exception) {
            mapSoapException(e)
        }
    }

    override suspend fun teardown(): Result<Unit> = withContext(Dispatchers.IO) {
        val controlUrl = currentControlUrl
        val session = currentMediaSession

        _castPlaybackState.value = CastPlaybackState.IDLE
        _castSessionInfo.value = CastSessionInfo()
        currentMediaSession = null
        currentControlUrl = null

        session?.let {
            localMediaServer.unregisterMedia(it.sessionId)
        }

        if (controlUrl != null) {
            try {
                val soap = UpnpSoapModels.createStopRequest()
                transport.sendSoapAction(controlUrl, "Stop", soap)
            } catch (e: Exception) {
                // Ignore teardown transport exceptions since we are closing anyway
            }
        }
        Result.success(Unit)
    }

    // VisibleForTesting
    internal var ssdpLocationProvider: suspend () -> String? = {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        if (activeNetwork != null) {
            val probe = SsdpProbe(TvProtocol.SAMSUNG_TIZEN, context, "urn:schemas-upnp-org:device:MediaRenderer:1", 4000)
            var loc: String? = null
            probe.discover(activeNetwork).take(5).collect { candidate ->
                if (candidate.ipAddress == targetIp && !candidate.location.isNullOrEmpty()) {
                    loc = candidate.location
                }
            }
            loc
        } else {
            null
        }
    }

    private suspend fun discoverAndResolveControlUrl(): String? {
        val location = ssdpLocationProvider()
        val locUrl = location ?: return null

        if (!locUrl.startsWith("http://", ignoreCase = true)) {
            return null // Reject non-http schemas
        }

        val deviceDescXml = transport.fetchDeviceDescription(locUrl) ?: return null

        val device = UpnpDeviceParser.parseDeviceDescription(deviceDescXml) ?: return null
        val avUrl = device.avTransportControlUrl ?: return null

        return UpnpDeviceParser.resolveControlUrl(locUrl, avUrl)
    }

    private fun mapSoapException(e: Exception): Result<Unit> {
        val msg = e.message ?: ""
        return if (msg.contains("HTTP 5")) {
            // Rough mapping for SOAP Faults
            Result.failure(CastError.SoapFault("500", msg))
        } else {
            Result.failure(CastError.NetworkError(e))
        }
    }

    private fun formatRelTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        val h = (ms / (1000 * 60 * 60)) // Can be > 24
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}
