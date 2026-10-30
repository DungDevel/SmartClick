package com.example.smartclick.app.tvconnection.cast

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.net.HttpURLConnection
import java.net.Socket
import java.io.Closeable

@OptIn(ExperimentalCoroutinesApi::class)
class LocalMediaServerTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var activeNetwork: Network
    private lateinit var networkCapabilities: NetworkCapabilities
    private lateinit var linkProperties: LinkProperties

    private lateinit var server: LocalMediaServer
    private val testDispatcher = StandardTestDispatcher()

    private fun contentUri(id: String = "123"): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://media/external/video/$id"
        return uri
    }

    private suspend fun registered(mime: String = "video/mp4", id: String = "123") =
        server.registerMedia(contentUri(id), mime)

    private fun statusForRawPath(path: String): Int = Socket("127.0.0.1", server.port).use { socket ->
        socket.soTimeout = 2_000
        socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray())
        val statusLine = socket.getInputStream().bufferedReader().readLine()
        statusLine.substringAfter(' ').substringBefore(' ').toInt()
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(firstArg())
        }

        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        activeNetwork = mockk(relaxed = true)
        networkCapabilities = mockk(relaxed = true)
        linkProperties = mockk(relaxed = true)

        every { context.contentResolver } returns contentResolver
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns activeNetwork
        every { connectivityManager.getNetworkCapabilities(activeNetwork) } returns networkCapabilities
        every { connectivityManager.getLinkProperties(activeNetwork) } returns linkProperties

        val mockInetAddress = mockk<Inet4Address>(relaxed = true) {
            every { hostAddress } returns "127.0.0.1"
            every { isLoopbackAddress } returns false
            every { isLinkLocalAddress } returns false
            every { isSiteLocalAddress } returns true
        }
        val mockLinkAddress = mockk<LinkAddress>(relaxed = true) {
            every { address } returns mockInetAddress
        }

        every { linkProperties.linkAddresses } returns listOf(mockLinkAddress)

        
        server = LocalMediaServer(context)
        // Reset companion object state or just ensure clean start
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `test registerMedia starts server and generates token`() = runTest {
        val uri = contentUri()
        val session = server.registerMedia(uri, "video/mp4", "Test Video")
        
        assertTrue(server.isRunning)
        assertNotNull(server.baseUrl)
        assertNotNull(session.sessionId)
        assertTrue(session.token.isNotEmpty())
        assertEquals(uri, session.uri)
        assertTrue(session.mediaUrl.contains("127.0.0.1"))
        assertFalse(session.mediaUrl.contains(session.sessionId))
        assertTrue(session.mediaUrl.contains(session.token))
        assertEquals(43, session.token.length)
        assertTrue(session.token.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertEquals("/${session.token}", URL(session.mediaUrl).path)
        assertEquals(null, URL(session.mediaUrl).query)
        server.stop()
    }

    @Test
    fun `each registration gets a fresh token and invalidates previous selection`() = runTest {
        val first = registered(id = "first")
        val second = registered(id = "second")
        assertNotEquals(first.token, second.token)
        assertEquals(404, (URL(first.mediaUrl).openConnection() as HttpURLConnection).responseCode)
        server.stop()
    }

    @Test
    fun `stop invalidates prior token and wrong or missing token is indistinguishable`() = runTest {
        val media = registered()
        val missing = URL("${server.baseUrl}/").openConnection() as HttpURLConnection
        assertEquals(404, missing.responseCode)
        val wrong = URL("${server.baseUrl}/not-a-real-token").openConnection() as HttpURLConnection
        assertEquals(404, wrong.responseCode)
        server.stop()
        server.start().getOrThrow()
        assertEquals(404, (URL("${server.baseUrl}/${media.token}").openConnection() as HttpURLConnection).responseCode)
        server.stop()
    }

    @Test
    fun `only a single token path is served and traversal variants return 404`() = runTest {
        val media = registered()
        val paths = listOf("/media/${media.sessionId}?token=${media.token}", "/${media.token}/extra", "/../${media.token}", "/%2e%2e/${media.token}", "//${media.token}", "/${media.token}%00", "/${media.token}\\extra")
        paths.forEach { path ->
            assertEquals("path=$path", 404, statusForRawPath(path))
        }
        server.stop()
    }

    @Test
    fun `stop closes an active media stream`() = runTest {
        registered()
        var closed = false
        val stream = Closeable { closed = true }
        val field = LocalMediaServer::class.java.getDeclaredField("activeStreams").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(server) as MutableSet<Closeable>).add(stream)
        server.stop()
        assertTrue(closed)
    }

    @Test
    fun `only private LAN peers are allowed`() {
        assertTrue(server.isAllowedPeerAddress("192.168.1.20"))
        assertTrue(server.isAllowedPeerAddress("10.0.0.8"))
        assertFalse(server.isAllowedPeerAddress("8.8.8.8"))
        assertFalse(server.isAllowedPeerAddress(null))
    }

    @Test
    fun `content resolver URIs are required and repeated start reuses one listener`() = runTest {
        val fileUri = mockk<Uri>(relaxed = true)
        every { fileUri.scheme } returns "file"
        try {
            server.registerMedia(fileUri, "video/mp4")
            fail("file URI must not expose arbitrary filesystem paths")
        } catch (_: IllegalArgumentException) { }

        val firstPort = server.start().getOrThrow()
        val secondPort = server.start().getOrThrow()
        assertEquals(firstPort, secondPort)
        assertTrue(server.isRunning)
        server.stop()
    }

    @Test
    fun `range supports suffix and open ended requests and rejects malformed and multiple ranges`() = runTest {
        val media = registered()
        val data = "HelloWorld".toByteArray()
        every { contentResolver.openInputStream(any()) } answers { ByteArrayInputStream(data) }
        val afd = mockk<AssetFileDescriptor>(relaxed = true)
        every { afd.length } returns data.size.toLong()
        every { contentResolver.openAssetFileDescriptor(any(), "r") } returns afd

        fun request(range: String): HttpURLConnection = (URL(media.mediaUrl).openConnection() as HttpURLConnection).also {
            it.setRequestProperty("Range", range)
        }
        val suffix = request("bytes=-5")
        assertEquals(206, suffix.responseCode)
        assertEquals("bytes 5-9/10", suffix.getHeaderField("Content-Range"))
        assertArrayEquals("World".toByteArray(), suffix.inputStream.readBytes())
        val open = request("bytes=5-")
        assertEquals(206, open.responseCode)
        assertEquals(5, open.inputStream.readBytes().size)
        listOf("bytes=abc", "bytes=0-1,4-5", "bytes=8-12", "bytes=-0").forEach { range ->
            val invalid = request(range)
            assertEquals("range=$range", 416, invalid.responseCode)
            assertEquals("bytes */10", invalid.getHeaderField("Content-Range"))
        }
        server.stop()
    }

    @Test
    fun `HEAD returns headers without body and all responses have safe headers`() = runTest {
        val media = registered()
        val data = "video".toByteArray()
        every { contentResolver.openInputStream(any()) } answers { ByteArrayInputStream(data) }
        val afd = mockk<AssetFileDescriptor>(relaxed = true)
        every { afd.length } returns data.size.toLong()
        every { contentResolver.openAssetFileDescriptor(any(), "r") } returns afd
        val conn = (URL(media.mediaUrl).openConnection() as HttpURLConnection).apply { requestMethod = "HEAD" }
        assertEquals(200, conn.responseCode)
        assertEquals(data.size.toLong(), conn.contentLengthLong)
        assertTrue(conn.inputStream.readBytes().isEmpty())
        assertEquals("nosniff", conn.getHeaderField("X-Content-Type-Options"))
        assertEquals("no-store", conn.getHeaderField("Cache-Control"))
        server.stop()
    }

    @Test
    fun `only whitelisted image and video MIME types can be registered`() = runTest {
        listOf("image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm", "video/x-matroska").forEach { mime ->
            assertEquals(mime, server.registerMedia(contentUri(mime.hashCode().toString()), mime).mimeType)
        }
        listOf("audio/mpeg", "text/html", "application/octet-stream", "image/svg+xml", "video/unknown").forEach { mime ->
            try {
                server.registerMedia(contentUri("bad-${mime.hashCode()}"), mime)
                fail("accepted MIME $mime")
            } catch (_: IllegalArgumentException) { }
        }
        server.stop()
    }

    @Test
    fun `registration fails when no LAN IPv4 is available`() = runTest {
        every { linkProperties.linkAddresses } returns emptyList()
        assertTrue(server.start().isFailure)
        try {
            server.registerMedia(contentUri(), "video/mp4")
            fail("registration must not return a dead URL")
        } catch (_: IllegalStateException) { }
        assertFalse(server.isRunning)
    }

    @Test
    fun `test HTTP full request 200 OK`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://media/external/video/123"
        val session = server.registerMedia(uri, "video/mp4")

        val data = "HelloWorld".toByteArray()
        val inputStream = ByteArrayInputStream(data)
        every { contentResolver.openInputStream(uri) } returns inputStream
        
        val afd = mockk<AssetFileDescriptor>(relaxed = true)
        every { afd.length } returns data.size.toLong()
        every { contentResolver.openAssetFileDescriptor(uri, "r") } returns afd

        val url = URL(session.mediaUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        
        assertEquals(200, conn.responseCode)
        assertEquals("video/mp4", conn.contentType)
        assertEquals("bytes", conn.getHeaderField("Accept-Ranges"))
        assertEquals(data.size.toLong(), conn.contentLengthLong)
        
        val respBody = conn.inputStream.readBytes()
        assertArrayEquals(data, respBody)
        server.stop()
    }

    @Test
    fun `test HTTP partial request 206 Partial Content`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://media/external/video/123"
        val session = server.registerMedia(uri, "video/mp4")

        val data = "HelloWorld".toByteArray()
        val inputStream = ByteArrayInputStream(data) // When skip() is called on ByteArrayInputStream it works correctly
        every { contentResolver.openInputStream(uri) } returns inputStream
        
        val afd = mockk<AssetFileDescriptor>(relaxed = true)
        every { afd.length } returns data.size.toLong()
        every { contentResolver.openAssetFileDescriptor(uri, "r") } returns afd

        val url = URL(session.mediaUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        // Request bytes 5-9 ("World")
        conn.setRequestProperty("Range", "bytes=5-9")
        
        assertEquals(206, conn.responseCode)
        assertEquals("bytes 5-9/10", conn.getHeaderField("Content-Range"))
        
        val respBody = conn.inputStream.readBytes()
        assertArrayEquals("World".toByteArray(), respBody)
        server.stop()
    }

    @Test
    fun `test HTTP range out of bounds 416`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://media/external/video/123"
        val session = server.registerMedia(uri, "video/mp4")

        val data = "HelloWorld".toByteArray()
        val inputStream = ByteArrayInputStream(data)
        every { contentResolver.openInputStream(uri) } returns inputStream
        
        val afd = mockk<AssetFileDescriptor>(relaxed = true)
        every { afd.length } returns data.size.toLong()
        every { contentResolver.openAssetFileDescriptor(uri, "r") } returns afd

        val url = URL(session.mediaUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Range", "bytes=20-30")
        
        assertEquals(416, conn.responseCode)
        assertEquals("bytes */10", conn.getHeaderField("Content-Range"))
        server.stop()
    }

    @Test
    fun `test HTTP missing token is hidden as 404`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://media/external/video/123"
        val session = server.registerMedia(uri, "video/mp4")
        
        val badUrl = "${server.baseUrl}/media/${session.sessionId}"
        val url = URL(badUrl)
        val conn = url.openConnection() as HttpURLConnection
        assertEquals(404, conn.responseCode)
        server.stop()
    }

    @Test
    fun `test HTTP invalid media session 404 Not Found`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://media/external/video/123"
        server.registerMedia(uri, "video/mp4")
        
        val badUrl = "${server.baseUrl}/media/thisiswrong?token=doesnotmatter"
        val url = URL(badUrl)
        val conn = url.openConnection() as HttpURLConnection
        assertEquals(404, conn.responseCode)
        server.stop()
    }

    @Test
    fun `test registerMedia and unregister triggers delay shutdown`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://media/external/video/123"
        val session = server.registerMedia(uri, "video/mp4")
        assertTrue(server.isRunning)

        server.unregisterMedia(session.sessionId)
        // Coroutine should be scheduled for shutdown
        assertTrue(server.isRunning) // Still running right after unregister

        advanceTimeBy(LocalMediaServer.SHUTDOWN_DELAY_MS / 2)
        assertTrue(server.isRunning) // Still running halfway

        advanceTimeBy(LocalMediaServer.SHUTDOWN_DELAY_MS)
        assertFalse(server.isRunning) // Should be stopped now
    }

    @Test
    fun `server stops after inactivity even if a media session was not explicitly removed`() = runTest {
        registered()
        advanceTimeBy(LocalMediaServer.SHUTDOWN_DELAY_MS - 1)
        assertTrue(server.isRunning)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(server.isRunning)
        assertNull(server.baseUrl)
    }
}
