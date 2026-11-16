package com.example.smartclick.app.domain.model

enum class PlaylistStatus {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
    ERROR,
    FINISHED
}

data class CastPlaylist(
    val items: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val status: PlaylistStatus = PlaylistStatus.IDLE,
    val imageDurationSeconds: Int = 5,
    val loop: Boolean = false,
    val errorCount: Int = 0,
    val errorMessage: String? = null
) {
    val currentItem: MediaItem?
        get() = items.getOrNull(currentIndex)

    val hasNext: Boolean
        get() = currentIndex < items.size - 1 || loop

    val hasPrevious: Boolean
        get() = currentIndex > 0
}
