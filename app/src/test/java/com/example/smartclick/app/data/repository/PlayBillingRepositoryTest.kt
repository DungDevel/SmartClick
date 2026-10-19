package com.example.smartclick.app.data.repository

import android.content.Context
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.example.smartclick.app.domain.model.SubscriptionPlan

class PlayBillingRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk(relaxed = true)
        every { context.getString(any()) } returns "Mocked Message"
        every { context.getString(any(), any()) } returns "Mocked Message"
    }

    @Test
    fun `subscription plan enum values are correct`() {
        assertEquals(2, SubscriptionPlan.values().size)
        assertTrue(SubscriptionPlan.valueOf("FREE") == SubscriptionPlan.FREE)
        assertTrue(SubscriptionPlan.valueOf("PREMIUM") == SubscriptionPlan.PREMIUM)
    }
}
