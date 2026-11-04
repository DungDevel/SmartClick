package com.example.smartclick.app.features.settings

import com.example.smartclick.app.domain.model.PricingPlan
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: ISubscriptionRepository
    private lateinit var viewModel: PremiumViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.billingMessages } returns MutableSharedFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPlans when repository returns empty list sets error and isLoading false`() = runTest {
        coEvery { repository.availablePlans() } returns emptyList()

        viewModel = PremiumViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.plans.isEmpty())
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }

    @Test
    fun `loadPlans when repository returns plans sets plans and clears error`() = runTest {
        val testPlans = listOf(
            PricingPlan("annual", "Annual", "$19.99", "per year", isBestValue = true)
        )
        coEvery { repository.availablePlans() } returns testPlans

        viewModel = PremiumViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(testPlans, state.plans)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }
}
