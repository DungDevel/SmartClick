#!/bin/bash
awk '
/val scanState: StateFlow<ScanState> = _scanState.asStateFlow()/ {
    print
    print ""
    print "    private val _pairingStep = MutableStateFlow<PairingStep?>(null)"
    print "    val pairingStep: StateFlow<PairingStep?> = _pairingStep.asStateFlow()"
    print ""
    print "    private var currentPairingDevice: TvDevice? = null"
    print "    private var pairingJob: Job? = null"
    next
}
/fun connectToDevice\(device: TvDevice\)/ {
    print "    fun connectToDevice(device: TvDevice) {"
    print "        if (!device.isPaired) {"
    print "            startPairing(device)"
    print "            return"
    print "        }"
    print "        viewModelScope.launch {"
    print "            val result = connectToTvUseCase(device)"
    print "            _connectionResult.emit(result)"
    print "        }"
    print "    }"
    print ""
    print "    private fun startPairing(device: TvDevice) {"
    print "        pairingJob?.cancel()"
    print "        currentPairingDevice = device"
    print "        pairingJob = viewModelScope.launch {"
    print "            pairTvUseCase(device).collect { step ->"
    print "                _pairingStep.value = step"
    print "                if (step is PairingStep.Succeeded) {"
    print "                    // Sau khi lưu credential (FW-6), tiến hành connect"
    print "                    val result = connectToTvUseCase(device.copy(isPaired = true))"
    print "                    _connectionResult.emit(result)"
    print "                    _pairingStep.value = null"
    print "                }"
    print "            }"
    print "        }"
    print "    }"
    print ""
    print "    fun submitPin(pin: String) {"
    print "        viewModelScope.launch {"
    print "            currentPairingDevice?.let {"
    print "                pairTvUseCase.providePin(it.protocol, pin)"
    print "            }"
    print "        }"
    print "    }"
    print ""
    print "    fun cancelPairing() {"
    print "        pairingJob?.cancel()"
    print "        pairingJob = null"
    print "        currentPairingDevice = null"
    print "        _pairingStep.value = null"
    print "    }"
    next
}
/fun connectToManualIp\(ipAddress: String\)/ {
    print "    fun connectToManualIp(ipAddress: String) {"
    print "        val brand = _targetBrand.value"
    print "        val id = \"manual_${ipAddress.replace(\".\", \"_\")}\""
    print "        val device = TvDevice(id = id, ipAddress = ipAddress, name = \"$brand TV (Manual)\", brand = brand, macAddress = \"\")"
    print "        connectToDevice(device)"
    print "    }"
    next
}
{ print }
' app/src/main/java/com/example/smartclick/app/features/devices/DevicesViewModel.kt > temp.kt
mv temp.kt app/src/main/java/com/example/smartclick/app/features/devices/DevicesViewModel.kt
