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
}
