package com.example.smartclick.app.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLocaleSelectorTest {
    @Test
    fun `maps supported app languages to recognition locales and safely falls back`() {
        assertEquals("en-US", SpeechLocaleSelector.forLanguageTag("en"))
        assertEquals("vi-VN", SpeechLocaleSelector.forLanguageTag("vi"))
        assertEquals("es-ES", SpeechLocaleSelector.forLanguageTag("es-MX"))
        assertEquals("pt-BR", SpeechLocaleSelector.forLanguageTag("pt-BR"))
        assertEquals("de-DE", SpeechLocaleSelector.forLanguageTag("de-AT"))
        assertEquals("en-US", SpeechLocaleSelector.forLanguageTag("xx-ZZ"))
    }
}
