package com.example.smartclick.app.tvconnection.samsung.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpSoapModelsTest {

    @Test
    fun `test createSetAVTransportURIRequest wraps url and didl in soap envelope`() {
        val request = UpnpSoapModels.createSetAVTransportURIRequest("http://192.168.1.10:8080/media/1", "<DIDL-Lite/>")
        assertTrue(request.contains("SetAVTransportURI"))
        assertTrue(request.contains("http://192.168.1.10:8080/media/1"))
        assertTrue(request.contains("&lt;DIDL-Lite/&gt;"))
    }

    @Test
    fun `test createPlayRequest generates correct action`() {
        val request = UpnpSoapModels.createPlayRequest()
        assertTrue(request.contains("Play"))
        assertTrue(request.contains("<Speed>1</Speed>"))
    }

    @Test
    fun `test createPauseRequest generates correct action`() {
        val request = UpnpSoapModels.createPauseRequest()
        assertTrue(request.contains("Pause"))
    }

    @Test
    fun `test createStopRequest generates correct action`() {
        val request = UpnpSoapModels.createStopRequest()
        assertTrue(request.contains("Stop"))
    }

    @Test
    fun `test createSeekRequest formats time target`() {
        val request = UpnpSoapModels.createSeekRequest("01:00:00")
        assertTrue(request.contains("Seek"))
        assertTrue(request.contains("<Unit>REL_TIME</Unit>"))
        assertTrue(request.contains("<Target>01:00:00</Target>"))
    }

    @Test
    fun `test createDidlLite escapes special xml characters in title and mediaUrl`() {
        val didl = UpnpSoapModels.createDidlLite("Movie & Chill <HD>", "http://10.0.0.1/vid?a=1&b=2", "video/mp4", "object.item.videoItem")
        assertTrue(didl.contains("Movie &amp; Chill &lt;HD&gt;"))
        assertTrue(didl.contains("http://10.0.0.1/vid?a=1&amp;b=2"))
        assertTrue(didl.contains("video/mp4"))
        assertTrue(didl.contains("object.item.videoItem"))
    }
}
