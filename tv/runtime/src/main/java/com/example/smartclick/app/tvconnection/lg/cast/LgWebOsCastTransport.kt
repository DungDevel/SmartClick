package com.example.smartclick.app.tvconnection.lg.cast

import org.json.JSONObject

interface LgWebOsCastTransport {
    /**
     * Connects to the WebOS TV SSAP WebSocket for casting purposes.
     * Uses the given [clientKey] to authenticate bypassing the TV prompt.
     */
    suspend fun connect(ipAddress: String, clientKey: String?): Result<Unit>

    /**
     * Disconnects the WebSocket gracefully.
     */
    suspend fun disconnect()

    /**
     * Sends a JSON-RPC-like request and waits for its corresponding response.
     * e.g. uri="ssap://media.viewer/open", payload={target="..."}
     */
    suspend fun sendRequest(uri: String, payload: JSONObject? = null): Result<JSONObject>

    /**
     * True if currently connected and authorized.
     */
    val isConnected: Boolean
}
