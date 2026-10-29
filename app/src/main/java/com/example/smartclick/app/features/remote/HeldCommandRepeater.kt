package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.usecase.SendCommandResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val HELD_COMMAND_REPEAT_INTERVAL_MS = 140L

internal fun isRepeatableHeldCommand(command: com.example.smartclick.app.domain.model.RemoteCommand): Boolean =
    command is com.example.smartclick.app.domain.model.RemoteCommand.DPadUp ||
        command is com.example.smartclick.app.domain.model.RemoteCommand.DPadDown ||
        command is com.example.smartclick.app.domain.model.RemoteCommand.DPadLeft ||
        command is com.example.smartclick.app.domain.model.RemoteCommand.DPadRight ||
        command is com.example.smartclick.app.domain.model.RemoteCommand.VolumeUp ||
        command is com.example.smartclick.app.domain.model.RemoteCommand.VolumeDown ||
        command is com.example.smartclick.app.domain.model.RemoteCommand.ChannelUp ||
        command is com.example.smartclick.app.domain.model.RemoteCommand.ChannelDown

internal fun CoroutineScope.launchHeldCommandRepeater(
    repeat: Boolean = true,
    intervalMs: Long = HELD_COMMAND_REPEAT_INTERVAL_MS,
    isPressed: () -> Boolean,
    sendCommand: suspend () -> SendCommandResult
): Job = launch {
    while (currentCoroutineContext().isActive && isPressed()) {
        when (sendCommand()) {
            SendCommandResult.SUCCESS, SendCommandResult.FAILED -> Unit
            SendCommandResult.NOT_SUPPORTED, SendCommandResult.NOT_CONNECTED -> return@launch
        }
        if (!repeat) return@launch
        delay(intervalMs)
    }
}
