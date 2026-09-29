package com.example.smartclick.app.domain.model

data class TvDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val brand: String? = null,
    val isPaired: Boolean = false,
    val lastConnectedAt: Long = 0L,
    // FW-1: protocol (theo thế hệ) là khoá nhận dạng để chọn driver đúng đời.
    val protocol: TvProtocol = TvProtocol.UNKNOWN,
    // FW-1: năng lực driver dùng để ẩn/mờ nút trên UI (Remote/Channels/Cast).
    val capabilities: Set<TvCapability> = emptySet(),
    // FW-1: tham chiếu tới token/chứng chỉ trong TvCredentialStore (FW-6). Không lưu bí mật ở đây.
    val credentialsRef: String? = null
)
