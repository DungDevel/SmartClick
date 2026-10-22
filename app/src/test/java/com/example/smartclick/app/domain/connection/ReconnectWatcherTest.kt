package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.usecase.ConnectToTvUseCase
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectWatcherTest {

    private val activeTvSession = mockk<IActiveTvSession>(relaxed = true)
    private val connectToTvUseCase = mockk<ConnectToTvUseCase>()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var reconnectWatcher: ReconnectWatcher

    private val sampleDevice = TvDevice(
        id = "dev_1",
        name = "Test TV",
        ipAddress = "192.168.1.50",
        macAddress = "00:11:22:33:44:55",
        protocol = TvProtocol.ANDROID_TV_V2
    )

    private val mockController = mockk<ITvController>(relaxed = true)

    @Before
    fun setup() {
        every { activeTvSession.currentController } returns MutableStateFlow(mockController)
        every { mockController.device } returns sampleDevice
        reconnectWatcher = ReconnectWatcher(activeTvSession, connectToTvUseCase, testScope)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `success command resets failure count`() = testScope.runTest {
        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(true)
        // Next failure should start from 1, not trigger reconnect immediately
        reconnectWatcher.onCommandResult(false)
        coVerify(exactly = 0) { connectToTvUseCase(any()) }
    }

    @Test
    fun `reaching failure threshold triggers reconnect with backoff`() = testScope.runTest {
        coEvery { connectToTvUseCase(sampleDevice) } returns ConnectOutcome.Connected

        // 2 failures triggers reconnect
        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)

        testScope.advanceTimeBy(2000L) // pass initial delay

        coVerify(atLeast = 1) { connectToTvUseCase(sampleDevice) }
        verify { activeTvSession.setConnectionState(match { it is TvConnectionState.Reconnecting }) }
        verify { activeTvSession.setConnectionState(match { it is TvConnectionState.Connected }) }
    }

    @Test
    fun `max attempts exceeded sets failed state`() = testScope.runTest {
        coEvery { connectToTvUseCase(sampleDevice) } returns ConnectOutcome.Failed("Network timeout")

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)

        // Advance through exponential backoff attempts (1.5s, 3s, 6s, 10s, 10s...)
        testScope.advanceTimeBy(40000L)

        verify { activeTvSession.setConnectionState(match { it is TvConnectionState.Failed }) }
    }

    @Test
    fun `permanent authentication failure aborts retries immediately`() = testScope.runTest {
        coEvery { connectToTvUseCase(sampleDevice) } returns ConnectOutcome.Failed("Authentication rejected")

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)

        testScope.advanceTimeBy(2000L)

        // Should call connect once and immediately set failed state without further retries
        coVerify(exactly = 1) { connectToTvUseCase(sampleDevice) }
        verify { activeTvSession.setConnectionState(match { it is TvConnectionState.Failed }) }
    }

    @Test
    fun `explicit cancellation stops reconnect loop`() = testScope.runTest {
        coEvery { connectToTvUseCase(sampleDevice) } returns ConnectOutcome.Failed("Timeout")

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)

        testScope.advanceTimeBy(1600L) // pass initial delay, triggers 1st attempt
        coVerify(exactly = 1) { connectToTvUseCase(sampleDevice) }

        reconnectWatcher.cancelReconnect()

        testScope.advanceTimeBy(30000L) // pass subsequent backoff periods

        // Should not retry further after cancellation
        coVerify(exactly = 1) { connectToTvUseCase(sampleDevice) }
    }

    @Test
    fun `exponential delay progression and max cap`() = testScope.runTest {
        val timestamps = mutableListOf<Long>()
        coEvery { connectToTvUseCase(sampleDevice) } answers {
            timestamps.add(testScope.testScheduler.currentTime)
            ConnectOutcome.Failed("Timeout")
        }

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)

        // Attempt 1 at t=1500
        testScope.advanceTimeBy(1600L)
        assertEquals(1, timestamps.size)

        // Attempt 2 at t=1500 + 3000 = 4500
        testScope.advanceTimeBy(3100L)
        assertEquals(2, timestamps.size)

        // Attempt 3 at t=4500 + 6000 = 10500
        testScope.advanceTimeBy(6100L)
        assertEquals(3, timestamps.size)

        // Attempt 4 at t=10500 + 10000 (capped at 10s) = 20500
        testScope.advanceTimeBy(10100L)
        assertEquals(4, timestamps.size)
    }

    @Test
    fun `concurrent reconnect triggers do not spawn duplicate jobs`() = testScope.runTest {
        coEvery { connectToTvUseCase(sampleDevice) } returns ConnectOutcome.Failed("Timeout")

        // Trigger multiple failures concurrently
        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)

        testScope.advanceTimeBy(1600L)
        // Should only have 1 active reconnect job / attempt per threshold/isReconnecting guard
        coVerify(exactly = 1) { connectToTvUseCase(sampleDevice) }
    }

    @Test
    fun `session invalidation during backoff stops retry work`() = testScope.runTest {
        val controllerFlow = MutableStateFlow<ITvController?>(mockController)
        every { activeTvSession.currentController } returns controllerFlow

        coEvery { connectToTvUseCase(sampleDevice) } returns ConnectOutcome.Failed("Timeout")

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)

        testScope.advanceTimeBy(500L)
        // Invalidate session (set controller to null)
        controllerFlow.value = null

        testScope.advanceTimeBy(30000L)

        // Should not have executed connect since session became null
        coVerify(exactly = 0) { connectToTvUseCase(any()) }
    }

    @Test
    fun `all permanent failure categories stop retries immediately`() = testScope.runTest {
        val permanentOutcomes = listOf(
            ConnectOutcome.PaywallRequired,
            ConnectOutcome.Failed("Pairing rejected"),
            ConnectOutcome.Failed("Unauthorized access"),
            ConnectOutcome.Failed("Unsupported protocol"),
            ConnectOutcome.Failed("Authentication failed")
        )

        for (outcome in permanentOutcomes) {
            clearMocks(connectToTvUseCase, activeTvSession, answers = false, recordedCalls = true)
            every { activeTvSession.currentController } returns MutableStateFlow(mockController)
            every { activeTvSession.currentController.value } returns mockController
            every { mockController.device } returns sampleDevice
            coEvery { connectToTvUseCase(sampleDevice) } returns outcome

            reconnectWatcher.cancelReconnect()
            reconnectWatcher.onCommandResult(false)
            reconnectWatcher.onCommandResult(false)

            testScope.advanceTimeBy(2000L)

            coVerify(exactly = 1) { connectToTvUseCase(sampleDevice) }
            verify { activeTvSession.setConnectionState(match { it is TvConnectionState.Failed }) }
        }
    }
}
