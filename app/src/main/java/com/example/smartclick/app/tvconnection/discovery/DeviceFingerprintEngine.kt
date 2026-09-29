package com.example.smartclick.app.tvconnection.discovery

import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * FW-4: Nhận dạng thiết bị từ [RawCandidate] song song nhiều driver.
 *
 * Thay thế cho DeviceIdentifier theo thiết kế v5.
 * Orchestrator sẽ đẩy RawCandidate vào đây để engine chạy song song TẤT CẢ các driver.
 * Xử lý nhóm "Chưa hỗ trợ" (TvProtocol.UNKNOWN) nếu không có driver nào khớp,
 * để FW-8 có dữ liệu xử lý sau.
 */
class DeviceFingerprintEngine @Inject constructor(
    private val driverRegistry: DriverRegistry
) {
    /**
     * Chạy toàn bộ các driver song song để fingerprint candidate.
     * Chọn kết quả có confidence cao nhất.
     * Nếu không có driver nào khớp, tạo TvDevice với protocol UNKNOWN (TV chưa hỗ trợ).
     */
    suspend fun identify(candidate: RawCandidate, brandFromUser: String? = null): TvDevice? = coroutineScope {
        val drivers = driverRegistry.getAllDrivers()

        if (drivers.isEmpty()) {
            return@coroutineScope generateUnknownDevice(candidate, brandFromUser)
        }

        val results = drivers.map { driver ->
            async {
                val fingerprint = try {
                    // Timeout (e.g. 2000ms) to ensure slow drivers don't block discovery
                    withTimeoutOrNull(2000L) {
                        driver.fingerprint(candidate)
                    }
                } catch (e: Exception) {
                    null
                }

                if (fingerprint != null) {
                    driver to fingerprint
                } else {
                    null
                }
            }
        }.awaitAll().filterNotNull()

        if (results.isEmpty()) {
            // Không có driver nào khớp -> TV chưa hỗ trợ nhưng đã phản hồi probe
            // Trả về thiết bị UNKNOWN để hiển thị tại FW-8.
            return@coroutineScope generateUnknownDevice(candidate, brandFromUser)
        }

        // Chọn best match: Ưu tiên confidence cao hơn.
        // Nếu bằng confidence, ưu tiên driver có protocol trùng với protocolHint
        val bestMatch = results.maxWithOrNull(Comparator { a, b ->
            val confCompare = a.second.confidence.compareTo(b.second.confidence)
            if (confCompare != 0) confCompare
            else {
                val aIsHint = if (a.first.protocol == candidate.protocolHint) 1 else 0
                val bIsHint = if (b.first.protocol == candidate.protocolHint) 1 else 0
                aIsHint.compareTo(bIsHint)
            }
        }) ?: return@coroutineScope null // should not happen

        val (driver, fingerprint) = bestMatch

        return@coroutineScope TvDevice(
            id = fingerprint.stableId,
            name = fingerprint.displayName,
            ipAddress = candidate.ipAddress,
            macAddress = fingerprint.macAddress,
            brand = brandFromUser,
            protocol = fingerprint.protocol,
            capabilities = driver.capabilities
        )
    }

    private fun generateUnknownDevice(candidate: RawCandidate, brandFromUser: String?): TvDevice {
        val deviceName = candidate.server ?: candidate.serviceName ?: "Unknown Device (${candidate.ipAddress})"
        return TvDevice(
            id = "unknown_${candidate.ipAddress}",
            name = deviceName,
            ipAddress = candidate.ipAddress,
            macAddress = "",
            brand = brandFromUser,
            protocol = TvProtocol.UNKNOWN,
            capabilities = emptySet()
        )
    }
}
