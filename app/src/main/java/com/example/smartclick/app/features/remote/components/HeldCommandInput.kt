package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.usecase.SendCommandResult
import com.example.smartclick.app.features.remote.launchHeldCommandRepeater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancelAndJoin

internal fun Modifier.heldCommandInput(
    command: RemoteCommand,
    enabled: Boolean,
    onPressStart: () -> Unit,
    sendCommand: suspend (RemoteCommand) -> SendCommandResult
): Modifier = pointerInput(enabled, command, sendCommand) {
    detectTapGestures(onPress = {
        if (!enabled) return@detectTapGestures
        onPressStart()
        var pressed = true
        val repeatJob = CoroutineScope(currentCoroutineContext()).launchHeldCommandRepeater(
            isPressed = { pressed && enabled },
            sendCommand = { sendCommand(command) }
        )
        try {
            tryAwaitRelease()
        } finally {
            pressed = false
            repeatJob.cancelAndJoin()
        }
    })
}
