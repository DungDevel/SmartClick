package com.example.smartclick.app.features.devices

import app.cash.turbine.test
import com.example.smartclick.app.domain.connection.*
import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.policy.PairingLimitPolicy
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import com.example.smartclick.app.domain.usecase.ConnectToTvUseCase
import com.example.smartclick.app.domain.usecase.PairTvUseCase
import com.example.smartclick.app.domain.usecase.RemoveDeviceUseCase
import com.example.smartclick.app.domain.usecase.ScanTvDevicesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {

    private lateinit var tvDeviceRepository: ITvDeviceRepository
    private lateinit var scanTvDevicesUseCase: ScanTvDevicesUseCase
    private lateinit var pairTvUseCase: PairTvUseCase
    private lateinit var connectToTvUseCase: ConnectToTvUseCase
    private lateinit var removeDeviceUseCase: RemoveDeviceUseCase
    private lateinit var wifiNetworkProvider: IWifiNetworkProvider
    private lateinit var activeTvSession: IActiveTvSession
    private lateinit var subscriptionRepository: ISubscriptionRepository
    private lateinit var tvCredentialStore: ITvCredentialStore
    private lateinit var pairingLimitPolicy: PairingLimitPolicy

    private lateinit var viewModel: DevicesViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        tvDeviceRepository = mockk(relaxed = true)
        scanTvDevicesUseCase = mockk(relaxed = true)
        pairTvUseCase = mockk(relaxed = true)
        connectToTvUseCase = mockk(relaxed = true)
        removeDeviceUseCase = mockk(relaxed = true)
        wifiNetworkProvider = mockk(relaxed = true)
        activeTvSession = mockk(relaxed = true)
        subscriptionRepository = mockk(relaxed = true)
        tvCredentialStore = mockk(relaxed = true)
        pairingLimitPolicy = PairingLimitPolicy()

        every { wifiNetworkProvider.getCurrentState() } returns NetworkState.WifiConnected("TestNet")
        every { wifiNetworkProvider.networkState } returns flowOf(NetworkState.WifiConnected("TestNet"))
        every { scanTvDevicesUseCase(any()) } returns flowOf(emptyList())
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(emptyList())
        val planFlow = MutableStateFlow(SubscriptionPlan.FREE)
        every { subscriptionRepository.currentPlan } returns planFlow
        every { activeTvSession.connectionState } returns MutableStateFlow(TvConnectionState.Idle)

        viewModel = DevicesViewModel(
            tvDeviceRepository,
            scanTvDevicesUseCase,
            pairTvUseCase,
            connectToTvUseCase,
            removeDeviceUseCase,
            wifiNetworkProvider,
            activeTvSession,
            subscriptionRepository,
            tvCredentialStore,
            pairingLimitPolicy
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `connectToDevice emits PaywallRequired for free user with already paired device`() = runTest {
        val pairedTv = TvDevice("oldId", "Old TV", "ip1", "mac1", isPaired = true, protocol = TvProtocol.ROKU_ECP)
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(listOf(pairedTv))

        val newTv = TvDevice("newId", "New TV", "ip2", "mac2", isPaired = false, protocol = TvProtocol.ROKU_ECP)

        // When connecting to an unpaired device while already having a paired device on FREE tier
        viewModel.connectionResult.test {
            viewModel.connectToDevice(newTv)

            val result = awaitItem()
            assertTrue(result is ConnectOutcome.PaywallRequired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connectToDevice starts pairing for free user with zero paired devices`() = runTest(testDispatcher) {
        every { tvDeviceRepository.getPairedDevices() } returns flowOf(emptyList())
        val newTv = TvDevice("newId", "New TV", "ip2", "mac2", isPaired = false, protocol = TvProtocol.ROKU_ECP)

        every { pairTvUseCase(any()) } returns flowOf(PairingStep.NotRequired)

        viewModel.connectToDevice(newTv)

        // Yield to allow coroutines to start
        testScheduler.advanceUntilIdle()

        // It shouldn't emit PaywallRequired, should just start pairing
        coVerify { pairTvUseCase(newTv) }
    }
}
