package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.cast.CastSessionInfo
import com.example.smartclick.app.domain.model.CastMediaType
import kotlinx.coroutines.flow.StateFlow

interface ICastController {
    val castPlaybackState: StateFlow<CastPlaybackState>
    val castSessionInfo: StateFlow<CastSessionInfo>

    suspend fun castMedia(url: String, mimeType: String, type: CastMediaType, title: String): Result<Unit>
    suspend fun controlCast(command: CastCommand): Result<Unit>
    suspend fun teardown(): Result<Unit>
}
