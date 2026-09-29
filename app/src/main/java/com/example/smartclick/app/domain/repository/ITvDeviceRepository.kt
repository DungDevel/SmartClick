package com.example.smartclick.app.domain.repository

import com.example.smartclick.app.domain.model.TvDevice
import kotlinx.coroutines.flow.Flow

interface ITvDeviceRepository {
    fun getPairedDevices(): Flow<List<TvDevice>>
    suspend fun saveDevice(device: TvDevice)
    suspend fun removeDevice(deviceId: String)
    suspend fun updateDevice(device: TvDevice)
    suspend fun getDeviceById(deviceId: String): TvDevice?
}
