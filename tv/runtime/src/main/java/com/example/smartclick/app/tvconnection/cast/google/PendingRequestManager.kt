package com.example.smartclick.app.tvconnection.cast.google

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages pending request-response correlations for the Google Cast protocol.
 * Ensures that requests are registered before they are sent to avoid race conditions.
 */
class PendingRequestManager {
    private val statusRequests = ConcurrentHashMap<Int, CompletableDeferred<org.json.JSONObject>>()
    private val launchStatusRequests = ConcurrentHashMap<Int, CompletableDeferred<org.json.JSONObject>>()
    private val launchErrorRequests = ConcurrentHashMap<Int, CompletableDeferred<org.json.JSONObject>>()

    // Explicit concurrency for matching appId broadcasts during LAUNCH
    private val appLaunchConfirmations = ConcurrentHashMap<String, CompletableDeferred<org.json.JSONObject>>()

    fun registerStatusRequest(requestId: Int): CompletableDeferred<org.json.JSONObject> {
        val deferred = CompletableDeferred<org.json.JSONObject>()
        statusRequests[requestId] = deferred
        return deferred
    }

    fun registerLaunchStatusRequest(requestId: Int): CompletableDeferred<org.json.JSONObject> {
        val deferred = CompletableDeferred<org.json.JSONObject>()
        launchStatusRequests[requestId] = deferred
        return deferred
    }

    fun registerLaunchErrorRequest(requestId: Int): CompletableDeferred<org.json.JSONObject> {
        val deferred = CompletableDeferred<org.json.JSONObject>()
        launchErrorRequests[requestId] = deferred
        return deferred
    }

    fun registerAppLaunchConfirmation(appId: String): CompletableDeferred<org.json.JSONObject> {
        val deferred = CompletableDeferred<org.json.JSONObject>()
        appLaunchConfirmations[appId] = deferred
        return deferred
    }

    fun completeStatusRequest(requestId: Int, response: org.json.JSONObject) {
        if (requestId <= 0) return
        statusRequests.remove(requestId)?.complete(response)
    }

    fun completeLaunchStatusRequest(requestId: Int, response: org.json.JSONObject) {
        if (requestId <= 0) return
        launchStatusRequests.remove(requestId)?.complete(response)
    }

    fun completeLaunchErrorRequest(requestId: Int, response: org.json.JSONObject) {
        if (requestId <= 0) return
        launchErrorRequests.remove(requestId)?.complete(response)
    }

    fun completeAppLaunchConfirmation(appId: String, response: org.json.JSONObject) {
        if (appId.isBlank()) return
        // Complete the first occurrence
        appLaunchConfirmations.remove(appId)?.complete(response)
    }

    fun clearRequest(requestId: Int) {
        statusRequests.remove(requestId)?.cancel()
        launchStatusRequests.remove(requestId)?.cancel()
        launchErrorRequests.remove(requestId)?.cancel()
    }

    fun clearAppLaunchConfirmation(appId: String) {
        appLaunchConfirmations.remove(appId)?.cancel()
    }

    fun clearAll() {
        statusRequests.values.forEach { it.cancel() }
        statusRequests.clear()
        launchStatusRequests.values.forEach { it.cancel() }
        launchStatusRequests.clear()
        launchErrorRequests.values.forEach { it.cancel() }
        launchErrorRequests.clear()
        appLaunchConfirmations.values.forEach { it.cancel() }
        appLaunchConfirmations.clear()
    }
}