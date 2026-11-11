package com.example.smartclick.app.tvconnection.lg.cast

import org.json.JSONObject
import kotlinx.coroutines.delay

class FakeLgWebOsCastTransport : LgWebOsCastTransport {
    var shouldConnectFail = false
    var shouldSendFail = false
    val recordedRequests = mutableListOf<JSONObject>()
    val registeredKeys = mutableListOf<String?>()
    var overrideResponse: JSONObject? = null
    var overrideErrorPayload: Throwable? = null

    override var isConnected: Boolean = false
        private set

    override suspend fun connect(ipAddress: String, clientKey: String?): Result<Unit> {
        if (shouldConnectFail) return Result.failure(Exception("Fake network failure"))
        isConnected = true
        registeredKeys.add(clientKey)
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        isConnected = false
    }

    override suspend fun sendRequest(uri: String, payload: JSONObject?): Result<JSONObject> {
        if (shouldSendFail) return Result.failure(overrideErrorPayload ?: Exception("Fake send failure"))
        if (!isConnected) return Result.failure(Exception("Not connected"))

        val reqObj = JSONObject().apply {
            put("uri", uri)
            if (payload != null) put("payload", payload)
        }
        recordedRequests.add(reqObj)

        delay(10) // simulate async

        if (overrideResponse != null) {
            return Result.success(overrideResponse!!)
        }

        // Default mock responses based on standard SSAP semantics
        val response = when (uri) {
            "ssap://media.viewer/open" -> JSONObject().apply {
                put("sessionId", "test-session-123")
                put("appId", "com.webos.app.photovideo")
            }
            "ssap://media.controls/play",
            "ssap://media.controls/pause",
            "ssap://media.controls/stop",
            "ssap://media.viewer/close" -> JSONObject().apply {
                put("returnValue", true)
            }
            else -> JSONObject()
        }
        return Result.success(response)
    }

    fun clear() {
        recordedRequests.clear()
        registeredKeys.clear()
        overrideResponse = null
        overrideErrorPayload = null
        shouldConnectFail = false
        shouldSendFail = false
        isConnected = false
    }
}
