package com.example.smartclick.app.data.local

import com.example.smartclick.app.data.local.db.TvDeviceDao
import com.example.smartclick.app.data.local.db.toDomainModel
import com.example.smartclick.app.data.local.db.toEntity
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTvDeviceRepository @Inject constructor(
    private val dao: TvDeviceDao
) : ITvDeviceRepository {

    override fun getPairedDevices(): Flow<List<TvDevice>> {
        return dao.observePairedDevices().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun saveDevice(device: TvDevice) {
        dao.insertOrReplace(device.toEntity())
    }

    override suspend fun removeDevice(deviceId: String) {
        dao.deleteById(deviceId)
    }

    override suspend fun updateDevice(device: TvDevice) {
        dao.update(device.toEntity())
    }

    override suspend fun getDeviceById(deviceId: String): TvDevice? {
        return dao.getDeviceById(deviceId)?.toDomainModel()
    }
}
