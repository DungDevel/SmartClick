package com.example.smartclick.app.features.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import com.example.smartclick.app.domain.connection.ConnectOutcome
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import com.example.smartclick.app.domain.usecase.ConnectToTvUseCase
import com.example.smartclick.app.domain.usecase.PairTvUseCase
import com.example.smartclick.app.domain.connection.PairingStep
import kotlinx.coroutines.Job
import com.example.smartclick.app.domain.usecase.RemoveDeviceUseCase
import com.example.smartclick.app.domain.usecase.ScanTvDevicesUseCase
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.connection.NetworkState
import com.example.smartclick.app.domain.connection.IWifiNetworkProvider
import com.example.smartclick.app.domain.policy.PairingLimitPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceRowUi(
    val device: TvDevice,
    val isActive: Boolean,
    val isOnline: Boolean,
    val isPaired: Boolean,
    val isLocked: Boolean
)

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val progress: Int) : ScanState()
    object Found : ScanState()
    object NotFound : ScanState()
}

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val tvDeviceRepository: ITvDeviceRepository,
    private val scanTvDevicesUseCase: ScanTvDevicesUseCase,
    private val pairTvUseCase: PairTvUseCase,
    private val connectToTvUseCase: ConnectToTvUseCase,
    private val removeDeviceUseCase: RemoveDeviceUseCase,
    private val wifiNetworkProvider: IWifiNetworkProvider,
    private val activeTvSession: IActiveTvSession,
    private val subscriptionRepository: ISubscriptionRepository,
    private val tvCredentialStore: ITvCredentialStore,
    private val pairingLimitPolicy: PairingLimitPolicy
) : ViewModel() {

    private val _targetBrand = MutableStateFlow("Roku")

    val networkState: StateFlow<NetworkState> = wifiNetworkProvider.networkState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), wifiNetworkProvider.getCurrentState())

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _pairingStep = MutableStateFlow<PairingStep?>(null)
    val pairingStep: StateFlow<PairingStep?> = _pairingStep.asStateFlow()

    private var currentPairingDevice: TvDevice? = null
    private var pairingJob: Job? = null

    private val _unsupportedDeviceCount = MutableStateFlow(0)
    val unsupportedDeviceCount: StateFlow<Int> = _unsupportedDeviceCount.asStateFlow()

    private val _unknownDevices = MutableStateFlow<List<TvDevice>>(emptyList())

    private val _discoveredDevices = MutableStateFlow<List<TvDevice>>(emptyList())
    private var scanJob: Job? = null

    init {
        startScan()
    }

    private fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            if (wifiNetworkProvider.getCurrentState() !is NetworkState.WifiConnected) {
                _discoveredDevices.value = emptyList()
                return@launch
            }

            _scanState.value = ScanState.Scanning(0)
            var elapsedTicks = 0
            val totalTicks = 100 // 100 ticks × 100ms = 10 seconds max scan time
            var listFound = emptyList<TvDevice>()

            // Actual scan flow
            val scanFlow = scanTvDevicesUseCase(_targetBrand.value)

            // Run scan collecting with timeout loop
            kotlinx.coroutines.coroutineScope {
                val collectJob = launch {
                    scanFlow.collect { list ->
                        listFound = list
                        val unknownList = list.filter { it.protocol == com.example.smartclick.app.domain.model.TvProtocol.UNKNOWN }
                        _unsupportedDeviceCount.value = unknownList.size
                        _unknownDevices.value = unknownList
                    }
                }

                while (elapsedTicks < totalTicks) {
                    kotlinx.coroutines.delay(100)
                    elapsedTicks++
                    val currentProgress = (elapsedTicks * 100 / totalTicks).coerceAtMost(100)
                    _scanState.value = ScanState.Scanning(currentProgress)

                    if (listFound.isNotEmpty() && currentProgress > 30) {
                        // Found devices — let the animation run a bit then short circuit
                        break
                    }
                }

                collectJob.cancel()
            }

            _discoveredDevices.value = listFound

            if (listFound.isNotEmpty()) {
                _scanState.value = ScanState.Found
            } else {
                _scanState.value = ScanState.NotFound
            }
        }
    }

    private val discoveredDevices: StateFlow<List<TvDevice>> = _discoveredDevices.asStateFlow()

    val combinedDevices: StateFlow<List<DeviceRowUi>> = combine(
        tvDeviceRepository.getPairedDevices(),
        discoveredDevices,
        activeTvSession.connectionState,
        subscriptionRepository.currentPlan
    ) { paired, discovered, connectionState, plan ->
        val activeDeviceId = (connectionState as? TvConnectionState.Connected)?.device?.id
        val isPremium = plan == com.example.smartclick.app.domain.model.SubscriptionPlan.PREMIUM
        val primaryDevice = paired.firstOrNull()

        val allDevicesMap = mutableMapOf<String, TvDevice>()
        paired.forEach { allDevicesMap[it.id] = it }
        discovered.forEach {
            if (!allDevicesMap.containsKey(it.id)) {
                allDevicesMap[it.id] = it
            } else {
                // Update IP if discovered again for paired device
                allDevicesMap[it.id] = allDevicesMap[it.id]!!.copy(ipAddress = it.ipAddress)
            }
        }

        allDevicesMap.values.filter { it.protocol != com.example.smartclick.app.domain.model.TvProtocol.UNKNOWN }.map { tv ->
            val isPaired = paired.any { it.id == tv.id }
            val isOnline = discovered.any { it.id == tv.id } || tv.id == activeDeviceId
            val isActive = tv.id == activeDeviceId
            val isLocked = !isPremium && paired.isNotEmpty() && tv.id != primaryDevice?.id

            DeviceRowUi(tv, isActive, isOnline, isPaired, isLocked)
        }.sortedByDescending { it.isActive }.sortedByDescending { it.isPaired }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _connectionResult = MutableSharedFlow<ConnectOutcome>()
    val connectionResult: SharedFlow<ConnectOutcome> = _connectionResult

    fun setTargetBrand(brand: String) {
        _targetBrand.value = brand
        refreshScan()
    }

    fun refreshScan() {
        startScan()
    }

    fun connectToDevice(device: TvDevice) {
        if (!device.isPaired) {
            viewModelScope.launch {
                val plan = subscriptionRepository.currentPlan.first()
                val paired = tvDeviceRepository.getPairedDevices().first()
                val result = pairingLimitPolicy.evaluate(plan, paired)
                if (result is com.example.smartclick.app.domain.policy.PairingResult.LimitExceeded) {
                    _connectionResult.emit(ConnectOutcome.PaywallRequired)
                    return@launch
                }
                startPairing(device)
            }
            return
        }
        viewModelScope.launch {
            val result = connectToTvUseCase(device)
            _connectionResult.emit(result)
        }
    }

    private fun startPairing(device: TvDevice) {
        pairingJob?.cancel()
        currentPairingDevice = device
        pairingJob = viewModelScope.launch {
            pairTvUseCase(device).collect { step ->
                _pairingStep.value = step
                if (step is PairingStep.Succeeded) {
                    // Sau khi lưu credential (FW-6), tiến hành connect
                    val ref = step.credentials?.let {
                        tvCredentialStore.saveCredentials(device.id, it)
                    }
                    val result = connectToTvUseCase(device.copy(isPaired = true, credentialsRef = ref))
                    _connectionResult.emit(result)
                    _pairingStep.value = null
                }
            }
        }
    }

    fun submitPin(pin: String) {
        viewModelScope.launch {
            currentPairingDevice?.let {
                pairTvUseCase.providePin(it.protocol, pin)
            }
        }
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        currentPairingDevice = null
        _pairingStep.value = null
    }

    fun connectToManualIp(ipAddress: String) {
        viewModelScope.launch {
            val device = scanTvDevicesUseCase.identifyManualIp(ipAddress)
            if (device != null) {
                val brand = _targetBrand.value
                val finalizedDevice = if (device.protocol == com.example.smartclick.app.domain.model.TvProtocol.UNKNOWN) {
                    device.copy(brand = brand)
                } else device

                if (!finalizedDevice.isPaired) {
                    val plan = subscriptionRepository.currentPlan.first()
                    val paired = tvDeviceRepository.getPairedDevices().first()
                    val result = pairingLimitPolicy.evaluate(plan, paired)
                    if (result is com.example.smartclick.app.domain.policy.PairingResult.LimitExceeded) {
                        _connectionResult.emit(ConnectOutcome.PaywallRequired)
                        return@launch
                    }
                }

                connectToDevice(finalizedDevice)
            } else {
                _connectionResult.emit(ConnectOutcome.Failed("Could not identify TV at $ipAddress"))
            }
        }
    }

    fun removeDevice(deviceId: String) {
        viewModelScope.launch {
            removeDeviceUseCase(deviceId)
        }
    }

    fun logSupportRequest() {
        val unknownList = _unknownDevices.value
        unknownList.forEach { device ->
            // In a real app this would go to telemetry/analytics.
            android.util.Log.i("SmartClickSupport", "User requested support for unknown device: ${device.brand} at ${device.ipAddress} - protocol: ${device.protocol}")
        }
    }
}
