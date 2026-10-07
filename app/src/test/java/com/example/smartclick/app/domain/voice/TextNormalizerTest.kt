package com.example.smartclick.app.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TextNormalizerTest {

    private lateinit var classUnderTest: TextNormalizer

    @Before
    fun setup() {
        classUnderTest = TextNormalizer()
    }

    @Test
    fun `normalize should remove Vietnamese accents and lowercase`() {
        val raw = "Mở tivi và Tăng Âm Lượng lên 5"
        val expected = "mo tivi va tang am luong len 5"
        assertEquals(expected, classUnderTest.normalize(raw))
    }

    @Test
    fun `normalize should handle extra whitespaces`() {
        val raw = "   Mở    Netflix   "
        val expected = "mo netflix"
        assertEquals(expected, classUnderTest.normalize(raw))
    }

    @Test
    fun `normalize should handle empty strings`() {
        val raw = "   "
        val expected = ""
        assertEquals(expected, classUnderTest.normalize(raw))
    }

    @Test
    fun `normalize should handle D characters`() {
        val raw = "đậm đà ĐÁ"
        val expected = "dam da da"
        assertEquals(expected, classUnderTest.normalize(raw))
    }
}
