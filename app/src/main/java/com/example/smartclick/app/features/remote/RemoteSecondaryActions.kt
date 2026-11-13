package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.model.TvCapability

internal data class RemoteSecondaryActions(
    val keyboard: Boolean,
    val dictation: Boolean,
    val search: Boolean
) {
    companion object {
        fun forCapabilities(capabilities: Set<TvCapability>, speechAvailable: Boolean) = RemoteSecondaryActions(
            keyboard = true,
            dictation = true,
            search = true
        )
    }
}

internal fun handleExtraButtonAction(
    capabilities: Set<TvCapability>,
    onAction: () -> Unit,
    onNotSupported: () -> Unit
) {
    if (TvCapability.KEYBOARD_TEXT in capabilities) {
        onAction()
    } else {
        onNotSupported()
    }
}
