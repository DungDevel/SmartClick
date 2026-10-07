package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.voice.VoiceIntent
import com.example.smartclick.app.domain.voice.AppRegistry
import javax.inject.Inject

enum class VoiceCommandResult {
    SUCCESS,
    NOT_CONNECTED,
    NOT_SUPPORTED,
    UNRECOGNIZED,
    FAILED
}

class ProcessVoiceCommandUseCase @Inject constructor(
    private val activeTvSession: IActiveTvSession,
    private val sendCommandUseCase: SendCommandUseCase,
    private val launchAppOnTvUseCase: LaunchAppOnTvUseCase,
    private val appRegistry: AppRegistry
) {
    suspend operator fun invoke(intent: VoiceIntent): VoiceCommandResult {
        val controller = activeTvSession.currentController.value ?: return VoiceCommandResult.NOT_CONNECTED
        val protocol = controller.device?.protocol ?: return VoiceCommandResult.NOT_CONNECTED
        val capabilities = controller.capabilities

        return when (intent) {
            is VoiceIntent.Unrecognized -> VoiceCommandResult.UNRECOGNIZED

            is VoiceIntent.TurnOn -> {
                mapResult(sendCommandUseCase(RemoteCommand.PowerOn))
            }
            is VoiceIntent.TurnOff -> {
                mapResult(sendCommandUseCase(RemoteCommand.PowerOff))
            }

            is VoiceIntent.MuteToggle -> {
                mapResult(sendCommandUseCase(RemoteCommand.Mute))
            }

            is VoiceIntent.VolumeUp -> {
                var success = true
                for (i in 0 until intent.steps) {
                    val r = sendCommandUseCase(RemoteCommand.VolumeUp)
                    if (r == SendCommandResult.NOT_SUPPORTED) return VoiceCommandResult.NOT_SUPPORTED
                    if (r != SendCommandResult.SUCCESS) success = false
                }
                if (success) VoiceCommandResult.SUCCESS else VoiceCommandResult.FAILED
            }

            is VoiceIntent.VolumeDown -> {
                var success = true
                for (i in 0 until intent.steps) {
                    val r = sendCommandUseCase(RemoteCommand.VolumeDown)
                    if (r == SendCommandResult.NOT_SUPPORTED) return VoiceCommandResult.NOT_SUPPORTED
                    if (r != SendCommandResult.SUCCESS) success = false
                }
                if (success) VoiceCommandResult.SUCCESS else VoiceCommandResult.FAILED
            }

            is VoiceIntent.Navigation -> {
                mapResult(sendCommandUseCase(intent.command))
            }

            is VoiceIntent.OpenApp -> {
                if (!capabilities.contains(TvCapability.INSTALLED_APPS)) {
                    return VoiceCommandResult.NOT_SUPPORTED
                }
                val appId = appRegistry.getAppId(intent.app, protocol)
                if (appId != null) {
                    val result = launchAppOnTvUseCase(appId)
                    if (result.isSuccess) VoiceCommandResult.SUCCESS else VoiceCommandResult.FAILED
                } else {
                    VoiceCommandResult.NOT_SUPPORTED
                }
            }

            is VoiceIntent.SearchContent -> {
                if (!capabilities.contains(TvCapability.SEARCH_CONTENT)) {
                    return VoiceCommandResult.NOT_SUPPORTED
                }
                val result = sendCommandUseCase(RemoteCommand.SearchContent(intent.query))
                mapResult(result)
            }
        }
    }

    private fun mapResult(result: SendCommandResult): VoiceCommandResult {
        return when (result) {
            SendCommandResult.SUCCESS -> VoiceCommandResult.SUCCESS
            SendCommandResult.FAILED -> VoiceCommandResult.FAILED
            SendCommandResult.NOT_SUPPORTED -> VoiceCommandResult.NOT_SUPPORTED
            SendCommandResult.NOT_CONNECTED -> VoiceCommandResult.NOT_CONNECTED
        }
    }
}
