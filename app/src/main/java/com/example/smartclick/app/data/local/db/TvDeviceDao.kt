package com.example.smartclick.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TvDeviceDao {
    @Query("SELECT * FROM tv_devices WHERE isPaired = 1 ORDER BY lastConnectedAt DESC")
    fun observePairedDevices(): Flow<List<TvDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(device: TvDeviceEntity)

    @Update
    suspend fun update(device: TvDeviceEntity)

    @Query("DELETE FROM tv_devices WHERE id = :deviceId")
    suspend fun deleteById(deviceId: String)

    @Query("SELECT * FROM tv_devices WHERE id = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): TvDeviceEntity?
}
