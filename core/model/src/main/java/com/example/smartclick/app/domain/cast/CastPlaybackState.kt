package com.example.smartclick.app.domain.cast

enum class CastPlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    STOPPED,
    COMPLETED,
    ERROR
}

data class CastSessionInfo(
    val currentUrl: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isMuted: Boolean = false,
    val volume: Int = 100
)
