package com.example.smartclick.app.domain.connection

/**
 * FW-1: Máy trạng thái ghép nối dùng chung cho mọi driver (mục 4.6).
 *
 * Mỗi driver phát ra một chuỗi `PairingStep` qua `IProtocolDriver.pair()`; UI ghép nối (FW-5)
 * chỉ cần hiểu các bước này, không cần biết giao thức bên dưới.
 */
sealed class PairingStep {
    /** Roku: nối được luôn, không cần ghép nối. */
    object NotRequired : PairingStep()

    /** Samsung, LG (một số đời): người dùng phải chọn "Cho phép" trên TV. */
    data class ConfirmOnTv(val timeoutMs: Long = 30_000L) : PairingStep()

    /** Android/Google TV, Vizio, VIDAA, Fire TV (REST): nhập mã hiện trên TV. */
    data class EnterPin(val length: Int = 6) : PairingStep()

    /**
     * TV tắt điều khiển qua mạng, hoặc Fire TV dùng ADB: hướng dẫn từng bước.
     * [key] là khoá chuỗi để UI tra `strings.xml`, không phải văn bản hiển thị.
     */
    data class Instruction(val key: String) : PairingStep()

    /** Xong: lưu token/chứng chỉ bằng TvCredentialStore (FW-6) rồi vào Remote. */
    data class Succeeded(val credentials: Credentials?) : PairingStep()

    /** Lỗi/timeout/từ chối: UI hiện nguyên nhân + nút thử lại. */
    data class Failed(val reason: PairingFailure) : PairingStep()
}

/**
 * Nguyên nhân ghép nối thất bại, phân loại để UI chọn đúng thông điệp và hành động
 * (thay vì so sánh chuỗi như trước FIX-01).
 */
sealed class PairingFailure {
    /** Người dùng bấm từ chối trên TV. */
    object RejectedOnTv : PairingFailure()

    /** Hết thời gian chờ người dùng xác nhận hoặc chờ TV trả lời. */
    object TimedOut : PairingFailure()

    /** Mã PIN sai; [remainingAttempts] = null khi driver không cho biết. */
    data class WrongPin(val remainingAttempts: Int? = null) : PairingFailure()

    /** TV yêu cầu bật điều khiển qua mạng trong cài đặt. */
    data class NetworkControlDisabled(val instructionKey: String) : PairingFailure()

    /** Lỗi mạng/IO. */
    data class Network(val message: String) : PairingFailure()

    /** Người dùng huỷ. */
    object Cancelled : PairingFailure()

    /** Lỗi khác, kèm thông điệp của driver. */
    data class Unknown(val message: String) : PairingFailure()
}

/**
 * Thông tin xác thực do driver sinh ra sau khi ghép nối (token, chứng chỉ client, khoá...).
 *
 * Giá trị bí mật **không** được lưu trong `TvDevice` hay Room; `TvCredentialStore` (FW-6)
 * cất vào Android Keystore và chỉ trả về `credentialsRef`.
 */
data class Credentials(
    val protocolValues: Map<String, String> = emptyMap()
)
