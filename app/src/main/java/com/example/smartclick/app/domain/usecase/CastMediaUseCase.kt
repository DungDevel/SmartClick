package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.model.CastMediaType
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.DriverRegistry
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import com.example.smartclick.app.domain.connection.TvConnectionState
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class CastMediaUseCase @Inject constructor(
    private val activeTvSession: IActiveTvSession,
    private val driverRegistry: DriverRegistry,
    private val tvCredentialStore: ITvCredentialStore
) {
    suspend operator fun invoke(media: MediaItem): Result<Unit> {
        val controller = activeTvSession.currentController.value
            ?: return Result.failure(Exception("No TV connected"))

        val connectionState = controller.connectionState.value
        if (connectionState !is TvConnectionState.Connected) {
            return Result.failure(Exception("TV is not connected"))
        }

        val device = controller.device
            ?: return Result.failure(Exception("No active device"))

        val driver = driverRegistry.getDriver(device.protocol)
            ?: return Result.failure(Exception("No driver found for protocol: ${device.protocol}"))

        val credentials = device.credentialsRef?.let { ref ->
            tvCredentialStore.getCredentials(device.id, ref)
        }

        val castController = try {
            driver.createCastController(device, credentials)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        } ?: return Result.failure(Exception("Casting not supported by driver ${device.protocol}"))

        val mediaType = when {
            media.mimeType.startsWith("video", ignoreCase = true) -> CastMediaType.VIDEO
            media.mimeType.startsWith("image", ignoreCase = true) -> CastMediaType.PHOTO
            else -> CastMediaType.AUDIO
        }

        return castController.castMedia(
            url = media.url,
            mimeType = media.mimeType,
            type = mediaType,
            title = media.title
        )
    }
}
