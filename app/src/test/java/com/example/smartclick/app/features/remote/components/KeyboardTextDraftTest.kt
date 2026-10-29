package com.example.smartclick.app.features.remote.components

import com.example.smartclick.app.domain.model.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardTextDraftTest {
    @Test
    fun `typing successive text changes sends one complete Text command only on submit`() {
        var draft = KeyboardTextDraft()
        val commands = mutableListOf<RemoteCommand>()
        listOf("v", "vi", "vie", "viet", "việt", "việt ", "việt n", "việt na", "việt nam")
            .forEach { draft = draft.edit(it) }

        assertEquals(emptyList<RemoteCommand>(), commands)
        draft.submit { command -> commands.add(command) }

        assertEquals(listOf(RemoteCommand.Text("việt nam")), commands)
    }

    @Test
    fun `submitting an empty draft sends nothing`() {
        val commands = mutableListOf<RemoteCommand>()
        KeyboardTextDraft().submit { command -> commands.add(command) }
        assertEquals(emptyList<RemoteCommand>(), commands)
    }
}
