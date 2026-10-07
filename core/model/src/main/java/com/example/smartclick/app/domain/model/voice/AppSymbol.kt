package com.example.smartclick.app.domain.model.voice

/**
 * Standardized symbols representing known applications.
 * This decouples the Voice logic from protocol-specific App IDs.
 */
enum class AppSymbol {
    YOUTUBE,
    NETFLIX,
    PRIME_VIDEO,
    HULU,
    DISNEY_PLUS,
    SPOTIFY,
    APPLE_TV,
    HBO_MAX,
    BROWSER,
    UNKNOWN
}
