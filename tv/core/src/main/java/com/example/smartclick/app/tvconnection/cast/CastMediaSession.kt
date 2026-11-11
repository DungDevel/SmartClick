package com.example.smartclick.app.tvconnection.cast

import android.net.Uri

data class CastMediaSession(
    val sessionId: String,
    val token: String,
    val mediaUrl: String,
    val uri: Uri,
    val mimeType: String,
    val title: String?
)
