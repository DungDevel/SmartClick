package com.example.smartclick.app.data.repository

import android.net.Uri
import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.app.domain.model.CastMediaMimeTypes
import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.repository.IMediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryMediaRepository @Inject constructor(
    private val contentReader: MediaContentReader
) : IMediaRepository {
    private val selectedMedia = MutableStateFlow<List<MediaItem>>(emptyList())

    override fun getLocalMedia(): Flow<List<MediaItem>> = selectedMedia.asStateFlow()

    override suspend fun prepareMedia(uri: Uri): Result<MediaItem> {
        if (!uri.scheme.equals("content", ignoreCase = true)) {
            return Result.failure(CastError.UnsupportedMediaSource(uri.scheme ?: "unknown"))
        }
        return try {
            val metadata = contentReader.inspect(uri)
            val mimeType = metadata.mimeType?.trim()?.lowercase()
            if (mimeType == null || mimeType !in CastMediaMimeTypes.ALLOWED) {
                return Result.failure(CastError.UnsupportedMediaFormat(mimeType))
            }
            val item = MediaItem(
                id = UUID.randomUUID().toString(),
                title = metadata.displayName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty(),
                url = uri.toString(),
                mimeType = mimeType,
                sizeBytes = metadata.sizeBytes
            )
            Result.success(item)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(CastError.MediaSourceUnavailable(error))
        }
    }

    override suspend fun selectMedia(media: MediaItem) {
        selectedMedia.value = listOf(media)
    }

    override suspend fun clearSelectedMedia() {
        selectedMedia.value = emptyList()
    }

    override suspend fun verifyReadable(media: MediaItem): Result<Unit> = try {
        contentReader.verifyReadable(Uri.parse(media.url))
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(CastError.MediaSourceUnavailable(error))
    }
}
