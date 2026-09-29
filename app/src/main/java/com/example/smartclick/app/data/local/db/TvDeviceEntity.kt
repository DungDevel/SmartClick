package com.example.smartclick.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol

@Entity(tableName = "tv_devices")
data class TvDeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val brand: String?,
    val isPaired: Boolean,
    val lastConnectedAt: Long,
    // FW-1: lưu protocol theo thế hệ để chọn đúng driver khi nối lại; credentialsRef trỏ tới TvCredentialStore.
    val protocol: String = TvProtocol.UNKNOWN.name,
    val credentialsRef: String? = null
)

// Mapping extensions
fun TvDeviceEntity.toDomainModel(): TvDevice {
    return TvDevice(
        id = id,
        name = name,
        ipAddress = ipAddress,
        macAddress = macAddress,
        brand = brand,
        isPaired = isPaired,
        lastConnectedAt = lastConnectedAt,
        protocol = runCatching { TvProtocol.valueOf(protocol) }.getOrDefault(TvProtocol.UNKNOWN),
        credentialsRef = credentialsRef
    )
}

fun TvDevice.toEntity(): TvDeviceEntity {
    return TvDeviceEntity(
        id = id,
        name = name,
        ipAddress = ipAddress,
        macAddress = macAddress,
        brand = brand,
        isPaired = isPaired,
        lastConnectedAt = lastConnectedAt,
        protocol = protocol.name,
        credentialsRef = credentialsRef
    )
}
