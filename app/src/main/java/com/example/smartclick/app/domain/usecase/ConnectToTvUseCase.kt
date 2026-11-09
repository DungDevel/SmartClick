package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.policy.PairingLimitPolicy
import com.example.smartclick.app.domain.policy.PairingResult
import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import com.example.smartclick.app.domain.connection.ConnectOutcome
import com.example.smartclick.app.domain.connection.DriverRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ConnectToTvUseCase @Inject constructor(
    private val tvDeviceRepository: ITvDeviceRepository,
    private val activeTvSession: IActiveTvSession,
    private val driverRegistry: DriverRegistry,
    private val pairingLimitPolicy: PairingLimitPolicy,
    private val subscriptionRepository: ISubscriptionRepository,
    private val tvCredentialStore: ITvCredentialStore
) {
    suspend operator fun invoke(device: TvDevice): ConnectOutcome {
        if (!com.example.smartclick.app.tvconnection.utils.NetworkSecurityUtils.isPrivateLanAddress(device.ipAddress)) {
            return ConnectOutcome.Failed("Invalid or public IP address destination blocked by security policy.")
        }

        if (!device.isPaired) {
            val plan = subscriptionRepository.currentPlan.first()
            val pairedList = tvDeviceRepository.getPairedDevices().first()
            if (pairingLimitPolicy.evaluate(plan, pairedList) is PairingResult.LimitExceeded) {
                return ConnectOutcome.PaywallRequired
            }
        }

        val driver = driverRegistry.getDriver(device.protocol)
            ?: return ConnectOutcome.Failed("No driver found for protocol: ${device.protocol}")

        val credentials = device.credentialsRef?.let { ref ->
            tvCredentialStore.getCredentials(device.id, ref)
        }
        val controller = try {
            driver.createController(device, credentials)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ConnectOutcome.Failed(e.message ?: "Failed to create TV controller")
        }
        activeTvSession.setActiveController(controller)

        val result = try {
            val connectResult = controller.connect(device)
            currentCoroutineContext().ensureActive()
            connectResult
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                try {
                    controller.disconnect()
                } catch (cleanupFailure: Exception) {
                    cancellation.addSuppressed(cleanupFailure)
                }
                if (activeTvSession.currentController.value === controller) {
                    activeTvSession.setActiveController(null)
                }
            }
            throw cancellation
        }

        return if (result.isSuccess) {
            val connectedDevice = device.copy(
                isPaired = true,
                lastConnectedAt = System.currentTimeMillis()
            )
            tvDeviceRepository.saveDevice(connectedDevice)
            ConnectOutcome.Connected
        } else {
            if (activeTvSession.currentController.value === controller) {
                activeTvSession.setActiveController(null)
            }
            val errorMessage = result.exceptionOrNull()?.message ?: "Unknown Connection Error"
            ConnectOutcome.Failed(errorMessage)
        }
    }
}
