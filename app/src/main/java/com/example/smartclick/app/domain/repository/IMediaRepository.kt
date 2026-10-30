package com.example.smartclick.app.domain.repository

import com.example.smartclick.app.domain.model.MediaItem
import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface IMediaRepository {
    /** Observe the visual media item currently selected in the Photo Picker. */
    fun getLocalMedia(): Flow<List<MediaItem>>

    /** Validate and retain one Photo Picker URI in memory. */
    suspend fun selectMedia(uri: Uri): Result<MediaItem>

    /** Re-check the temporary picker grant immediately before casting. */
    suspend fun verifyReadable(media: MediaItem): Result<Unit>
}
