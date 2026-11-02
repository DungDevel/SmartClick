package com.example.smartclick.app.domain.connection

import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.usecase.ConnectToTvUseCase
import com.example.smartclick.app.domain.usecase.RemoveDeviceUseCase
import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
    private val controllerFlow = MutableStateFlow<ITvController?>(mockController)

    @Before
    fun setup() {
        every { activeTvSession.currentController } returns controllerFlow
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
    fun `pinned client setup failure stops retries as a permanent pairing failure`() = testScope.runTest {
        coEvery { connectToTvUseCase(sampleDevice) } returns ConnectOutcome.Failed(
            "Pairing credentials invalid: failed to create pinned LG WebOS client"
        )

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)

        testScope.advanceTimeBy(40000L)

        coVerify(exactly = 1) { connectToTvUseCase(sampleDevice) }
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
    fun `cancelReconnect cancels in flight connect before controller becomes null`() = testScope.runTest {
        var attempts = 0
        var cancellationObserved = false
        coEvery { connectToTvUseCase(sampleDevice) } coAnswers {
            attempts++
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancellationObserved = true }
            }
        }

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)
        testScope.advanceTimeBy(1600L)
        assertEquals(1, attempts)

        reconnectWatcher.cancelReconnect()
        assertTrue("in-flight connect must be cancelled before cancelReconnect returns", cancellationObserved)
        controllerFlow.value = null
        testScope.advanceUntilIdle()

        assertEquals(1, attempts)
        assertNull(controllerFlow.value)
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
    fun `null active controller during backoff does not cancel retry work`() = testScope.runTest {
        every { activeTvSession.currentController } returns controllerFlow

        coEvery { connectToTvUseCase(sampleDevice) } returns ConnectOutcome.Failed("Timeout")

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)

        testScope.advanceTimeBy(500L)
        // Invalidate session (set controller to null)
        controllerFlow.value = null

        testScope.advanceUntilIdle()

        // Null can be produced by a failed connect attempt; only explicit cancellation
        // or a different non-null device invalidates the reconnect generation.
        coVerify(exactly = 5) { connectToTvUseCase(sampleDevice) }
    }

    @Test
    fun `switching active device during backoff never reconnects the previous device`() = testScope.runTest {
        val secondDevice = sampleDevice.copy(id = "dev_2", name = "Other TV", ipAddress = "192.168.1.51")
        val secondController = mockk<ITvController>(relaxed = true)
        every { secondController.device } returns secondDevice
        controllerFlow.value = mockController
        coEvery { connectToTvUseCase(any()) } returns ConnectOutcome.Failed("Timeout")

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)
        testScope.advanceTimeBy(500L)
        controllerFlow.value = secondController
        testScope.advanceTimeBy(30000L)

        coVerify(exactly = 0) { connectToTvUseCase(sampleDevice) }
        verify(exactly = 0) { activeTvSession.setConnectionState(match { it == TvConnectionState.Connected(sampleDevice) }) }
    }

    @Test
    fun `successful reconnect does not write old device state after active device switches`() = testScope.runTest {
        val secondDevice = sampleDevice.copy(id = "dev_2", name = "Other TV", ipAddress = "192.168.1.51")
        val secondController = mockk<ITvController>(relaxed = true)
        every { secondController.device } returns secondDevice
        val reconnectStarted = CompletableDeferred<Unit>()
        val finishReconnect = CompletableDeferred<ConnectOutcome>()
        coEvery { connectToTvUseCase(sampleDevice) } coAnswers {
            reconnectStarted.complete(Unit)
            finishReconnect.await()
        }

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)
        testScope.advanceTimeBy(1600L)
        assertTrue("reconnect attempt should be in progress", reconnectStarted.isCompleted)

        controllerFlow.value = secondController
        finishReconnect.complete(ConnectOutcome.Connected)
        testScope.advanceUntilIdle()

        verify(exactly = 0) {
            activeTvSession.setConnectionState(TvConnectionState.Connected(sampleDevice))
        }
    }


    @Test
    fun `reconnect retries after failed use case clears active controller`() = testScope.runTest {
        var attempts = 0
        coEvery { connectToTvUseCase(sampleDevice) } coAnswers {
            attempts++
            val controller = mockk<ITvController>(relaxed = true)
            every { controller.device } returns sampleDevice.copy()
            controllerFlow.value = controller

            if (attempts == 1) {
                controllerFlow.value = null
                ConnectOutcome.Failed("Temporary network failure")
            } else {
                ConnectOutcome.Connected
            }
        }

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)
        testScope.advanceTimeBy(6000L)

        assertEquals("failed attempt should be retried for the same TV", 2, attempts)
        verify { activeTvSession.setConnectionState(TvConnectionState.Connected(sampleDevice)) }
    }

    @Test
    fun `maximum failed attempts finish in Failed state exactly once`() = testScope.runTest {
        val states = mutableListOf<TvConnectionState>()
        every { activeTvSession.setConnectionState(any()) } answers {
            states += firstArg<TvConnectionState>()
        }
        coEvery { connectToTvUseCase(sampleDevice) } returns ConnectOutcome.Failed("Temporary network failure")

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)
        testScope.advanceTimeBy(60000L)

        coVerify(exactly = 5) { connectToTvUseCase(sampleDevice) }
        assertEquals(
            1,
            states.count { it is TvConnectionState.Failed && it.reason.startsWith("Connection dropped after") }
        )
        assertTrue(states.last() is TvConnectionState.Failed)
    }

    @Test
    fun `transient null followed by the same device controller does not cancel reconnect`() = testScope.runTest {
        val replacementController = mockk<ITvController>(relaxed = true)
        every { replacementController.device } returns sampleDevice.copy()
        val reconnectStarted = CompletableDeferred<Unit>()
        val finishReconnect = CompletableDeferred<ConnectOutcome>()
        coEvery { connectToTvUseCase(sampleDevice) } coAnswers {
            reconnectStarted.complete(Unit)
            finishReconnect.await()
        }

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)
        testScope.advanceTimeBy(1600L)
        assertTrue("reconnect attempt should be in progress", reconnectStarted.isCompleted)

        controllerFlow.value = null
        testScope.runCurrent()
        controllerFlow.value = replacementController
        finishReconnect.complete(ConnectOutcome.Connected)
        testScope.advanceUntilIdle()

        verify { activeTvSession.setConnectionState(TvConnectionState.Connected(sampleDevice)) }
    }

    @Test
    fun `removing reconnect target cancels before clearing controller and prevents another attempt`() = testScope.runTest {
        val reconnectStarted = CompletableDeferred<Unit>()
        val finishReconnect = CompletableDeferred<ConnectOutcome>()
        val lateController = mockk<ITvController>(relaxed = true)
        every { lateController.device } returns sampleDevice
        val states = mutableListOf<TvConnectionState>()
        val lifecycle = mutableListOf<String>()
        every { mockController.connectionState } returns MutableStateFlow(TvConnectionState.Connected(sampleDevice))
        every { activeTvSession.setConnectionState(any()) } answers {
            states += firstArg<TvConnectionState>()
        }
        coEvery { connectToTvUseCase(sampleDevice) } coAnswers {
            reconnectStarted.complete(Unit)
            val outcome = finishReconnect.await()
            if (outcome is ConnectOutcome.Connected) {
                activeTvSession.setActiveController(lateController)
            }
            outcome
        }

        reconnectWatcher.onCommandResult(false)
        reconnectWatcher.onCommandResult(false)
        testScope.advanceTimeBy(1600L)
        assertTrue("reconnect attempt should be in progress", reconnectStarted.isCompleted)

        val deviceRepository = mockk<ITvDeviceRepository>(relaxed = true)
        coEvery { deviceRepository.getDeviceById(sampleDevice.id) } returns sampleDevice
        coEvery { mockController.disconnect() } coAnswers { lifecycle += "disconnect" }
        every { activeTvSession.setActiveController(null) } answers {
            lifecycle += "clear"
            controllerFlow.value = null
        }
        val removeUseCase = RemoveDeviceUseCase(
            deviceRepository,
            activeTvSession,
            mockk<ITvCredentialStore>(relaxed = true),
            reconnectWatcher
        )

        removeUseCase(sampleDevice.id)
        finishReconnect.complete(ConnectOutcome.Connected)
        testScope.advanceUntilIdle()

        assertEquals(listOf("disconnect", "clear"), lifecycle)
        coVerify(exactly = 1) { connectToTvUseCase(sampleDevice) }
        verify(exactly = 0) { activeTvSession.setActiveController(lateController) }
        assertNull(controllerFlow.value)
        assertTrue(states.none { it is TvConnectionState.Connected || it is TvConnectionState.Failed })
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
            val controllerFlow = MutableStateFlow<ITvController?>(mockController)
            every { activeTvSession.currentController } returns controllerFlow
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
