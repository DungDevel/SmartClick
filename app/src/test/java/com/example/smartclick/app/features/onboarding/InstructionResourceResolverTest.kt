package com.example.smartclick.app.features.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionResourceResolverTest {

    @Test
    fun `missing instruction key uses fallback and logs warning`() {
        var warning: String? = null

        val resolved = InstructionResourceResolver.resolve(
            key = "missing_instruction_key",
            fallbackResourceId = 123,
            findResourceId = { 0 },
            warn = { warning = it }
        )

        assertEquals(123, resolved)
        assertTrue(warning.orEmpty().contains("missing_instruction_key"))
    }
}
