package com.example.smartclick.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

data class MediaContentMetadata(
    val mimeType: String?,
    val displayName: String?,
    val sizeBytes: Long?
)

interface MediaContentReader {
    suspend fun inspect(uri: Uri): MediaContentMetadata
    suspend fun verifyReadable(uri: Uri)
}

@Singleton
class AndroidMediaContentReader @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaContentReader {
    override suspend fun inspect(uri: Uri): MediaContentMetadata = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri)
        var displayName: String? = null
        var sizeBytes: Long? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex).takeIf { it >= 0 }?.let { sizeBytes = it }
                }
            }
        verifyReadable(uri)
        MediaContentMetadata(mimeType, displayName, sizeBytes)
    }

    override suspend fun verifyReadable(uri: Uri) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Unable to open selected media URI")
        stream.use { }
    }
}
