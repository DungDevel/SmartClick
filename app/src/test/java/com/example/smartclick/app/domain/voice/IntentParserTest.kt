package com.example.smartclick.app.domain.voice

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.voice.AppSymbol
import com.example.smartclick.app.domain.model.voice.VoiceIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IntentParserTest {

    private lateinit var parser: IntentParser

    @Before
    fun setup() {
        parser = IntentParser(TextNormalizer())
    }

    @Test
    fun `parse TurnOn intent`() {
        val result = parser.parse("Bật tivi lên đi")
        assertTrue(result is VoiceIntent.TurnOn)
    }

    @Test
    fun `parse TurnOff intent`() {
        val result = parser.parse("tắt tv")
        assertTrue(result is VoiceIntent.TurnOff)
    }

    @Test
    fun `parse MuteToggle intent`() {
        val result = parser.parse("tắt tiếng tivi")
        assertTrue(result is VoiceIntent.MuteToggle)
    }

    @Test
    fun `parse VolumeUp intent with number`() {
        val result = parser.parse("tăng âm lượng lên 5")
        assertTrue(result is VoiceIntent.VolumeUp)
        assertEquals(5, (result as VoiceIntent.VolumeUp).steps)
    }

    @Test
    fun `parse VolumeDown intent without number`() {
        val result = parser.parse("nhỏ xuống")
        assertTrue(result is VoiceIntent.VolumeDown)
        assertEquals(1, (result as VoiceIntent.VolumeDown).steps)
    }

    @Test
    fun `parse OpenApp intent for YouTube`() {
        val result = parser.parse("Mở ứng dụng YouTube")
        assertTrue(result is VoiceIntent.OpenApp)
        assertEquals(AppSymbol.YOUTUBE, (result as VoiceIntent.OpenApp).app)
    }

    @Test
    fun `parse OpenApp intent for Netflix`() {
        val result = parser.parse("Bật netflix xem phim")
        assertTrue(result is VoiceIntent.OpenApp)
        assertEquals(AppSymbol.NETFLIX, (result as VoiceIntent.OpenApp).app)
    }

    @Test
    fun `parse Navigation intent DPadUp`() {
        val result = parser.parse("lên trên")
        assertTrue(result is VoiceIntent.Navigation)
        assertEquals(RemoteCommand.DPadUp, (result as VoiceIntent.Navigation).command)
    }

    @Test
    fun `parse Search intent`() {
        val raw = "tìm kiếm video ca nhạc"
        val result = parser.parse(raw)
        assertTrue(result is VoiceIntent.SearchContent)
        assertEquals("video ca nhac", (result as VoiceIntent.SearchContent).query)
        assertEquals(raw, result.rawInput)
    }

    @Test
    fun `parse Unrecognized intent`() {
        val result = parser.parse("hôm nay trời đẹp quá")
        assertTrue(result is VoiceIntent.Unrecognized)
    }
}
