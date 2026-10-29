package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.model.TvCapability

internal data class RemoteSecondaryActions(
    val keyboard: Boolean,
    val dictation: Boolean,
    val headphone: Boolean
) {
    companion object {
        fun forCapabilities(capabilities: Set<TvCapability>, speechAvailable: Boolean) = RemoteSecondaryActions(
            keyboard = TvCapability.KEYBOARD_TEXT in capabilities,
            dictation = TvCapability.KEYBOARD_TEXT in capabilities && speechAvailable,
            headphone = TvCapability.HEADPHONE_MODE in capabilities
        )
    }
}
