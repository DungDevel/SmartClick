package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.policy.PairingLimitPolicy
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import com.example.smartclick.app.domain.connection.ConnectOutcome
import com.example.smartclick.app.domain.connection.DriverRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ConnectToTvUseCaseTest {

    private lateinit var tvDeviceRepository: ITvDeviceRepository
    private lateinit var subscriptionRepository: ISubscriptionRepository
    private lateinit var activeTvSession: IActiveTvSession
    private lateinit var currentControllerFlow: MutableStateFlow<ITvController?>
    private lateinit var driverRegistry: DriverRegistry
    private lateinit var pairingLimitPolicy: PairingLimitPolicy
    private lateinit var tvCredentialStore: ITvCredentialStore

    private lateinit var useCase: ConnectToTvUseCase
    private lateinit var mockDriver: IProtocolDriver
    private lateinit var mockController: ITvController

    @Before
    fun setup() {
        tvDeviceRepository = mockk(relaxed = true)
        subscriptionRepository = mockk(relaxed = true)
        activeTvSession = mockk(relaxed = true)
        currentControllerFlow = MutableStateFlow(null)
        every { activeTvSession.currentController } returns currentControllerFlow
        every { activeTvSession.setActiveController(any()) } answers {
            currentControllerFlow.value = firstArg()
        }
        driverRegistry = mockk(relaxed = true)
        tvCredentialStore = mockk(relaxed = true)
        pairingLimitPolicy = PairingLimitPolicy()

        mockController = mockk(relaxed = true)
        mockDriver = mockk(relaxed = true)
        every { mockDriver.createController(any(), any()) } returns mockController
        coEvery { tvCredentialStore.getCredentials(any(), any()) } returns null
        every { driverRegistry.getDriver(any()) } returns mockDriver
        every { subscriptionRepository.currentPlan } returns flowOf(SubscriptionPlan.FREE)

        useCase = ConnectToTvUseCase(
            tvDeviceRepository = tvDeviceRepository,
            activeTvSession = activeTvSession,
            driverRegistry = driverRegistry,
            pairingLimitPolicy = pairingLimitPolicy,
            subscriptionRepository = subscriptionRepository,
            tvCredentialStore = tvCredentialStore
        )
    }

    @Test
    fun `connect fails when free user already has another paired TV`() = runTest {
        val pairedTv = TvDevice("oldId", "Old TV", "192.168.1.10", "mac", isPaired = true)
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(listOf(pairedTv))

        val newTv = TvDevice("newId", "New TV", "192.168.1.11", "mac2")

        val result = useCase(newTv)

        assertTrue(result is ConnectOutcome.PaywallRequired)
    }

    @Test
    fun `connect succeeds when free user reconnects to same paired TV`() = runTest {
        val pairedTv = TvDevice("oldId", "Old TV", "192.168.1.10", "mac", isPaired = true)
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(listOf(pairedTv))

        coEvery { mockController.connect(any()) } returns Result.success(Unit)

        val result = useCase(pairedTv)

        assertTrue(result is ConnectOutcome.Connected)
        coVerify { mockController.connect(pairedTv) }
    }

    @Test
    fun `connect succeeds for premium user with multiple TVs`() = runTest {
        every { subscriptionRepository.currentPlan } returns flowOf(SubscriptionPlan.PREMIUM)
        val pairedTv = TvDevice("oldId", "Old TV", "192.168.1.10", "mac", isPaired = true)
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(listOf(pairedTv))

        coEvery { mockController.connect(any()) } returns Result.success(Unit)

        val newTv = TvDevice("newId", "New TV", "192.168.1.11", "mac2")

        val result = useCase(newTv)

        assertTrue(result is ConnectOutcome.Connected)
        coVerify { mockController.connect(newTv) }
    }

    @Test
    fun `connect returns failure when creating pinned controller throws`() = runTest {
        val pairedTv = TvDevice("oldId", "Old TV", "192.168.1.10", "mac", isPaired = true)
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(listOf(pairedTv))
        every { mockDriver.createController(any(), any()) } throws IllegalStateException("pinned setup failed")

        val result = useCase(pairedTv)

        assertTrue(result is ConnectOutcome.Failed)
        verify(exactly = 0) { activeTvSession.setActiveController(any()) }
    }

    @Test
    fun `late successful connect after cancellation disconnects and does not save device`() = runTest {
        val device = TvDevice("tv-a", "TV A", "192.168.1.10", "mac", isPaired = true, lastConnectedAt = 10L)
        val started = CompletableDeferred<Unit>()
        val finishConnect = CompletableDeferred<Unit>()
        coEvery { mockController.connect(device) } coAnswers {
            started.complete(Unit)
            suspendCoroutine { continuation ->
                finishConnect.invokeOnCompletion { cause ->
                    if (cause == null) continuation.resume(Result.success(Unit))
                    else continuation.resume(Result.failure(cause))
                }
            }
        }
        coEvery { mockController.disconnect() } returns Unit

        val job = launch { useCase(device) }
        runCurrent()
        assertTrue(started.isCompleted)
        assertSame(mockController, currentControllerFlow.value)

        job.cancel()
        finishConnect.complete(Unit)
        runCurrent()

        assertTrue(job.isCompleted)
        coVerify(exactly = 0) { tvDeviceRepository.saveDevice(any()) }
        coVerify(exactly = 1) { mockController.disconnect() }
        assertNull(currentControllerFlow.value)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `late successful connect after cancellation does not clear another active controller`() = runTest {
        val device = TvDevice("tv-a", "TV A", "192.168.1.10", "mac", isPaired = true)
        val controllerB = mockk<ITvController>(relaxed = true)
        val started = CompletableDeferred<Unit>()
        val finishConnect = CompletableDeferred<Unit>()
        coEvery { mockController.connect(device) } coAnswers {
            started.complete(Unit)
            suspendCoroutine { continuation ->
                finishConnect.invokeOnCompletion { cause ->
                    if (cause == null) continuation.resume(Result.success(Unit))
                    else continuation.resume(Result.failure(cause))
                }
            }
        }
        coEvery { mockController.disconnect() } returns Unit

        val job = launch { useCase(device) }
        runCurrent()
        assertTrue(started.isCompleted)
        job.cancel()
        activeTvSession.setActiveController(controllerB)
        finishConnect.complete(Unit)
        runCurrent()

        assertSame(controllerB, currentControllerFlow.value)
        coVerify(exactly = 0) { activeTvSession.setActiveController(null) }
        coVerify(exactly = 0) { tvDeviceRepository.saveDevice(any()) }
        coVerify(exactly = 1) { mockController.disconnect() }
    }

    @Test
    fun `connect success saves paired device and last connected time`() = runTest {
        val device = TvDevice("tv-a", "TV A", "192.168.1.10", "mac", isPaired = false)
        every { subscriptionRepository.currentPlan } returns flowOf(SubscriptionPlan.PREMIUM)
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(emptyList())
        coEvery { mockController.connect(device) } returns Result.success(Unit)

        val result = useCase(device)

        assertTrue(result is ConnectOutcome.Connected)
        coVerify(exactly = 1) {
            tvDeviceRepository.saveDevice(match {
                it.id == device.id && it.isPaired && it.lastConnectedAt > 0L
            })
        }
        assertSame(mockController, currentControllerFlow.value)
    }

    @Test
    fun `failed connect does not clear a different active controller`() = runTest {
        val device = TvDevice("tv-a", "TV A", "192.168.1.10", "mac", isPaired = true)
        val controllerB = mockk<ITvController>(relaxed = true)
        coEvery { mockController.connect(device) } coAnswers {
            activeTvSession.setActiveController(controllerB)
            Result.failure(IllegalStateException("offline"))
        }

        val result = useCase(device)

        assertTrue(result is ConnectOutcome.Failed)
        assertSame(controllerB, currentControllerFlow.value)
        coVerify(exactly = 0) { activeTvSession.setActiveController(null) }
        coVerify(exactly = 0) { tvDeviceRepository.saveDevice(any()) }
    }

    @Test
    fun `connect fails when device has public IP address`() = runTest {
        val device = TvDevice("tv-public", "Public IP TV", "8.8.8.8", "mac", isPaired = true)

        val result = useCase(device)

        assertTrue(result is ConnectOutcome.Failed)
        coVerify(exactly = 0) { mockDriver.createController(any(), any()) }
    }
}
