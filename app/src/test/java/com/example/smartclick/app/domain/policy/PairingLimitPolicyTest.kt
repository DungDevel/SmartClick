package com.example.smartclick.app.domain.policy

import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.model.TvDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingLimitPolicyTest {

    private val policy = PairingLimitPolicy()

    @Test
    fun `evaluate returns Allowed when user is PREMIUM regardless of paired devices count`() {
        // Arrange
        val plan = SubscriptionPlan.PREMIUM
        val pairedDevices = listOf(
            TvDevice("1", "TV 1", "ip1", "mac1", isPaired = true),
            TvDevice("2", "TV 2", "ip2", "mac2", isPaired = true)
        )

        // Act
        val result = policy.evaluate(plan, pairedDevices)

        // Assert
        assertEquals(PairingResult.Allowed, result)
    }

    @Test
    fun `evaluate returns LimitExceeded when user is FREE and already has 1 paired device`() {
        // Arrange
        val plan = SubscriptionPlan.FREE
        val pairedDevices = listOf(
            TvDevice("1", "TV 1", "ip1", "mac1", isPaired = true)
        )

        // Act
        val result = policy.evaluate(plan, pairedDevices)

        // Assert
        assertEquals(PairingResult.LimitExceeded, result)
    }

    @Test
    fun `evaluate returns Allowed when user is FREE and has 0 paired devices`() {
        // Arrange
        val plan = SubscriptionPlan.FREE
        val pairedDevices = emptyList<TvDevice>()

        // Act
        val result = policy.evaluate(plan, pairedDevices)

        // Assert
        assertEquals(PairingResult.Allowed, result)
    }
}
