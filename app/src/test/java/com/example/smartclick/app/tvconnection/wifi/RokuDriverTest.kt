package com.example.smartclick.app.tvconnection.wifi

import com.example.smartclick.app.domain.model.TvCapability
import org.junit.Assert.assertTrue
import org.junit.Test

class RokuDriverTest {

    @Test
    fun `capabilities should include OPEN_SEARCH_UI`() {
        assertTrue(
            "Roku capability should contain OPEN_SEARCH_UI",
            RokuDriver.ROKU_CAPABILITIES.contains(TvCapability.OPEN_SEARCH_UI)
        )
    }
}
