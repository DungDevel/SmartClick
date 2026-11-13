package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.model.TvCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteSecondaryActionsTest {
    @Test fun `keyboard and dictation are always enabled while search is available`() {
        assertEquals(RemoteSecondaryActions(true, true, true), RemoteSecondaryActions.forCapabilities(emptySet(), true))
    }

    @Test fun `keyboard and dictation remain enabled with capabilities and speech availability`() {
        assertEquals(RemoteSecondaryActions(true, true, true), RemoteSecondaryActions.forCapabilities(setOf(TvCapability.KEYBOARD_TEXT), false))
        assertEquals(RemoteSecondaryActions(true, true, true), RemoteSecondaryActions.forCapabilities(setOf(TvCapability.KEYBOARD_TEXT), true))
    }

    @Test fun `click decision executes action when text capability present`() {
        var actionCalled = false
        var toastCalled = false
        val capabilities = setOf(TvCapability.KEYBOARD_TEXT)

        handleExtraButtonAction(capabilities, onAction = { actionCalled = true }, onNotSupported = { toastCalled = true })

        assertEquals(true, actionCalled)
        assertEquals(false, toastCalled)
    }

    @Test fun `click decision triggers not supported when text capability absent`() {
        var actionCalled = false
        var toastCalled = false
        val capabilities = emptySet<TvCapability>()

        handleExtraButtonAction(capabilities, onAction = { actionCalled = true }, onNotSupported = { toastCalled = true })

        assertEquals(false, actionCalled)
        assertEquals(true, toastCalled)
    }
}
