package com.example.smartclick.app.tvconnection.wifi

import android.util.Log
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvApp
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.cast.CastSessionInfo
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.model.CastMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import com.example.smartclick.app.tvconnection.utils.NetworkSecurityUtils

class RokuController @Inject constructor(
    client: OkHttpClient
) : ITvController, ICastController {

    private val tvClient = client.newBuilder()
        .connectTimeout(1000, TimeUnit.MILLISECONDS)
        .readTimeout(1000, TimeUnit.MILLISECONDS)
        .callTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    private var currentDevice: TvDevice? = null
    override val device: TvDevice? get() = currentDevice

    private val _castPlaybackState = MutableStateFlow(CastPlaybackState.IDLE)
    override val castPlaybackState: StateFlow<CastPlaybackState> = _castPlaybackState.asStateFlow()

    private val _castSessionInfo = MutableStateFlow(CastSessionInfo())
    override val castSessionInfo: StateFlow<CastSessionInfo> = _castSessionInfo.asStateFlow()

    override val capabilities: Set<TvCapability> = RokuDriver.ROKU_CAPABILITIES

    private val emptyBody = "".toRequestBody("text/plain".toMediaType())

    private val commandMutex = Mutex()
    private var cumulativeDx = 0f
    private var cumulativeDy = 0f
    private val swipeThreshold = 50f

    override suspend fun connect(device: TvDevice): Result<Unit> {
        if (!NetworkSecurityUtils.isPrivateLanAddress(device.ipAddress)) {
            val err = "Invalid address: not a private LAN IP"
            _connectionState.value = TvConnectionState.Failed(err)
            Log.w(TAG, "Rejected non-LAN address: ${device.ipAddress}")
            return Result.failure(Exception(err))
        }

        _connectionState.value = TvConnectionState.Connecting(com.example.smartclick.app.domain.connection.ConnectProgress.CONNECTING_SOCKET)
        currentDevice = device

        return withContext(Dispatchers.IO) {
            try {
                val url = "http://${device.ipAddress}:8060/query/device-info"
                val request = Request.Builder().url(url).build()

                tvClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        _connectionState.value = TvConnectionState.Connected(device)
                        Log.d(TAG, "Successfully connected to Roku at ${device.ipAddress}")
                        Result.success(Unit)
                    } else {
                        val msg = if (response.code == 403) "Mở cấu hình TV: Settings > System > Advanced system settings > Control by mobile apps > Bật 'Default' hoặc 'Permissive'" else "Roku HTTP Error: ${response.code}"
                        _connectionState.value = TvConnectionState.Failed(msg)
                        Result.failure(Exception(msg))
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Connection failed", e)
                val msg = "Connection failed: ${e.message}"
                _connectionState.value = TvConnectionState.Failed(msg)
                Result.failure(Exception(msg))
            }
        }
    }

    override suspend fun disconnect() {
        _connectionState.value = TvConnectionState.Idle
        currentDevice = null
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        val dev = currentDevice ?: return Result.failure(Exception("No active device"))
        if (_connectionState.value !is TvConnectionState.Connected) return Result.failure(Exception("Not connected"))

        return commandMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    when (command) {
                        is RemoteCommand.Text -> {
                            var lastResult: Result<Unit> = Result.success(Unit)
                            for (char in command.text) {
                                // encode individual character, replacing '+' with '%20' for spaces
                                val encoded = URLEncoder.encode(char.toString(), "UTF-8").replace("+", "%20")
                                val url = "http://${dev.ipAddress}:8060/keypress/Lit_$encoded"
                                val result = postToRoku(url)
                                if (result.isFailure) {
                                    lastResult = result
                                    break
                                }
                            }
                            lastResult
                        }
                        is RemoteCommand.PointerMove -> {
                            cumulativeDx += command.dx
                            cumulativeDy += command.dy

                            val keysToPress = mutableListOf<String>()
                            while (abs(cumulativeDx) > swipeThreshold) {
                                if (cumulativeDx > 0) {
                                    keysToPress.add("Right")
                                    cumulativeDx -= swipeThreshold
                                } else {
                                    keysToPress.add("Left")
                                    cumulativeDx += swipeThreshold
                                }
                            }

                            while (abs(cumulativeDy) > swipeThreshold) {
                                if (cumulativeDy > 0) {
                                    keysToPress.add("Down")
                                    cumulativeDy -= swipeThreshold
                                } else {
                                    keysToPress.add("Up")
                                    cumulativeDy += swipeThreshold
                                }
                            }

                            if (keysToPress.isEmpty()) {
                                Result.success(Unit)
                            } else {
                                var lastRes = Result.success(Unit)
                                for (key in keysToPress) {
                                    val url = "http://${dev.ipAddress}:8060/keypress/$key"
                                    lastRes = postToRoku(url)
                                    if (lastRes.isFailure) break
                                }
                                lastRes
                            }
                        }
                        is RemoteCommand.SearchContent -> {
                            val encodedQuery = URLEncoder.encode(command.query, "UTF-8").replace("+", "%20")
                            val url = "http://${dev.ipAddress}:8060/search/browse?keyword=$encodedQuery"
                            postToRoku(url)
                        }
                        else -> {
                            val key = mapCommandToRokuKey(command)
                            if (key == null) {
                                Result.success(Unit)
                            } else {
                                val url = "http://${dev.ipAddress}:8060/keypress/$key"
                                postToRoku(url)
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to send command to Roku", e)
                    Result.failure(e)
                }
            }
        }
    }

    private fun postToRoku(url: String): Result<Unit> {
        val request = Request.Builder()
            .url(url)
            .post(emptyBody)
            .build()
        tvClient.newCall(request).execute().use { response ->
            return if (!response.isSuccessful) {
                Log.w(TAG, "Command failed: ${response.code} at $url")
                Result.failure(Exception("HTTP Error: ${response.code}"))
            } else {
                Result.success(Unit)
            }
        }
    }

    override suspend fun getApps(): Result<List<TvApp>> {
        val dev = currentDevice ?: return Result.failure(Exception("No active device"))
        if (_connectionState.value !is TvConnectionState.Connected) return Result.failure(Exception("Not connected"))

        return withContext(Dispatchers.IO) {
            try {
                val url = "http://${dev.ipAddress}:8060/query/apps"
                val request = Request.Builder().url(url).build()

                tvClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                    }

                    val xmlData = response.body?.string() ?: return@withContext Result.defaultSuccess()

                    val appList = mutableListOf<TvApp>()
                    val factory = XmlPullParserFactory.newInstance()
                    factory.isNamespaceAware = true
                    val parser = factory.newPullParser()
                    parser.setInput(StringReader(xmlData))

                    var eventType = parser.eventType
                    var currentAppId: String? = null
                    var currentAppName: String? = null

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        val tag = parser.name
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                if (tag == "app") {
                                    currentAppId = parser.getAttributeValue(null, "id")
                                }
                            }
                            XmlPullParser.TEXT -> {
                                if (currentAppId != null && !parser.isWhitespace) {
                                    currentAppName = parser.text
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                if (tag == "app") {
                                    if (currentAppId != null && currentAppName != null) {
                                        val iconUrl = "http://${dev.ipAddress}:8060/query/icon/$currentAppId"
                                        appList.add(TvApp(currentAppId, currentAppName, iconUrl))
                                    }
                                    currentAppId = null
                                    currentAppName = null
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    Result.success(appList)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get apps", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun launchApp(appId: String): Result<Unit> {
        val dev = currentDevice ?: return Result.failure(Exception("No active device"))
        if (_connectionState.value !is TvConnectionState.Connected) return Result.failure(Exception("Not connected"))

        return withContext(Dispatchers.IO) {
            try {
                val url = "http://${dev.ipAddress}:8060/launch/$appId"
                val request = Request.Builder()
                    .url(url)
                    .post(emptyBody)
                    .build()

                tvClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("HTTP Error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch app $appId", e)
                Result.failure(e)
            }
        }
    }


    override suspend fun castMedia(url: String, mimeType: String, type: CastMediaType, title: String): Result<Unit> {
        val dev = currentDevice ?: return Result.failure(Exception("No active device"))
        if (_connectionState.value !is TvConnectionState.Connected) return Result.failure(Exception("Not connected"))

        return withContext(Dispatchers.IO) {
            try {
                val encodedUrl = URLEncoder.encode(url, "UTF-8")
                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                val t = when (type) {
                    CastMediaType.VIDEO -> "v"
                    CastMediaType.PHOTO -> "p"
                    CastMediaType.AUDIO -> "a"
                }

                val format = mimeType.substringAfterLast("/", "mp4")
                var reqUrl = "http://${dev.ipAddress}:8060/input/15985?t=$t&u=$encodedUrl&videoName=$encodedTitle"

                if (type == CastMediaType.VIDEO) {
                    reqUrl += "&videoFormat=$format"
                } else if (type == CastMediaType.AUDIO) {
                    reqUrl += "&songFormat=$format&songName=$encodedTitle"
                }

                postToRoku(reqUrl).also {
                    if (it.isSuccess) {
                        _castPlaybackState.value = CastPlaybackState.BUFFERING
                        _castSessionInfo.value = CastSessionInfo(currentUrl = url)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cast media to Roku", e)
                _castPlaybackState.value = CastPlaybackState.ERROR
                Result.failure(e)
            }
        }
    }

    override suspend fun controlCast(command: CastCommand): Result<Unit> {
        return when (command) {
            is CastCommand.Play -> sendCommand(RemoteCommand.PlayPause)
            is CastCommand.Pause -> sendCommand(RemoteCommand.PlayPause) // Roku toggles play/pause with same key
            is CastCommand.Stop -> {
                _castPlaybackState.value = CastPlaybackState.IDLE
                sendCommand(RemoteCommand.Back)
            } // Stops cast by going back
            is CastCommand.SeekBy -> {
                if (command.offsetMs > 0) sendCommand(RemoteCommand.FastForward) else sendCommand(RemoteCommand.Rewind)
            }
            is CastCommand.SetVolume -> {
                // Roku basic ECP does not support direct set volume typically via standard API,
                // but can potentially loop VolumeUp/Down. Not implemented.
                Result.failure(Exception("Unsupported cast command on Roku via ECP basic remote"))
            }
            is CastCommand.Mute -> sendCommand(RemoteCommand.MuteOn)
            is CastCommand.Unmute -> sendCommand(RemoteCommand.MuteOff)
            else -> Result.failure(Exception("Unsupported cast command on Roku via ECP basic remote"))
        }
    }

    override suspend fun teardown(): Result<Unit> {
        _castPlaybackState.value = CastPlaybackState.IDLE
        _castSessionInfo.value = CastSessionInfo()
        return sendCommand(RemoteCommand.Back)
    }

    private fun mapCommandToRokuKey(cmd: RemoteCommand): String? {
        return when (cmd) {
            is RemoteCommand.Power -> "Power"
            is RemoteCommand.PowerOn,
            is RemoteCommand.PowerOff -> null
            is RemoteCommand.VolumeUp -> "VolumeUp"
            is RemoteCommand.VolumeDown -> "VolumeDown"
            is RemoteCommand.Mute,
            is RemoteCommand.MuteOn,
            is RemoteCommand.MuteOff -> "VolumeMute"
            is RemoteCommand.DPadUp -> "Up"
            is RemoteCommand.DPadDown -> "Down"
            is RemoteCommand.DPadLeft -> "Left"
            is RemoteCommand.DPadRight -> "Right"
            is RemoteCommand.Ok -> "Select"
            is RemoteCommand.Back -> "Back"
            is RemoteCommand.Home -> "Home"
            is RemoteCommand.Menu -> "Info"
            is RemoteCommand.Input -> "InputTuner"
            is RemoteCommand.PlayPause -> "Play"
            is RemoteCommand.Next -> "Fwd"
            is RemoteCommand.Previous -> "Rev"
            is RemoteCommand.FastForward -> "Fwd"
            is RemoteCommand.Rewind -> "Rev"
            is RemoteCommand.Numpad -> "Lit_${cmd.digit}"
            is RemoteCommand.Backspace -> "Backspace"
            is RemoteCommand.ChannelUp -> "ChannelUp"
            is RemoteCommand.ChannelDown -> "ChannelDown"
            is RemoteCommand.SearchContent -> null // Handled separately
            is RemoteCommand.OpenSearchUi -> "Search"
            is RemoteCommand.Text -> null // Handled separately
            is RemoteCommand.PointerMove -> null // Handled separately
            is RemoteCommand.PointerClick -> "Select"
        }
    }

    companion object {
        private const val TAG = "RokuController"
        private fun Result.Companion.defaultSuccess() = success(emptyList<TvApp>())
    }
}

