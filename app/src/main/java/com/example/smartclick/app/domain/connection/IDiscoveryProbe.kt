package com.example.smartclick.app.domain.connection

import android.net.Network
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.flow.Flow

/**
 * FW-1/FW-3: Một *cách* tìm thiết bị trên LAN (SSDP, mDNS, dò cổng).
 *
 * Probe chỉ phát ra `RawCandidate` thô — không nhận dạng, không tạo controller.
 * Một luồng khám phá duy nhất do `DiscoveryOrchestrator` điều phối (nguyên tắc 9, mục 4.10).
 */
interface IDiscoveryProbe {
    /** Giao thức mà probe này nhắm tới; dùng để lọc theo `probePlan` của BrandProfile. */
    val protocol: TvProtocol

    /**
     * Phát các ứng viên tìm được trên [network]. Flow kết thúc khi hết thời gian quét
     * của probe hoặc scope bị huỷ; probe tự dọn socket/lock trong `awaitClose`.
     */
    fun discover(network: Network): Flow<RawCandidate>
}
