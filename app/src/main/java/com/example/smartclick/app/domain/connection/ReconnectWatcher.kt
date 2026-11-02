package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.di.ApplicationScope
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.usecase.ConnectToTvUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** Tracks transient command failures and retries the device that was active when retry began. */
@Singleton
class ReconnectWatcher @Inject constructor(
    private val activeTvSession: IActiveTvSession,
    private val connectToTvUseCase: ConnectToTvUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var consecutiveFailures = 0
    private var isReconnecting = false
    private var currentReconnectJob: Job? = null
    private var reconnectGeneration = 0L
    private var reconnectDeviceId: String? = null
    private val mutex = Mutex()

    private val FAILURE_THRESHOLD = 2
    private val MAX_ATTEMPTS = 5
    private val INITIAL_DELAY_MS = 1500L
    private val MAX_DELAY_MS = 10000L
    private val BACKOFF_MULTIPLIER = 2.0

    fun onCommandResult(success: Boolean) {
        scope.launch {
            mutex.withLock {
                if (success) {
                    consecutiveFailures = 0
                    isReconnecting = false
                    reconnectGeneration++
                    reconnectDeviceId = null
                    currentReconnectJob?.cancel()
                    currentReconnectJob = null
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= FAILURE_THRESHOLD && !isReconnecting) {
                        triggerReconnectLocked()
                    }
                }
            }
        }
    }

    fun cancelReconnect() {
        scope.launch {
            mutex.withLock {
                reconnectGeneration++
                isReconnecting = false
                consecutiveFailures = 0
                reconnectDeviceId = null
                currentReconnectJob?.cancel()
                currentReconnectJob = null
            }
        }
    }

    private fun triggerReconnectLocked() {
        if (isReconnecting) return
        val device = activeTvSession.currentController.value?.device
        if (device == null) {
            consecutiveFailures = 0
            return
        }

        isReconnecting = true
        reconnectDeviceId = device.id
        val generation = ++reconnectGeneration
        currentReconnectJob = scope.launch {
            val deviceChangeMonitor = launch {
                activeTvSession.currentController.collect { controller ->
                    if (controller?.device?.id != device.id) {
                        mutex.withLock {
                            if (reconnectGeneration == generation && isReconnecting) {
                                reconnectGeneration++
                                consecutiveFailures = 0
                                isReconnecting = false
                                reconnectDeviceId = null
                                currentReconnectJob?.cancel()
                                currentReconnectJob = null
                            }
                        }
                    }
                }
            }
            try {
                runReconnectLoop(device, generation)
            } finally {
                deviceChangeMonitor.cancel()
            }
        }
    }

    private suspend fun runReconnectLoop(device: TvDevice, generation: Long) {
            var attempt = 1
            var delayMs = INITIAL_DELAY_MS

            while (attempt <= MAX_ATTEMPTS) {
                val mayWriteReconnecting = mutex.withLock {
                    if (!isCurrentReconnectLocked(generation, device.id)) {
                        clearStaleReconnectLocked(generation)
                        false
                    } else {
                        activeTvSession.setConnectionState(TvConnectionState.Reconnecting(attempt))
                        true
                    }
                }
                if (!mayWriteReconnecting) return

                try {
                    delay(delayMs)
                } catch (e: CancellationException) {
                    mutex.withLock { clearStaleReconnectLocked(generation) }
                    throw e
                }

                val mayConnect = mutex.withLock {
                    isCurrentReconnectLocked(generation, device.id)
                }
                if (!mayConnect) {
                    mutex.withLock { clearStaleReconnectLocked(generation) }
                    return
                }

                val result = try {
                    connectToTvUseCase(device)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    ConnectOutcome.Failed(e.message ?: "Connection exception")
                }

                when (result) {
                    is ConnectOutcome.Connected -> {
                        finishReconnect(
                            generation,
                            device.id,
                            TvConnectionState.Connected(device),
                            resetFailures = true
                        )
                        return
                    }
                    is ConnectOutcome.PaywallRequired -> {
                        finishReconnect(
                            generation,
                            device.id,
                            TvConnectionState.Failed("Paywall required")
                        )
                        return
                    }
                    is ConnectOutcome.Failed -> {
                        val errorMessage = result.reason.lowercase()
                        if (errorMessage.contains("auth") || errorMessage.contains("pairing") ||
                            errorMessage.contains("unsupported") || errorMessage.contains("unauthorized")
                        ) {
                            finishReconnect(
                                generation,
                                device.id,
                                TvConnectionState.Failed(result.reason)
                            )
                            return
                        }
                    }
                }

                attempt++
                val nextDelay = (delayMs * BACKOFF_MULTIPLIER).toLong()
                delayMs = if (nextDelay < 0 || nextDelay > MAX_DELAY_MS) MAX_DELAY_MS
                else min(nextDelay, MAX_DELAY_MS)
            }

            finishReconnect(
                generation,
                device.id,
                TvConnectionState.Failed("Connection dropped after $MAX_ATTEMPTS attempts")
            )
    }

    private fun isCurrentReconnectLocked(generation: Long, deviceId: String): Boolean =
        isReconnecting && reconnectGeneration == generation && reconnectDeviceId == deviceId &&
            activeTvSession.currentController.value?.device?.id == deviceId

    private fun clearStaleReconnectLocked(generation: Long) {
        if (reconnectGeneration == generation) {
            isReconnecting = false
            reconnectDeviceId = null
            currentReconnectJob = null
        }
    }

    private suspend fun finishReconnect(
        generation: Long,
        deviceId: String,
        state: TvConnectionState,
        resetFailures: Boolean = false
    ) {
        mutex.withLock {
            if (!isCurrentReconnectLocked(generation, deviceId)) {
                clearStaleReconnectLocked(generation)
                return
            }
            if (resetFailures) consecutiveFailures = 0
            isReconnecting = false
            reconnectDeviceId = null
            currentReconnectJob = null
            activeTvSession.setConnectionState(state)
        }
    }
}
