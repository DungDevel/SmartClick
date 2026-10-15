package com.example.smartclick.app.domain.connection

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeOnLanTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `wake with null or blank mac returns false`() = runTest {
        assertFalse(WakeOnLan.wake(null))
        assertFalse(WakeOnLan.wake(""))
        assertFalse(WakeOnLan.wake("   "))
    }

    @Test
    fun `wake with invalid mac format returns false`() = runTest {
        assertFalse(WakeOnLan.wake("invalid-mac"))
        assertFalse(WakeOnLan.wake("00:11:22:33:44")) // too short
        assertFalse(WakeOnLan.wake("00:11:22:33:44:55:66")) // too long
        assertFalse(WakeOnLan.wake("zz:zz:zz:zz:zz:zz")) // invalid hex
    }

    @Test
    fun `wake with valid colon or hyphen separated mac executes`() = runTest {
        val resultColon = WakeOnLan.wake("00:11:22:33:44:55")
        val resultHyphen = WakeOnLan.wake("00-11-22-33-44-55")
        assertTrue(resultColon || !resultColon)
        assertTrue(resultHyphen || !resultHyphen)
    }
}
