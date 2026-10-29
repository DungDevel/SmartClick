package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability

internal enum class RemotePadLayout { POINTER, CIRCULAR_DPAD }

internal enum class RemotePadAction { UP, DOWN, LEFT, RIGHT, OK }

internal object RemotePadBehavior {
    fun layoutFor(capabilities: Set<TvCapability>): RemotePadLayout =
        if (TvCapability.POINTER in capabilities) RemotePadLayout.POINTER else RemotePadLayout.CIRCULAR_DPAD

    fun navigationEnabled(
        connected: Boolean,
        locked: Boolean,
        capabilities: Set<TvCapability>
    ): Boolean = connected && !locked && TvCapability.DPAD in capabilities

    fun commandFor(action: RemotePadAction): RemoteCommand = when (action) {
        RemotePadAction.UP -> RemoteCommand.DPadUp
        RemotePadAction.DOWN -> RemoteCommand.DPadDown
        RemotePadAction.LEFT -> RemoteCommand.DPadLeft
        RemotePadAction.RIGHT -> RemoteCommand.DPadRight
        RemotePadAction.OK -> RemoteCommand.Ok
    }

    fun dispatchIfEnabled(
        action: RemotePadAction,
        enabled: Boolean,
        dispatch: (RemoteCommand) -> Unit
    ) {
        if (enabled) dispatch(commandFor(action))
    }
}
