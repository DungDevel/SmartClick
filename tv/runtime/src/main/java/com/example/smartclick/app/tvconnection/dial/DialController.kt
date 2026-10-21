package com.example.smartclick.app.tvconnection.dial

import android.util.Log
import com.example.smartclick.app.domain.connection.ConnectProgress
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvApp
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.tvconnection.utils.NetworkSecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject

class DialController @Inject constructor(
    private val httpClient: OkHttpClient
) : ITvController {

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    override var device: TvDevice? = null
        private set

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.INSTALLED_APPS
    )

    private var applicationUrl: String? = null

    override suspend fun connect(device: TvDevice): Result<Unit> = withContext(Dispatchers.IO) {
        if (!NetworkSecurityUtils.isPrivateLanAddress(device.ipAddress)) {
            _connectionState.value = TvConnectionState.Failed("Invalid IP address")
            return@withContext Result.failure(SecurityException("IP address is not in private LAN range"))
        }

        this@DialController.device = device
        _connectionState.value = TvConnectionState.Connecting(ConnectProgress.CONNECTING_SOCKET)

        try {
            applicationUrl = "http://${device.ipAddress}:56700/apps/"
            _connectionState.value = TvConnectionState.Connected(device)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _connectionState.value = TvConnectionState.Failed(e.message ?: "Connection failed")
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        _connectionState.value = TvConnectionState.Idle
        device = null
        applicationUrl = null
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Remote commands not supported via DIAL protocol"))
    }

    override suspend fun getApps(): Result<List<TvApp>> = withContext(Dispatchers.IO) {
        Result.success(
            listOf(
                TvApp("YouTube", "YouTube"),
                TvApp("Netflix", "Netflix"),
                TvApp("DisneyPlus", "Disney+")
            )
        )
    }

    override suspend fun launchApp(appId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentAppUrl = applicationUrl ?: run {
            val dev = device ?: return@withContext Result.failure(IllegalStateException("Not connected"))
            "http://${dev.ipAddress}:56700/apps/"
        }

        try {
            val encodedAppId = URLEncoder.encode(appId, "UTF-8")
            val targetUrl = "$currentAppUrl$encodedAppId"

            if (!isSafeUrl(targetUrl)) {
                return@withContext Result.failure(SecurityException("Target URL violates security policy"))
            }

            val request = Request.Builder()
                .url(targetUrl)
                .post("".toRequestBody(null))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code == 201 || response.code == 200) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to launch app, HTTP status: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app", e)
            Result.failure(e)
        }
    }

    private fun isSafeUrl(urlStr: String): Boolean {
        try {
            val uri = java.net.URI(urlStr)
            val host = uri.host ?: return false
            return NetworkSecurityUtils.isPrivateLanAddress(host) || host.equals("localhost", ignoreCase = true)
        } catch (e: Exception) {
            return false
        }
    }

    companion object {
        private const val TAG = "DialController"
    }
}
