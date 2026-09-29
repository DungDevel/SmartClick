package com.example.smartclick.app.tvconnection.discovery

import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.model.TvProtocol
import javax.inject.Inject

/**
 * FW-4: Quản lý danh sách các [IProtocolDriver] thông qua Hilt multibinding.
 * Các module driver sẽ cung cấp dependency dưới dạng `@IntoSet`.
 */
class DriverRegistry @Inject constructor(
    private val drivers: Set<@JvmSuppressWildcards IProtocolDriver>
) {
    /**
     * Lấy danh sách tất cả các driver hiện có trong app.
     */
    fun getAllDrivers(): Set<IProtocolDriver> = drivers

    /**
     * Lấy driver cho một giao thức cụ thể.
     */
    fun getDriver(protocol: TvProtocol): IProtocolDriver? {
        return drivers.find { it.protocol == protocol }
    }
}
