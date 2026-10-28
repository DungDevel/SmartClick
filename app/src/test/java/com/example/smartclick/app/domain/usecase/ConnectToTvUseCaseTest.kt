package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.policy.PairingLimitPolicy
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import com.example.smartclick.app.tvconnection.ActiveTvSession
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectToTvUseCaseTest {

    private lateinit var tvDeviceRepository: ITvDeviceRepository
    private lateinit var subscriptionRepository: ISubscriptionRepository
    private lateinit var activeTvSession: ActiveTvSession
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
        val pairedTv = TvDevice("oldId", "Old TV", "ip", "mac", isPaired = true)
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(listOf(pairedTv))

        val newTv = TvDevice("newId", "New TV", "ip2", "mac2")

        val result = useCase(newTv)

        assertTrue(result is ConnectOutcome.PaywallRequired)
    }

    @Test
    fun `connect succeeds when free user reconnects to same paired TV`() = runTest {
        val pairedTv = TvDevice("oldId", "Old TV", "ip", "mac", isPaired = true)
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(listOf(pairedTv))

        coEvery { mockController.connect(any()) } returns Result.success(Unit)

        val result = useCase(pairedTv)

        assertTrue(result is ConnectOutcome.Connected)
        coVerify { mockController.connect(pairedTv) }
    }

    @Test
    fun `connect succeeds for premium user with multiple TVs`() = runTest {
        every { subscriptionRepository.currentPlan } returns flowOf(SubscriptionPlan.PREMIUM)
        val pairedTv = TvDevice("oldId", "Old TV", "ip", "mac", isPaired = true)
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(listOf(pairedTv))

        coEvery { mockController.connect(any()) } returns Result.success(Unit)

        val newTv = TvDevice("newId", "New TV", "ip2", "mac2")

        val result = useCase(newTv)

        assertTrue(result is ConnectOutcome.Connected)
        coVerify { mockController.connect(newTv) }
    }

    @Test
    fun `connect returns failure when creating pinned controller throws`() = runTest {
        val pairedTv = TvDevice("oldId", "Old TV", "ip", "mac", isPaired = true)
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(listOf(pairedTv))
        every { mockDriver.createController(any(), any()) } throws IllegalStateException("pinned setup failed")

        val result = useCase(pairedTv)

        assertTrue(result is ConnectOutcome.Failed)
        verify(exactly = 0) { activeTvSession.setActiveController(any()) }
    }
}
