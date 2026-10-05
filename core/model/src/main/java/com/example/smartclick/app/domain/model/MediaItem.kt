package com.example.smartclick.app.domain.model

data class MediaItem(
    val id: String,
    val title: String,
    val url: String,
    val mimeType: String, // e.g., "video/mp4", "image/jpeg"
    val thumbnailUrl: String? = null
)
