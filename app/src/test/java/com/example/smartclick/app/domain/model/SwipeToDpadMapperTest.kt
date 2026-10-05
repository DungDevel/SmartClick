package com.example.smartclick.app.domain.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SwipeToDpadMapperTest {

    private lateinit var classUnderTest: SwipeToDpadMapper

    @Before
    fun setup() {
        classUnderTest = SwipeToDpadMapper(thresholdPx = 100f, cooldownMs = 150L)
    }

    @Test
    fun `ignores small movements below threshold`() {
        val result = classUnderTest.onMove(50f, 0f, 0L)
        assertNull(result)
        assertEquals(50f, classUnderTest.fallbackDx)
    }

    @Test
    fun `emits right command when dx exceeds threshold`() {
        val result = classUnderTest.onMove(101f, 0f, 200L)
        assertEquals(RemoteCommand.DPadRight, result)
        assertEquals(0f, classUnderTest.fallbackDx)
    }

    @Test
    fun `respects cooldown period`() {
        classUnderTest.onMove(101f, 0f, 200L)
        val secondMove = classUnderTest.onMove(101f, 0f, 250L) // Under cooldown
        assertNull(secondMove)
        assertEquals(101f, classUnderTest.fallbackDx)

        val thirdMove = classUnderTest.onMove(1f, 0f, 400L) // After cooldown
        assertEquals(RemoteCommand.DPadRight, thirdMove)
    }

    @Test
    fun `picks major axis for diagonal movement`() {
        val result1 = classUnderTest.onMove(101f, 50f, 200L)
        assertEquals(RemoteCommand.DPadRight, result1)
        
        classUnderTest.onDragEnd()
        
        val result2 = classUnderTest.onMove(50f, 101f, 400L)
        assertEquals(RemoteCommand.DPadDown, result2)
    }
}
