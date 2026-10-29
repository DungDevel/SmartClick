package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.model.TvCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteSecondaryActionsTest {
    @Test fun `keyboard and dictation are hidden without text capability`() {
        assertEquals(RemoteSecondaryActions(false, false, false), RemoteSecondaryActions.forCapabilities(emptySet(), true))
    }

    @Test fun `dictation requires both text capability and available speech`() {
        assertEquals(RemoteSecondaryActions(true, false, false), RemoteSecondaryActions.forCapabilities(setOf(TvCapability.KEYBOARD_TEXT), false))
        assertEquals(RemoteSecondaryActions(true, true, false), RemoteSecondaryActions.forCapabilities(setOf(TvCapability.KEYBOARD_TEXT), true))
    }

    @Test fun `headphone visibility requires its explicit capability`() {
        assertEquals(RemoteSecondaryActions(false, false, true), RemoteSecondaryActions.forCapabilities(setOf(TvCapability.HEADPHONE_MODE), false))
    }
}
