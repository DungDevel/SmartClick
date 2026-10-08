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
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://media/external/video/123"
        val session = server.registerMedia(uri, "video/mp4", "Test Video")
        
        assertTrue(server.isRunning)
        assertNotNull(server.baseUrl)
        assertNotNull(session.sessionId)
        assertTrue(session.token.isNotEmpty())
        assertEquals(uri, session.uri)
        assertTrue(session.mediaUrl.contains("127.0.0.1"))
        assertTrue(session.mediaUrl.contains(session.sessionId))
        assertTrue(session.mediaUrl.contains(session.token))
        server.stop()
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
    fun `test HTTP no token 403 Forbidden`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://media/external/video/123"
        val session = server.registerMedia(uri, "video/mp4")
        
        val badUrl = "${server.baseUrl}/media/${session.sessionId}"
        val url = URL(badUrl)
        val conn = url.openConnection() as HttpURLConnection
        assertEquals(403, conn.responseCode)
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
}
