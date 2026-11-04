package com.example.smartclick.app.data.repository

import android.content.Context
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test
    fun `buildPlans with empty map returns empty list`() {
        val plans = buildPlans(emptyMap())
        assertTrue(plans.isEmpty())
    }

    @Test
    fun `buildPlans with only annual returns single annual plan without default dollar price`() {
        val map = mapOf("annual" to PlanPrice("annual", "$29.99", "per year", true))
        val plans = buildPlans(map)
        assertEquals(1, plans.size)
        assertEquals("annual", plans[0].id)
        assertEquals("Annual", plans[0].displayName)
        assertEquals("$29.99", plans[0].price)
        assertEquals("per year", plans[0].period)
        assertTrue(plans[0].isBestValue)
    }

    @Test
    fun `buildPlans with all three plans returns correct order and best value flag`() {
        val map = mapOf(
            "lifetime" to PlanPrice("lifetime", "$99.99", "one-time", false),
            "weekly" to PlanPrice("weekly", "$3.99", "per week", false),
            "annual" to PlanPrice("annual", "$24.99", "per year", true)
        )
        val plans = buildPlans(map)
        assertEquals(3, plans.size)
        assertEquals("weekly", plans[0].id)
        assertEquals("Weekly", plans[0].displayName)
        assertFalse(plans[0].isBestValue)

        assertEquals("annual", plans[1].id)
        assertEquals("Annual", plans[1].displayName)
        assertTrue(plans[1].isBestValue)

        assertEquals("lifetime", plans[2].id)
        assertEquals("Lifetime", plans[2].displayName)
        assertFalse(plans[2].isBestValue)
    }
}
