package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.domain.model.TvDevice
import kotlinx.coroutines.flow.Flow

/**
 * Interface extracted for FIX-08 (layering), allowing domain use cases
 * to orchestrate discovery without depending on `tvconnection`.
 */
interface IDiscoveryOrchestrator {
    fun scanForDevices(brandId: String): Flow<List<TvDevice>>
    suspend fun identifyManualIp(ip: String): TvDevice?
}
