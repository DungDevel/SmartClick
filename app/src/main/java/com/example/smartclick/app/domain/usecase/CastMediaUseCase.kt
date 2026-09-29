package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import javax.inject.Inject

class CastMediaUseCase @Inject constructor(
    private val activeTvSession: IActiveTvSession
) {
    suspend operator fun invoke(media: MediaItem): Result<Unit> {
        val controller = activeTvSession.currentController.value ?: return Result.failure(Exception("No TV connected"))

        return if (controller.connectionState.value is TvConnectionState.Connected) {
            // TODO(P6): Bổ sung logic hàm cast() trong ITvController (hoặc giả lập cast qua dlna)
            // Hiện tại pass qua, return success
            Result.success(Unit)
        } else {
            Result.failure(Exception("TV is not connected"))
        }
    }
}


