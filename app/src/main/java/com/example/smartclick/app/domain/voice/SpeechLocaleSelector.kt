package com.example.smartclick.app.domain.voice

internal object SpeechLocaleSelector {
    fun forLanguageTag(languageTag: String): String = when (languageTag.substringBefore('-').lowercase()) {
        "vi" -> "vi-VN"
        "es" -> "es-ES"
        "pt" -> "pt-BR"
        "de" -> "de-DE"
        "en" -> "en-US"
        else -> "en-US"
    }
}
