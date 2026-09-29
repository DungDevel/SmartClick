#!/bin/bash
sed -i 's/private val scanTvDevicesUseCase: ScanTvDevicesUseCase/private val scanTvDevicesUseCase: ScanTvDevicesUseCase,\n    private val pairTvUseCase: PairTvUseCase/' app/src/main/java/com/example/smartclick/app/features/devices/DevicesViewModel.kt
