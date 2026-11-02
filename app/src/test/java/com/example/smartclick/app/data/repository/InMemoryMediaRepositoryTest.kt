package com.example.smartclick.app.data.repository

import android.net.Uri
import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.app.domain.model.CastMediaMimeTypes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryMediaRepositoryTest {
    @Test
    fun `selected URI is retained with MIME name and size`() = runTest {
        val uri = contentUri()
        val repository = InMemoryMediaRepository(FakeReader(MediaContentMetadata("image/webp", "photo.webp", 123L)))

        val result = repository.prepareMedia(uri)

        assertTrue(result.isSuccess)
        val item = result.getOrThrow()
        assertEquals(uri.toString(), item.url)
        assertEquals("image/webp", item.mimeType)
        assertEquals("photo.webp", item.title)
        assertEquals(123L, item.sizeBytes)
        assertTrue(repository.getLocalMedia().first().isEmpty())
        repository.selectMedia(item)
        assertEquals(listOf(item), repository.getLocalMedia().first())
    }

    @Test
    fun `audio MIME is rejected as unsupported format`() = runTest {
        val repository = InMemoryMediaRepository(FakeReader(MediaContentMetadata("audio/mpeg", "song.mp3", 10L)))

        val result = repository.prepareMedia(contentUri())

        assertTrue(result.exceptionOrNull() is CastError.UnsupportedMediaFormat)
        assertTrue(repository.getLocalMedia().first().isEmpty())
    }

    @Test
    fun `null MIME is rejected as unsupported format`() = runTest {
        val repository = InMemoryMediaRepository(FakeReader(MediaContentMetadata(null, "unknown", null)))

        val result = repository.prepareMedia(contentUri())

        assertTrue(result.exceptionOrNull() is CastError.UnsupportedMediaFormat)
    }

    @Test
    fun `URI read failure is classified instead of thrown`() = runTest {
        val repository = InMemoryMediaRepository(FakeReader(failure = SecurityException("picker grant expired")))

        val result = repository.prepareMedia(contentUri())

        assertTrue(result.exceptionOrNull() is CastError.MediaSourceUnavailable)
    }

    @Test
    fun `MIME allowlist is the shared LocalMediaServer contract`() {
        assertEquals(
            setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp", "video/mp4", "video/webm", "video/x-matroska", "video/quicktime"),
            CastMediaMimeTypes.ALLOWED
        )
    }

    private fun contentUri(): Uri = mockk<Uri>().also { uri ->
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://picker/item-1"
        every { uri.lastPathSegment } returns "item-1"
    }

    private class FakeReader(
        private val metadata: MediaContentMetadata = MediaContentMetadata("image/jpeg", "image.jpg", null),
        private val failure: Throwable? = null
    ) : MediaContentReader {
        override suspend fun inspect(uri: Uri): MediaContentMetadata {
            failure?.let { throw it }
            return metadata
        }

        override suspend fun verifyReadable(uri: Uri) {
            failure?.let { throw it }
        }
    }
}
