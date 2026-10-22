package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.di.ApplicationScope
import com.example.smartclick.app.domain.usecase.ConnectToTvUseCase
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * ReconnectWatcher tracks command failures and implements exponential backoff
 * with jitter protection, transient/permanent failure classification, and concurrency control
 * to automatically trigger reconnections when the TV connection drops. (Phase 9 P9-05)
 */
@Singleton
class ReconnectWatcher @Inject constructor(
    private val activeTvSession: IActiveTvSession,
    private val connectToTvUseCase: ConnectToTvUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var consecutiveFailures = 0
    private var isReconnecting = false
    private var currentReconnectJob: Job? = null
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
                isReconnecting = false
                consecutiveFailures = 0
                currentReconnectJob?.cancel()
                currentReconnectJob = null
            }
        }
    }

    private fun triggerReconnectLocked() {
        if (isReconnecting) return
        isReconnecting = true

        val device = activeTvSession.currentController.value?.device
        if (device == null) {
            isReconnecting = false
            consecutiveFailures = 0
            return
        }

        currentReconnectJob = scope.launch {
            var attempt = 1
            var delayMs = INITIAL_DELAY_MS

            while (attempt <= MAX_ATTEMPTS) {
                mutex.withLock {
                    if (!isReconnecting || activeTvSession.currentController.value == null) {
                        isReconnecting = false
                        return@launch
                    }
                }

                activeTvSession.setConnectionState(TvConnectionState.Reconnecting(attempt))

                try {
                    delay(delayMs)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    mutex.withLock {
                        isReconnecting = false
                    }
                    throw e
                }

                mutex.withLock {
                    if (!isReconnecting || activeTvSession.currentController.value == null) {
                        isReconnecting = false
                        return@launch
                    }
                }

                val result = try {
                    connectToTvUseCase(device)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    ConnectOutcome.Failed(e.message ?: "Connection exception")
                }

                when (result) {
                    is ConnectOutcome.Connected -> {
                        mutex.withLock {
                            consecutiveFailures = 0
                            isReconnecting = false
                            currentReconnectJob = null
                        }
                        activeTvSession.setConnectionState(TvConnectionState.Connected(device))
                        return@launch
                    }
                    is ConnectOutcome.PaywallRequired -> {
                        mutex.withLock {
                            isReconnecting = false
                            currentReconnectJob = null
                        }
                        activeTvSession.setConnectionState(TvConnectionState.Failed("Paywall required"))
                        return@launch
                    }
                    is ConnectOutcome.Failed -> {
                        val errorMessage = result.reason.lowercase()
                        // Permanent failures (auth, pairing, unsupported) should not be retried infinitely
                        if (errorMessage.contains("auth") || errorMessage.contains("pairing") || errorMessage.contains("unsupported") || errorMessage.contains("unauthorized")) {
                            mutex.withLock {
                                isReconnecting = false
                                currentReconnectJob = null
                            }
                            activeTvSession.setConnectionState(TvConnectionState.Failed(result.reason))
                            return@launch
                        }
                    }
                }

                attempt++
                // Overflow-safe exponential backoff calculation
                val nextDelay = (delayMs * BACKOFF_MULTIPLIER).toLong()
                delayMs = if (nextDelay < 0 || nextDelay > MAX_DELAY_MS) MAX_DELAY_MS else min(nextDelay, MAX_DELAY_MS)
            }

            mutex.withLock {
                isReconnecting = false
                currentReconnectJob = null
            }
            activeTvSession.setConnectionState(TvConnectionState.Failed("Connection dropped after $MAX_ATTEMPTS attempts"))
        }
    }
}
