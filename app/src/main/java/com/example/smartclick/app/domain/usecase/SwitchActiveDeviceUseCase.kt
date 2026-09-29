package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.connection.ConnectOutcome
import javax.inject.Inject

class SwitchActiveDeviceUseCase @Inject constructor(
    private val connectToTvUseCase: ConnectToTvUseCase
) {
    suspend operator fun invoke(device: TvDevice): ConnectOutcome {
        return connectToTvUseCase(device)
    }
}
