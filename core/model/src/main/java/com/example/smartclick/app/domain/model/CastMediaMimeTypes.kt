package com.example.smartclick.app.domain.model

/** The media types supported by both the picker flow and LocalMediaServer. */
object CastMediaMimeTypes {
    val ALLOWED: Set<String> = setOf(
        "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp",
        "video/mp4", "video/webm", "video/x-matroska", "video/quicktime"
    )
}
