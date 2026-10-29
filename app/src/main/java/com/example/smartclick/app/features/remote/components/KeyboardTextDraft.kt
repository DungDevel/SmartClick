package com.example.smartclick.app.features.remote.components

import com.example.smartclick.app.domain.model.RemoteCommand

internal data class KeyboardTextDraft(val text: String = "") {
    fun edit(newText: String): KeyboardTextDraft = copy(text = newText)

    fun submit(sendCommand: (RemoteCommand) -> Unit) {
        if (text.isNotEmpty()) sendCommand(RemoteCommand.Text(text))
    }
}
