package com.example.smartclick.app.tvconnection.discovery

import android.content.Context
import android.net.Network
import android.net.wifi.WifiManager
import android.util.Log
import com.example.smartclick.app.domain.connection.IWifiNetworkProvider
import com.example.smartclick.app.domain.model.BrandProfile
import com.example.smartclick.app.domain.model.ProbePlan
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.repository.IBrandProfileRepository
import com.example.smartclick.app.di.IoDispatcher
import com.example.smartclick.app.domain.connection.IDiscoveryOrchestrator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FW-3: Orchestrator thế chỗ DeviceScanAggregator.
 *
 * - Quản lý MulticastLock tập trung cho toàn bộ phiên quét.
 * - Đọc BrandProfile để điều phối các Probe theo Tier.
 * - Hỗ trợ nhận dạng thủ công qua IP (Manual IP).
 * - Phát ra danh sách TvDevice tăng dần (progressive list).
 */
@Singleton
class DiscoveryOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val brandProfileRepo: IBrandProfileRepository,
    private val wifiNetworkProvider: IWifiNetworkProvider,
    private val probeFactory: ProbeFactory,
    private val fingerprintEngine: DeviceFingerprintEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IDiscoveryOrchestrator {

    /**
     * Nhận dạng một TV từ IP nhập thủ công.
     * Tạo một RawCandidate giả lập và chạy fingerprinting.
     */
    override suspend fun identifyManualIp(ip: String): TvDevice? {
        Log.d(TAG, "Manually identifying device at IP: $ip")
        val candidate = RawCandidate(
            ipAddress = ip,
            protocolHint = TvProtocol.UNKNOWN, // Không biết trước protocol khi nhập IP
            location = null,
            server = null,
            usn = null
        )
        return fingerprintEngine.identify(candidate, null)
    }

    override fun scanForDevices(brandId: String): Flow<List<TvDevice>> = callbackFlow {
        val network = wifiNetworkProvider.getActiveWifiNetwork()
        if (network == null) {
            Log.w(TAG, "No active Wi-Fi network for discovery")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // FW-3: Quản lý MulticastLock tập trung tại Orchestrator.
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("SmartClick_Discovery_Lock")

        val profile = brandProfileRepo.getProfile(brandId) ?: BrandProfile(
            id = "others",
            displayName = "Other",
            probePlan = ProbePlan.All
        )

        val foundDevices = ConcurrentHashMap<String, TvDevice>()
        val processedIps = ConcurrentHashMap<String, Boolean>()

        val discoverJob = launch(ioDispatcher) {
            try {
                multicastLock?.setReferenceCounted(true)
                multicastLock?.acquire()
                Log.d(TAG, "MulticastLock acquired for discovery session")

                val plan = profile.probePlan
                when (plan) {
                    is ProbePlan.Tiered -> {
                        var accDelay = 0L
                        for (tier in plan.tiers) {
                            // Chạy mỗi tier trong một scope riêng để quản lý timeout
                            launch {
                                if (accDelay > 0) {
                                    delay(accDelay)
                                }
                                Log.d(TAG, "Starting Tier ${tier.tier} for brand $brandId")
                                val probes = tier.protocols.flatMap { probeFactory.createProbesForProtocol(it) }
                                probes.forEach { probe ->
                                    launch(ioDispatcher) {
                                        probe.discover(network).collect { candidate ->
                                            if (processedIps.putIfAbsent(candidate.ipAddress, true) == null) {
                                                val device = fingerprintEngine.identify(candidate, profile.displayName)
                                                if (device != null) {
                                                    if (foundDevices.putIfAbsent(device.id, device) == null) {
                                                        trySend(foundDevices.values.toList())
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            accDelay += tier.timeoutMs
                        }
                    }
                    is ProbePlan.All -> {
                        Log.d(TAG, "Starting ProbePlan.All for brand $brandId")
                        val probes = probeFactory.createAllProbes()
                        probes.forEach { probe ->
                            launch(ioDispatcher) {
                                probe.discover(network).collect { candidate ->
                                    if (processedIps.putIfAbsent(candidate.ipAddress, true) == null) {
                                        val device = fingerprintEngine.identify(candidate, null)
                                        if (device != null) {
                                            if (foundDevices.putIfAbsent(device.id, device) == null) {
                                                trySend(foundDevices.values.toList())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during discovery session", e)
            }
        }

        awaitClose {
            discoverJob.cancel()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                    Log.d(TAG, "MulticastLock released")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MulticastLock", e)
            }
        }
    }

    companion object {
        private const val TAG = "DiscoveryOrchestrator"
    }
}
