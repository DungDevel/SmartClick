package com.example.smartclick.app.features.cast

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class CastLifecycleTest {

    @Test
    fun `LifecycleEventObserver on ON_STOP calls pausePlaylist without stopping casting`() {
        val viewModel = mockk<CastViewModel>(relaxed = true)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.pausePlaylist()
            }
        }

        observer.onStateChanged(mockk(relaxed = true), Lifecycle.Event.ON_STOP)

        verify { viewModel.pausePlaylist() }
        verify(exactly = 0) { viewModel.stopCasting() }
    }

    @Test
    fun `LifecycleEventObserver on ON_START does not call resumePlaylist`() {
        val viewModel = mockk<CastViewModel>(relaxed = true)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.pausePlaylist()
            }
        }

        observer.onStateChanged(mockk(relaxed = true), Lifecycle.Event.ON_START)

        verify(exactly = 0) { viewModel.resumePlaylist() }
    }

    @Test
    fun `onDispose calls stopCasting`() {
        val viewModel = mockk<CastViewModel>(relaxed = true)
        viewModel.stopCasting()
        verify { viewModel.stopCasting() }
    }
}
