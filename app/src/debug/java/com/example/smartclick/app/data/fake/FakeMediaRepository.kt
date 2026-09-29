package com.example.smartclick.app.data.fake

import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.repository.IMediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeMediaRepository @Inject constructor() : IMediaRepository {
    override fun getLocalMedia(): Flow<List<MediaItem>> {
        return flowOf(
            listOf(
                MediaItem("1", "Photo 1", "local://1", "image/jpeg"),
                MediaItem("2", "Video 1", "local://2", "video/mp4"),
                MediaItem("3", "Photo 2", "local://3", "image/png"),
                MediaItem("4", "Photo 3", "local://4", "image/jpeg"),
                MediaItem("5", "Video 2", "local://5", "video/mp4"),
                MediaItem("6", "Photo 4", "local://6", "image/jpeg"),
                MediaItem("7", "Photo 5", "local://7", "image/png"),
                MediaItem("8", "Video 3", "local://8", "video/mp4"),
                MediaItem("9", "Photo 6", "local://9", "image/jpeg"),
                MediaItem("10", "Photo 7", "local://10", "image/jpeg"),
                MediaItem("11", "Video 4", "local://11", "video/mp4"),
                MediaItem("12", "Photo 8", "local://12", "image/png")
            )
        )
    }
}
