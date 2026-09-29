package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.flow.Flow

/**
 * FW-1/FW-4: Một *giao thức điều khiển* (lớp cách ly thư viện bên thứ ba — mục 4.10, nguyên tắc 3).
 *
 * Mỗi thế hệ giao thức có một driver riêng (Roku ECP, Android TV v2/v1, Tizen, webOS...).
 * Driver chịu trách nhiệm: nhận dạng phần của mình, ghép nối, và tạo `ITvController`.
 * Đăng ký vào app bằng Hilt multibinding `@IntoSet` (FW-9) — thêm hãng = thêm một driver.
 */
interface IProtocolDriver {
    /** Giao thức (kèm thế hệ) mà driver này xử lý. */
    val protocol: TvProtocol

    /** Năng lực tối đa của giao thức; controller có thể thu hẹp theo thiết bị thật. */
    val capabilities: Set<TvCapability>

    /**
     * Nhận dạng một ứng viên thô. Trả `null` nếu ứng viên **không** thuộc giao thức này.
     * Chỉ đọc thông tin của riêng giao thức mình (mục 4.10, nguyên tắc 4).
     */
    suspend fun fingerprint(candidate: RawCandidate): Fingerprint?

    /**
     * Ghép nối thiết bị, phát các bước `PairingStep`. Với giao thức không cần ghép nối
     * (Roku), phát `NotRequired` rồi `Succeeded(null)`.
     */
    /** Cung cấp mã PIN cho quá trình ghép nối (nếu đang ở bước EnterPin). */
    suspend fun providePin(pin: String) {}
    fun pair(device: TvDevice): Flow<PairingStep>

    /**
     * Tạo controller đã sẵn sàng gửi lệnh. [credentials] là token/chứng chỉ lấy từ
     * `TvCredentialStore` (FW-6) qua `TvDevice.credentialsRef`; `null` nếu giao thức không cần.
     */
    fun createController(device: TvDevice, credentials: Credentials?): ITvController
}
