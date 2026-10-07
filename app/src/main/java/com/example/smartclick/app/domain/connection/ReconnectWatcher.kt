package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.di.ApplicationScope
import com.example.smartclick.app.domain.usecase.ConnectToTvUseCase
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * ReconnectWatcher tracks command failures and implements exponential backoff
 * to automatically trigger reconnections when the TV connection drops. (Phase 3 Fix)
 */
@Singleton
class ReconnectWatcher @Inject constructor(
    private val activeTvSession: IActiveTvSession,
    private val connectToTvUseCase: ConnectToTvUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var consecutiveFailures = 0
    private var isReconnecting = false
    private val mutex = Mutex()

    private val FAILURE_THRESHOLD = 2
    private val MAX_ATTEMPTS = 5

    fun onCommandResult(success: Boolean) {
        scope.launch {
            mutex.withLock {
                if (success) {
                    consecutiveFailures = 0
                    isReconnecting = false
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= FAILURE_THRESHOLD && !isReconnecting) {
                        triggerReconnectLocked()
                    }
                }
            }
        }
    }

    private fun triggerReconnectLocked() {
        if (isReconnecting) return
        isReconnecting = true

        val device = activeTvSession.currentController.value?.device
        if (device == null) {
            // No active device to reconnect to
            isReconnecting = false
            consecutiveFailures = 0
            return
        }

        scope.launch {
            var attempt = 1
            var delayMs = 1500L

            while (attempt <= MAX_ATTEMPTS) {
                mutex.withLock {
                    if (!isReconnecting) return@launch
                }

                activeTvSession.setConnectionState(TvConnectionState.Reconnecting(attempt))

                // Wait for the backoff period
                delay(delayMs)

                // Attempt to reconnect
                val result = connectToTvUseCase(device)

                if (result is com.example.smartclick.app.domain.connection.ConnectOutcome.Connected) {
                    mutex.withLock {
                        consecutiveFailures = 0
                        isReconnecting = false
                    }
                    activeTvSession.setConnectionState(TvConnectionState.Connected(device))
                    return@launch
                }

                attempt++
                delayMs = min(delayMs * 2, 10000L) // Exponential backoff, cap at 10s
            }

            // If we've reached here, all attempts failed
            mutex.withLock {
                isReconnecting = false
            }
            activeTvSession.setConnectionState(TvConnectionState.Failed("Connection dropped"))
        }
    }
}


