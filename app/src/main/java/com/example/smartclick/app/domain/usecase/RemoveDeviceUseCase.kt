package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import com.example.smartclick.app.domain.connection.TvConnectionState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RemoveDeviceUseCase @Inject constructor(
    private val tvDeviceRepository: ITvDeviceRepository,
    private val activeTvSession: IActiveTvSession,
    private val tvCredentialStore: ITvCredentialStore
) {
    suspend operator fun invoke(deviceId: String) {
        // Disconnect if active
        val currentController = activeTvSession.currentController.value
        val state = currentController?.connectionState?.value

        if (state is TvConnectionState.Connected && state.device.id == deviceId) {
            currentController.disconnect()
            activeTvSession.setActiveController(null)
        }

        val device = tvDeviceRepository.getDeviceById(deviceId)
        if (device?.credentialsRef != null) {
            tvCredentialStore.clearCredentials(deviceId, device.credentialsRef)
        }

        tvDeviceRepository.removeDevice(deviceId)
    }
}


