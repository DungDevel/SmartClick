package com.example.smartclick.app.tvconnection.wifi

import android.util.Log
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvApp
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
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

class RokuController @Inject constructor(
    client: OkHttpClient
) : ITvController {

    private val tvClient = client.newBuilder()
        .connectTimeout(1000, TimeUnit.MILLISECONDS)
        .readTimeout(1000, TimeUnit.MILLISECONDS)
        .callTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    private var currentDevice: TvDevice? = null
    override val device: TvDevice? get() = currentDevice

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.KEYBOARD_TEXT,
        TvCapability.INSTALLED_APPS
    )

    private val emptyBody = "".toRequestBody("text/plain".toMediaType())

    private val commandMutex = Mutex()
    private var cumulativeDx = 0f
    private var cumulativeDy = 0f
    private val swipeThreshold = 50f

    override suspend fun connect(device: TvDevice): Result<Unit> {
        if (!isPrivateLanAddress(device.ipAddress)) {
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

    private fun isPrivateLanAddress(ip: String): Boolean {
        return try {
            val addr = InetAddress.getByName(ip)
            addr.isSiteLocalAddress || addr.isLinkLocalAddress || ip.startsWith("192.168.") || ip.startsWith("10.") // fallback manual check
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse IP address: $ip", e)
            false
        }
    }

    private fun mapCommandToRokuKey(cmd: RemoteCommand): String? {
        return when (cmd) {
            is RemoteCommand.Power -> "Power"
            is RemoteCommand.VolumeUp -> "VolumeUp"
            is RemoteCommand.VolumeDown -> "VolumeDown"
            is RemoteCommand.Mute -> "VolumeMute"
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

