package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TouchpadPointerMoveDispatcherTest {
    @Test
    fun `coalesces movement in a window without losing total delta`() = runTest {
        val sent = mutableListOf<RemoteCommand.PointerMove>()
        val dispatcher = TouchpadPointerMoveDispatcher(
            scope = backgroundScope,
            windowMs = 16,
            send = { sent += it },
            haptic = {}
        )

        dispatcher.onMove(1f, 2f)
        dispatcher.onMove(3f, 4f)
        dispatcher.onMove(-1f, 5f)
        runCurrent()
        advanceTimeBy(16)
        runCurrent()

        assertEquals(1, sent.size)
        assertEquals(RemoteCommand.PointerMove(3f, 11f), sent.single())
    }

    @Test
    fun `moves separated by half a window still coalesce`() = runTest {
        val sent = mutableListOf<RemoteCommand.PointerMove>()
        val dispatcher = TouchpadPointerMoveDispatcher(
            scope = backgroundScope,
            send = { sent += it },
            haptic = {}
        )

        dispatcher.onMove(1f, 0f)
        advanceTimeBy(8)
        dispatcher.onMove(1f, 0f)
        advanceTimeBy(8)
        runCurrent()

        assertEquals(listOf(RemoteCommand.PointerMove(2f, 0f)), sent)
    }

    @Test
    fun `send failure does not prevent scheduling later movement`() = runTest {
        val sent = mutableListOf<RemoteCommand.PointerMove>()
        var attempts = 0
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, _ -> }
        )
        val dispatcher = TouchpadPointerMoveDispatcher(
            scope = scope,
            send = {
                attempts++
                if (attempts == 1) error("first send fails")
                sent += it
            },
            haptic = {}
        )

        dispatcher.onMove(1f, 0f)
        advanceTimeBy(16)
        runCurrent()
        dispatcher.onMove(2f, 0f)
        advanceTimeBy(16)
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(listOf(RemoteCommand.PointerMove(2f, 0f)), sent)
    }

    @Test
    fun `sequential windows preserve movement order`() = runTest {
        val sent = mutableListOf<RemoteCommand.PointerMove>()
        val dispatcher = TouchpadPointerMoveDispatcher(backgroundScope, 16, { sent += it }, {})

        dispatcher.onMove(2f, 0f)
        advanceTimeBy(16)
        runCurrent()
        dispatcher.onMove(-5f, 0f)
        advanceTimeBy(16)
        runCurrent()

        assertEquals(listOf(RemoteCommand.PointerMove(2f, 0f), RemoteCommand.PointerMove(-5f, 0f)), sent)
    }

    @Test
    fun `haptics once per drag gesture`() = runTest {
        var haptics = 0
        val dispatcher = TouchpadPointerMoveDispatcher(backgroundScope, 16, {}, { haptics++ })

        dispatcher.onMove(1f, 0f)
        dispatcher.onMove(1f, 0f)
        dispatcher.onDragEnd()
        dispatcher.onMove(1f, 0f)
        dispatcher.onDragEnd()

        assertEquals(2, haptics)
    }

    @Test
    fun `drag end flushes pending delta`() = runTest {
        val sent = mutableListOf<RemoteCommand.PointerMove>()
        val dispatcher = TouchpadPointerMoveDispatcher(backgroundScope, 16, { sent += it }, {})

        dispatcher.onMove(7f, -3f)
        dispatcher.onMove(2f, 8f)
        dispatcher.onDragEnd()
        runCurrent()

        assertEquals(listOf(RemoteCommand.PointerMove(9f, 5f)), sent)
    }

    @Test
    fun `send failure with plain Job does not cancel scope`() = runTest {
        val sent = mutableListOf<RemoteCommand.PointerMove>()
        var attempts = 0
        val scope = CoroutineScope(
            Job() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, _ -> }
        )
        val dispatcher = TouchpadPointerMoveDispatcher(
            scope = scope,
            send = {
                attempts++
                if (attempts == 1) throw IllegalStateException("network error")
                sent += it
            },
            haptic = {}
        )

        dispatcher.onMove(1f, 0f)
        advanceTimeBy(16)
        runCurrent()
        // After first send failure, scope must still be active
        assertTrue("scope must be active after send error", scope.coroutineContext[Job]!!.isActive)
        dispatcher.onMove(2f, 0f)
        advanceTimeBy(16)
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(listOf(RemoteCommand.PointerMove(2f, 0f)), sent)
    }

    @Test
    fun `send failure in onDragEnd does not propagate and resets haptic`() = runTest {
        val sent = mutableListOf<RemoteCommand.PointerMove>()
        var haptics = 0
        var attempts = 0
        val dispatcher = TouchpadPointerMoveDispatcher(
            scope = backgroundScope,
            send = {
                attempts++
                if (attempts == 1) throw IllegalStateException("network error")
                sent += it
            },
            haptic = { haptics++ }
        )

        // First gesture: send throws on dragEnd flush
        dispatcher.onMove(1f, 0f)
        dispatcher.onDragEnd()   // flushPending throws; must not propagate

        // Second gesture: haptic should fire again (flag was reset)
        dispatcher.onMove(2f, 0f)
        advanceTimeBy(16)
        runCurrent()

        assertEquals(2, haptics)
        assertEquals(listOf(RemoteCommand.PointerMove(2f, 0f)), sent)
    }

    @Test
    fun `missing pointer capability keeps circular dpad fallback`() {
        assertEquals(RemotePadLayout.CIRCULAR_DPAD, RemotePadBehavior.layoutFor(setOf(TvCapability.DPAD)))
        assertTrue(RemotePadBehavior.navigationEnabled(true, false, setOf(TvCapability.DPAD)))
    }
}
