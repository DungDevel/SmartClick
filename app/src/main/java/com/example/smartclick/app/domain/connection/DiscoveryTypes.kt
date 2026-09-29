package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.domain.model.TvProtocol

/**
 * FW-1: Một ứng viên thô do `IDiscoveryProbe` tìm thấy, **trước** khi nhận dạng (fingerprint).
 *
 * Chỉ chứa dữ liệu quan sát được trên mạng; chưa biết chắc giao thức hay thế hệ.
 * `DeviceFingerprintEngine` (FW-4) sẽ gọi `fingerprint()` của từng driver trên ứng viên này.
 */
data class RawCandidate(
    /** IP LAN của thiết bị (đã qua kiểm tra LAN ở probe). */
    val ipAddress: String,
    /** Giao thức mà probe *phỏng đoán* (theo ST/serviceType/cổng); chỉ là gợi ý. */
    val protocolHint: TvProtocol = TvProtocol.UNKNOWN,
    /** URL mô tả thiết bị (SSDP LOCATION) nếu có. */
    val location: String? = null,
    /** Chuỗi SERVER của SSDP nếu có. */
    val server: String? = null,
    /** USN/uniqueServiceName nếu có. */
    val usn: String? = null,
    /** Tên dịch vụ/host mDNS nếu có. */
    val serviceName: String? = null,
    /** Các cổng đã thấy mở khi dò cổng (PortProbe). */
    val openPorts: Set<Int> = emptySet(),
    /** Bản ghi TXT của mDNS hoặc header phụ khác, để driver tự đọc. */
    val attributes: Map<String, String> = emptyMap()
)

/**
 * FW-1: Kết quả nhận dạng của một driver trên một `RawCandidate`.
 *
 * `null` từ `IProtocolDriver.fingerprint()` nghĩa là "không phải giao thức của tôi".
 */
data class Fingerprint(
    /** Giao thức đã xác định (kèm thế hệ đúng). */
    val protocol: TvProtocol,
    /** Id ổn định của thiết bị (serial/UDN...), dùng để dedupe và cache. */
    val stableId: String,
    /** Tên hiển thị lấy từ mô tả thiết bị. */
    val displayName: String,
    /** Mức tin cậy của kết quả nhận dạng. */
    val confidence: Confidence = Confidence.MEDIUM,
    /** MAC nếu đọc được (có thể rỗng). */
    val macAddress: String = ""
) {
    enum class Confidence { LOW, MEDIUM, HIGH }
}
