package com.example.smartclick.app.tvconnection.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.net.Inet4Address
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaServer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var server: InternalServer? = null
    private val sessions = ConcurrentHashMap<String, CastMediaSession>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var shutdownJob: Job? = null

    // Mutex for starting/stopping safely
    private val lifecycleMutex = Mutex()

    private val secureRandom = SecureRandom()

    val isRunning: Boolean
        get() = server?.isAlive == true

    val port: Int
        get() = server?.listeningPort ?: 0

    var baseUrl: String? = null
        private set

    companion object {
        private const val TAG = "LocalMediaServer"
        const val SHUTDOWN_DELAY_MS = 10 * 60 * 1000L // 10 minutes
    }

    suspend fun start(): Result<Int> = lifecycleMutex.withLock {
        if (server?.isAlive == true) {
            val ip = getActiveIpv4Address()
            if (ip != null) {
                baseUrl = "http://$ip:${server?.listeningPort}"
            }
            return Result.success(server!!.listeningPort)
        }

        try {
            val ip = getActiveIpv4Address() ?: return Result.failure(Exception("No active IPv4 LAN connection found"))

            // start at random available port (0)
            val newServer = InternalServer(0)
            newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = newServer

            baseUrl = "http://$ip:${newServer.listeningPort}"
            Log.d(TAG, "Started LocalMediaServer on $baseUrl")

            return Result.success(newServer.listeningPort)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LocalMediaServer", e)
            return Result.failure(e)
        }
    }

    suspend fun stop() = lifecycleMutex.withLock {
        shutdownJob?.cancel()
        shutdownJob = null
        server?.stop()
        server = null
        baseUrl = null
        Log.d(TAG, "Stopped LocalMediaServer")
    }

    suspend fun registerMedia(uri: Uri, mimeType: String, title: String? = null): CastMediaSession {
        // Cancel any pending shutdown
        shutdownJob?.cancel()
        shutdownJob = null

        if (!isRunning) {
            start() // try to start dynamically. Ignore failure, it might throw or not work
        }

        val sessionId = UUID.randomUUID().toString()
        val tokenBytes = ByteArray(32)
        secureRandom.nextBytes(tokenBytes)
        val token = Base64.encodeToString(tokenBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        val url = if (baseUrl != null) {
            "${baseUrl}/media/$sessionId?token=$token"
        } else {
            "" // fallback if start failed
        }

        val session = CastMediaSession(sessionId, token, url, uri, mimeType, title)
        sessions[sessionId] = session
        Log.d(TAG, "Registered new media session: $sessionId")
        return session
    }

    suspend fun unregisterMedia(sessionId: String) {
        if (sessions.remove(sessionId) != null) {
            Log.d(TAG, "Unregistered media session: $sessionId")
        }

        if (sessions.isEmpty()) {
            scheduleShutdown()
        }
    }

    private fun scheduleShutdown() {
        shutdownJob?.cancel()
        shutdownJob = scope.launch {
            Log.d(TAG, "No active sessions. Scheduling shutdown in ${SHUTDOWN_DELAY_MS}ms")
            delay(SHUTDOWN_DELAY_MS)
            if (sessions.isEmpty()) {
                stop()
            }
        }
    }

    private fun getActiveIpv4Address(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val activeNetwork = cm.activeNetwork ?: return null
        val linkProperties: LinkProperties = cm.getLinkProperties(activeNetwork) ?: return null

        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address
            if (address is Inet4Address && !address.isLoopbackAddress) {
                return address.hostAddress
            }
        }
        return null
    }

    private fun InputStream.skipFully(toSkip: Long): Boolean {
        var remaining = toSkip
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped == 0L) {
                if (read() == -1) {
                    return false
                }
                remaining--
            } else {
                remaining -= skipped
            }
        }
        return true
    }

    private inner class InternalServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uriStr = session.uri

            // Expected /media/{sessionId}
            if (!uriStr.startsWith("/media/")) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }

            val sessionId = uriStr.substringAfter("/media/")
            val token = session.parameters["token"]?.firstOrNull()

            if (token == null) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Missing token")
            }

            val castSession = sessions[sessionId]
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Media session deleted or invalid")

            if (castSession.token != token) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Invalid token")
            }

            return try {
                val contentResolver = context.contentResolver
                val contentUri = castSession.uri
                val mimeType = castSession.mimeType

                // Find content length safely without buffering
                var totalLength = -1L
                try {
                    contentResolver.openAssetFileDescriptor(contentUri, "r")?.use { afd ->
                        totalLength = afd.length
                    }
                } catch (e: Exception) {
                    // ignore
                }

                if (totalLength <= 0 && contentUri.scheme == "content") {
                    try {
                        contentResolver.query(contentUri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (sizeIndex != -1) {
                                    val size = cursor.getLong(sizeIndex)
                                    if (size > 0) totalLength = size
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                val rangeHeader = session.headers["range"]
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val rangeValue = rangeHeader.substring("bytes=".length)
                    val split = rangeValue.split("-")
                    val start = if (split[0].isNotEmpty()) split[0].toLong() else 0L

                    var end = -1L
                    if (split.size > 1 && split[1].isNotEmpty()) {
                        end = split[1].toLong()
                    } else if (totalLength > 0) {
                        end = totalLength - 1
                    }

                    if (totalLength > 0) {
                        if (start >= totalLength || (end != -1L && end >= totalLength)) {
                            val res = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                            res.addHeader("Content-Range", "bytes */$totalLength")
                            return res
                        }
                    }

                    val contentLength = if (end != -1L) (end - start + 1) else -1L

                    val inputStream = contentResolver.openInputStream(contentUri)
                    if (inputStream == null) {
                        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream not found")
                    }

                    if (start > 0 && !inputStream.skipFully(start)) {
                        val res = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                        val lenStr = if (totalLength > 0) totalLength.toString() else "*"
                        res.addHeader("Content-Range", "bytes */$lenStr")
                        return res
                    }

                    val res = if (contentLength > 0) {
                        newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, inputStream, contentLength)
                    } else {
                        newChunkedResponse(Response.Status.PARTIAL_CONTENT, mimeType, inputStream)
                    }

                    val endStr = if (end != -1L) end.toString() else ""
                    val lenStr = if (totalLength > 0) totalLength.toString() else "*"
                    res.addHeader("Content-Range", "bytes $start-$endStr/$lenStr")
                    res.addHeader("Accept-Ranges", "bytes")
                    res

                } else {
                    val inputStream = contentResolver.openInputStream(contentUri)
                    if (inputStream == null) {
                        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream not found")
                    }

                    val res = if (totalLength > 0) {
                        newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, totalLength)
                    } else {
                        newChunkedResponse(Response.Status.OK, mimeType, inputStream)
                    }
                    res.addHeader("Accept-Ranges", "bytes")
                    res
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error serving media", e)
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
            }
        }
    }
}
