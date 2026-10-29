package com.example.smartclick.app.features.onboarding

import com.example.smartclick.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportEmailRequestTest {

    @Test
    fun `blank support email does not open intent and reports unavailable channel`() {
        var openedMailto: String? = null
        var shownMessage: Int? = null

        SupportEmailRequest.dispatch(
            supportEmail = "  ",
            unavailableMessageResourceId = R.string.support_channel_unavailable,
            openMailto = { openedMailto = it },
            showUnavailable = { shownMessage = it }
        )

        assertNull(openedMailto)
        assertEquals(R.string.support_channel_unavailable, shownMessage)
    }

    @Test
    fun `configured support email opens correct mailto uri`() {
        var openedMailto: String? = null
        var shownMessage: Int? = null

        SupportEmailRequest.dispatch(
            supportEmail = "help@example.com",
            unavailableMessageResourceId = R.string.support_channel_unavailable,
            openMailto = { openedMailto = it },
            showUnavailable = { shownMessage = it }
        )

        assertEquals("mailto:help@example.com", openedMailto)
        assertNull(shownMessage)
    }
}
