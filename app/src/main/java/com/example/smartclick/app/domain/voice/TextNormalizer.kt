package com.example.smartclick.app.domain.voice

import javax.inject.Inject
import javax.inject.Singleton
import java.text.Normalizer

/**
 * Prepares raw text from speech-to-text engines by normalizing whitespace,
 * handling missing diacritics, and lowercasing for easier intent parsing.
 */
@Singleton
class TextNormalizer @Inject constructor() {

    fun normalize(rawText: String): String {
        if (rawText.isBlank()) return ""

        return rawText.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ") // Remove extra spaces
            .removeAccents() // Remove Vietnamese accents for easier matching
    }

    private fun String.removeAccents(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        val regex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        return regex.replace(normalized, "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
    }
}
