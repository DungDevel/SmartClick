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
    fun `parse short keyword negative tests`() {
        assertTrue(parser.parse("momo") is VoiceIntent.Unrecognized)
        assertTrue(parser.parse("battery") is VoiceIntent.Unrecognized)
        assertTrue(parser.parse("time") is VoiceIntent.Unrecognized)
        assertTrue(parser.parse("to lenh") is VoiceIntent.Unrecognized)
    }

    @Test
    fun `parse negative clause does not block TurnOff`() {
        val result = parser.parse("tat tivi di toi khong xem nua")
        assertTrue(result is VoiceIntent.TurnOff)
    }

    @Test
    fun `parse negation prefix khong tat tivi`() {
        val result = parser.parse("khong tat tivi")
        assertTrue(result is VoiceIntent.Unrecognized)
    }

    @Test
    fun `parse negation prefix dung tat tivi`() {
        val result = parser.parse("dung tat tivi")
        assertTrue(result is VoiceIntent.Unrecognized)
    }

    @Test
    fun `parse negation suffix tat tivi dung`() {
        val result = parser.parse("tat tivi dung")
        assertTrue(result is VoiceIntent.Unrecognized)
    }

    @Test
    fun `parse english negation dont turn off the tv`() {
        val result = parser.parse("don't turn off the tv")
        assertTrue(result is VoiceIntent.Unrecognized)
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
    fun `parse multiple apps in one sentence`() {
        val result = parser.parse("mo youtube de xem netflix")
        assertTrue(result is VoiceIntent.OpenApp)
        assertEquals(AppSymbol.YOUTUBE, (result as VoiceIntent.OpenApp).app)
    }

    @Test
    fun `parse power and app combined returns unrecognized`() {
        val result = parser.parse("mo youtube roi tat tivi")
        assertTrue(result is VoiceIntent.Unrecognized)
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
