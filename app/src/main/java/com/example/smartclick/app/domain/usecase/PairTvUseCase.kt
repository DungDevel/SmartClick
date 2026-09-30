package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.connection.DriverRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class PairTvUseCase @Inject constructor(
    private val driverRegistry: DriverRegistry
) {
    operator fun invoke(device: TvDevice): Flow<PairingStep> {
        val driver = driverRegistry.getDriver(device.protocol)
            ?: return flowOf(PairingStep.Failed(com.example.smartclick.app.domain.connection.PairingFailure.Unknown("No driver found for ${device.protocol}")))
        return driver.pair(device)
    }

    suspend fun providePin(protocol: TvProtocol, pin: String) {
        val driver = driverRegistry.getDriver(protocol)
        driver?.providePin(pin)
    }
}
