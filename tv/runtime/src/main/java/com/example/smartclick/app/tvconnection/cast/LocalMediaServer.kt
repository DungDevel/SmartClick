package com.example.smartclick.app.tvconnection.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.smartclick.app.tvconnection.utils.NetworkSecurityUtils
import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaServer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var server: InternalServer? = null
    private val sessionsById = ConcurrentHashMap<String, CastMediaSession>()
    private val sessionsByToken = ConcurrentHashMap<String, CastMediaSession>()
    private val activeStreams = ConcurrentHashMap.newKeySet<Closeable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var shutdownJob: Job? = null
    private val idleGeneration = AtomicLong()
    private val lifecycleMutex = Mutex()
    private val secureRandom = SecureRandom()

    val isRunning: Boolean get() = server?.isAlive == true
    val port: Int get() = server?.listeningPort ?: 0
    var baseUrl: String? = null
        private set

    companion object {
        private const val TAG = "LocalMediaServer"
        const val SHUTDOWN_DELAY_MS = 10 * 60 * 1000L
        private const val TOKEN_BYTES = 32 // 256 bits of entropy
        private val ALLOWED_MIME_TYPES = setOf(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp",
            "video/mp4", "video/webm", "video/x-matroska", "video/quicktime"
        )
    }

    suspend fun start(): Result<Int> = lifecycleMutex.withLock { startLocked() }

    private fun startLocked(): Result<Int> {
        val ip = getActiveIpv4Address()
            ?: return Result.failure(NoLanAddressException("No active private IPv4 LAN address found"))
        val existing = server
        if (existing?.isAlive == true && baseUrl?.startsWith("http://$ip:") == true) {
            return Result.success(existing.listeningPort)
        }
        if (existing != null) {
            existing.stop()
            server = null
            baseUrl = null
        }

        return try {
            val newServer = InternalServer(ip, 0)
            newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = newServer
            baseUrl = "http://$ip:${newServer.listeningPort}"
            scheduleIdleShutdown()
            Log.d(TAG, "Started LocalMediaServer on $baseUrl")
            Result.success(newServer.listeningPort)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start LocalMediaServer", error)
            Result.failure(error)
        }
    }

    suspend fun stop() = lifecycleMutex.withLock { stopLocked() }

    private fun stopLocked() {
        idleGeneration.incrementAndGet()
        shutdownJob?.cancel()
        shutdownJob = null
        sessionsById.clear()
        sessionsByToken.clear()
        closeActiveStreams()
        server?.stop()
        server = null
        baseUrl = null
        Log.d(TAG, "Stopped LocalMediaServer")
    }

    suspend fun registerMedia(uri: Uri, mimeType: String, title: String? = null): CastMediaSession =
        lifecycleMutex.withLock {
            val normalizedMime = mimeType.trim().lowercase()
            require(normalizedMime in ALLOWED_MIME_TYPES) { "Unsupported cast MIME type" }
            require(uri.scheme.equals("content", ignoreCase = true)) { "Media must be registered as a content URI" }

            shutdownJob?.cancel()
            shutdownJob = null
            startLocked().getOrElse { throw NoLanAddressException("Unable to start local media server", it) }

            // A newly selected file replaces the previous share and invalidates its URL token.
            sessionsById.clear()
            sessionsByToken.clear()
            closeActiveStreams()

            val tokenBytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
            val token = Base64.encodeToString(tokenBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val sessionId = UUID.randomUUID().toString()
            val mediaUrl = "${baseUrl ?: throw NoLanAddressException("LAN address disappeared while registering media")}/$token"
            val castSession = CastMediaSession(sessionId, token, mediaUrl, uri, normalizedMime, title)
            sessionsById[sessionId] = castSession
            sessionsByToken[token] = castSession
            scheduleIdleShutdown()
            castSession
        }

    suspend fun unregisterMedia(sessionId: String) = lifecycleMutex.withLock {
        val removed = sessionsById.remove(sessionId)
        if (removed != null) {
            sessionsByToken.remove(removed.token)
            closeActiveStreams()
        }
        scheduleIdleShutdown()
    }

    @Synchronized
    private fun scheduleIdleShutdown() {
        val scheduledGeneration = idleGeneration.incrementAndGet()
        shutdownJob?.cancel()
        shutdownJob = scope.launch {
            delay(SHUTDOWN_DELAY_MS)
            lifecycleMutex.withLock {
                if (scheduledGeneration == idleGeneration.get()) stopLocked()
            }
        }
    }

    private fun getActiveIpv4Address(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val properties: LinkProperties = cm.getLinkProperties(network) ?: return null
        return properties.linkAddresses.asSequence()
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && NetworkSecurityUtils.isPrivateLanAddress(it.hostAddress) }
            ?.hostAddress
    }

    internal fun isAllowedPeerAddress(address: String?): Boolean =
        NetworkSecurityUtils.isPrivateLanAddress(address)

    private fun closeActiveStreams() {
        activeStreams.toList().forEach { stream ->
            runCatching { stream.close() }
        }
        activeStreams.clear()
    }

    private fun InputStream.skipFully(toSkip: Long): Boolean {
        var remaining = toSkip
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped == 0L) {
                if (read() == -1) return false
                remaining--
            } else remaining -= skipped
        }
        return true
    }

    private data class ByteRange(val start: Long, val end: Long, val total: Long) {
        val length: Long get() = end - start + 1
    }

    private fun parseRange(header: String, total: Long): ByteRange? {
        if (total < 0 || !header.startsWith("bytes=") || header.contains(',')) return null
        val match = Regex("^bytes=(\\d*)-(\\d*)$").matchEntire(header) ?: return null
        val left = match.groupValues[1]
        val right = match.groupValues[2]
        if (left.isEmpty() && right.isEmpty()) return null
        return try {
            if (left.isEmpty()) {
                val suffixLength = right.toLong()
                if (suffixLength <= 0 || total == 0L) return null
                ByteRange((total - suffixLength).coerceAtLeast(0), total - 1, total)
            } else {
                val start = left.toLong()
                val end = if (right.isEmpty()) total - 1 else right.toLong()
                if (start >= total || end < start || end >= total) return null
                ByteRange(start, end, total)
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private inner class InternalServer(hostname: String, port: Int) : NanoHTTPD(hostname, port) {
        override fun serve(session: IHTTPSession): Response {
            if (!isAllowedPeerAddress(session.remoteIpAddress)) return notFound()

            val path = session.uri
            if (!path.matches(Regex("^/[A-Za-z0-9_-]{43}$"))) return notFound()
            val token = path.substring(1)
            val media = sessionsByToken[token] ?: return notFound()
            scheduleIdleShutdown()

            return try {
                val total = getContentLength(media.uri)
                val rangeHeader = session.headers["range"]
                if (rangeHeader != null) {
                    val range = parseRange(rangeHeader, total)
                        ?: return rangeNotSatisfiable(total)
                    val stream = openTrackedStream(media.uri) ?: return notFound()
                    if (range.start > 0 && !stream.skipFully(range.start)) {
                        stream.close()
                        return rangeNotSatisfiable(total)
                    }
                    val response = newFixedLengthResponse(
                        Response.Status.PARTIAL_CONTENT, media.mimeType, stream, range.length
                    )
                    response.addHeader("Content-Range", "bytes ${range.start}-${range.end}/${range.total}")
                    response.addHeader("Accept-Ranges", "bytes")
                    addSafeHeaders(response)
                    response
                } else {
                    val stream = openTrackedStream(media.uri) ?: return notFound()
                    val response = if (total >= 0) {
                        newFixedLengthResponse(Response.Status.OK, media.mimeType, stream, total)
                    } else {
                        newChunkedResponse(Response.Status.OK, media.mimeType, stream)
                    }
                    response.addHeader("Accept-Ranges", "bytes")
                    addSafeHeaders(response)
                    response
                }
            } catch (error: Exception) {
                Log.e(TAG, "Error serving media", error)
                addSafeHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Media unavailable"))
            }
        }

        private fun getContentLength(uri: Uri): Long {
            runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }
                .getOrNull()?.takeIf { it >= 0 }?.let { return it }
            if (uri.scheme == "content") {
                runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (index >= 0) cursor.getLong(index).takeIf { it >= 0 } else null
                        } else null
                    }
                }.getOrNull()?.let { return it }
            }
            return -1L
        }

        private fun openTrackedStream(uri: Uri): InputStream? {
            val source = context.contentResolver.openInputStream(uri) ?: return null
            lateinit var tracked: InputStream
            tracked = object : FilterInputStream(source) {
                override fun close() {
                    try { super.close() } finally { activeStreams.remove(tracked) }
                }
            }
            activeStreams.add(tracked)
            return tracked
        }

        private fun rangeNotSatisfiable(total: Long): Response {
            val response = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
            response.addHeader("Content-Range", "bytes */${if (total >= 0) total else "*"}")
            return addSafeHeaders(response)
        }

        private fun notFound(): Response = addSafeHeaders(
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        )

        private fun addSafeHeaders(response: Response): Response {
            response.addHeader("X-Content-Type-Options", "nosniff")
            response.addHeader("Cache-Control", "no-store")
            return response
        }
    }
}

class NoLanAddressException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
