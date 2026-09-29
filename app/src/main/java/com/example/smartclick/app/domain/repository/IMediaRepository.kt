package com.example.smartclick.app.domain.repository

import com.example.smartclick.app.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface IMediaRepository {
    /** Get local media for casting */
    fun getLocalMedia(): Flow<List<MediaItem>>
}
