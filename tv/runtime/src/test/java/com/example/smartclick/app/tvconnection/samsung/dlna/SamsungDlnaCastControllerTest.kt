package com.example.smartclick.app.tvconnection.samsung.dlna

import android.content.Context
import android.net.Uri
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.model.CastMediaType
import com.example.smartclick.app.tvconnection.cast.CastMediaSession
import com.example.smartclick.app.tvconnection.cast.LocalMediaServer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SamsungDlnaCastControllerTest {

    private lateinit var controller: SamsungDlnaCastController
    private lateinit var transport: FakeSamsungDlnaTransport
    private lateinit var localMediaServer: LocalMediaServer
    private lateinit var context: Context

    private val deviceXml = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>Samsung TV</friendlyName>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/smp_10_</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        context = mockk(relaxed = true)
        transport = FakeSamsungDlnaTransport(deviceXml)
        localMediaServer = mockk(relaxed = true)

        controller = SamsungDlnaCastController(context, transport, localMediaServer, "192.168.1.50")
        controller.ssdpLocationProvider = { "http://192.168.1.50:7110/" }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `castMedia resolves control URL, registers media, sends SetAVTransportURI and Play`() = runBlocking {
        // Arrange
        val testUri = mockk<Uri>(relaxed = true)
        val mockMediaSession = CastMediaSession("sess-123", "token-abc", "http://10.0.0.5:8080/media/1", testUri, "video/mp4", "Test Video")

        coEvery { localMediaServer.registerMedia(any(), any(), any()) } returns mockMediaSession

        // Act
        val result = controller.castMedia("content://video/1", "video/mp4", CastMediaType.VIDEO, "Test Video")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals("http://192.168.1.50:7110/", transport.fetchedDeviceDescriptionUrl)

        assertEquals(2, transport.soapActionsCalled.size)
        val (action1, xml1) = transport.soapActionsCalled[0]
        assertEquals("SetAVTransportURI", action1)
        assertTrue(xml1.contains("<CurrentURI>http://10.0.0.5:8080/media/1</CurrentURI>"))
        assertTrue(xml1.contains("Test Video"))
        assertTrue(xml1.contains("video/mp4"))
        assertTrue(xml1.contains("object.item.videoItem"))

        val (action2, xml2) = transport.soapActionsCalled[1]
        assertEquals("Play", action2)

        val state = controller.castPlaybackState.first()
        assertEquals(CastPlaybackState.BUFFERING, state) // Changed from PLAYING because it shouldn't jump to PLAYING

        val info = controller.castSessionInfo.first()
        assertEquals("http://10.0.0.5:8080/media/1", info.currentUrl)
    }

    @Test
    fun `remote HTTP media is sent directly without local registration`() = runBlocking {
        val result = controller.castMedia("https://cdn.example/video.mp4", "video/mp4", CastMediaType.VIDEO, "Remote")
        assertTrue(result.isSuccess)
        assertTrue(transport.soapActionsCalled.first().second.contains("https://cdn.example/video.mp4"))
        io.mockk.coVerify(exactly = 0) { localMediaServer.registerMedia(any(), any(), any()) }
    }

    @Test
    fun `controlCast Pause does not falsely jump state to PAUSED`() = runBlocking {
        // Assume already casting
        val testUri = mockk<Uri>(relaxed = true)
        val mockMediaSession = CastMediaSession("sess-1", "token-a", "http://l/media/1", testUri, "audio/mp3", "Tune")
        coEvery { localMediaServer.registerMedia(any(), any(), any()) } returns mockMediaSession
        controller.castMedia("content://a/1", "audio/mp3", CastMediaType.AUDIO, "Tune")

        transport.soapActionsCalled.clear()

        // Act
        val result = controller.controlCast(CastCommand.Pause)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(1, transport.soapActionsCalled.size)
        assertEquals("Pause", transport.soapActionsCalled[0].first)
        assertEquals(CastPlaybackState.BUFFERING, controller.castPlaybackState.first())
    }

    @Test
    fun `controlCast SeekTo sends REL_TIME formatted command`() = runBlocking {
        // Assume already casting
        val testUri = mockk<Uri>(relaxed = true)
        val mockMediaSession = CastMediaSession("sess-1", "token-a", "http://l/media/1", testUri, "video/mp4", "Tune")
        coEvery { localMediaServer.registerMedia(any(), any(), any()) } returns mockMediaSession
        controller.castMedia("content://a/1", "video/mp4", CastMediaType.VIDEO, "Tune")

        transport.soapActionsCalled.clear()

        // Act
        // Seek to 1 hour, 5 minutes, 3 seconds = 3600000 + 300000 + 3000 = 3903000 ms
        val result = controller.controlCast(CastCommand.SeekTo(3903000L))

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(1, transport.soapActionsCalled.size)
        val (action, xml) = transport.soapActionsCalled[0]
        assertEquals("Seek", action)
        assertTrue(xml.contains("<Unit>REL_TIME</Unit>"))
        assertTrue(xml.contains("<Target>01:05:03</Target>"))
    }

    @Test
    fun `teardown closes connection, resets state, stops media and unregisters server`() = runBlocking {
        val testUri = mockk<Uri>(relaxed = true)
        val mockMediaSession = CastMediaSession("sess-1", "token-a", "http://l/media/1", testUri, "video/mp4", "Tune")
        coEvery { localMediaServer.registerMedia(any(), any(), any()) } returns mockMediaSession
        controller.castMedia("content://a/1", "video/mp4", CastMediaType.VIDEO, "Tune")

        transport.soapActionsCalled.clear()

        val result = controller.teardown()

        assertTrue(result.isSuccess)
        assertEquals(CastPlaybackState.IDLE, controller.castPlaybackState.first())
        io.mockk.coVerify { localMediaServer.unregisterMedia("sess-1") }

        // Must have fired stop
        assertEquals(1, transport.soapActionsCalled.size)
        assertEquals("Stop", transport.soapActionsCalled[0].first)
    }
}
