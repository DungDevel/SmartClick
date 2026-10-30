package com.example.smartclick.app.domain.repository

import com.example.smartclick.app.domain.model.MediaItem
import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface IMediaRepository {
    /** Observe the visual media item currently selected in the Photo Picker. */
    fun getLocalMedia(): Flow<List<MediaItem>>

    /** Inspect and validate a picker URI without adding it to the selected-media state. */
    suspend fun prepareMedia(uri: Uri): Result<MediaItem>

    /** Retain an already validated item in memory after TV, capability, and Premium gates pass. */
    suspend fun selectMedia(media: MediaItem)

    /** Re-check the temporary picker grant immediately before casting. */
    suspend fun verifyReadable(media: MediaItem): Result<Unit>
}
