package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.usecase.SendCommandResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeldCommandRepeaterTest {
    @Test
    fun `held command sends immediately and repeats at configured interval`() = runTest {
        val sent = mutableListOf<Long>()
        val job = launchHeldCommandRepeater(isPressed = { true }) {
            sent += testScheduler.currentTime
            SendCommandResult.SUCCESS
        }

        runCurrent()
        advanceTimeBy(420)
        runCurrent()
        job.cancel()

        assertEquals(listOf(0L, 140L, 280L, 420L), sent)
    }

    @Test
    fun `release stops repeating`() = runTest {
        var pressed = true
        val sent = mutableListOf<Long>()
        val job = launchHeldCommandRepeater(isPressed = { pressed }) {
            sent += testScheduler.currentTime
            SendCommandResult.SUCCESS
        }
        runCurrent()
        advanceTimeBy(280)
        runCurrent()
        pressed = false
        advanceTimeBy(500)
        runCurrent()
        job.cancel()

        assertEquals(listOf(0L, 140L, 280L), sent)
    }

    @Test
    fun `non repeating command sends once while held`() = runTest {
        var sent = 0
        val job = launchHeldCommandRepeater(repeat = false, isPressed = { true }) {
            sent++
            SendCommandResult.SUCCESS
        }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        job.cancel()

        assertEquals(1, sent)
    }

    @Test
    fun `not connected result stops repeating`() = runTest {
        var sent = 0
        val job = launchHeldCommandRepeater(isPressed = { true }) {
            sent++
            SendCommandResult.NOT_CONNECTED
        }
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        job.cancel()

        assertEquals(1, sent)
    }
}
