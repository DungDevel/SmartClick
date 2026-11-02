package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import com.example.smartclick.app.domain.connection.ReconnectWatcher
import com.example.smartclick.app.domain.connection.TvConnectionState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RemoveDeviceUseCase @Inject constructor(
    private val tvDeviceRepository: ITvDeviceRepository,
    private val activeTvSession: IActiveTvSession,
    private val tvCredentialStore: ITvCredentialStore,
    private val reconnectWatcher: ReconnectWatcher
) {
    suspend operator fun invoke(deviceId: String) {
        val currentController = activeTvSession.currentController.value
        val isActiveDevice = currentController?.device?.id == deviceId
        val isReconnectTarget = reconnectWatcher.isReconnectingForDevice(deviceId)

        if (isActiveDevice || isReconnectTarget) {
            // Invalidate the generation before disconnecting or clearing the controller.
            reconnectWatcher.cancelReconnect()
        }

        if (isActiveDevice) {
            if (currentController?.connectionState?.value is TvConnectionState.Connected) {
                currentController.disconnect()
            }
            activeTvSession.setActiveController(null)
        }

        val device = tvDeviceRepository.getDeviceById(deviceId)
        val ref = device?.credentialsRef
        if (ref != null) {
            tvCredentialStore.clearCredentials(deviceId, ref)
        }

        tvDeviceRepository.removeDevice(deviceId)
    }
}


