package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.tvconnection.discovery.DiscoveryOrchestrator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanTvDevicesUseCase @Inject constructor(
    private val discoveryOrchestrator: DiscoveryOrchestrator
) {
    operator fun invoke(brand: String = "Roku"): Flow<List<TvDevice>> {
        return discoveryOrchestrator.scanForDevices(brand)
    }

    suspend fun identifyManualIp(ip: String): TvDevice? {
        return discoveryOrchestrator.identifyManualIp(ip)
    }
}
