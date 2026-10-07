package com.example.smartclick.app.domain.model.voice

import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.voice.AppSymbol

/**
 * Represents confidence level of the Voice Intent parsing.
 */
enum class Confidence(val score: Int) {
    HIGH(100),
    MEDIUM(75),
    LOW(50),
    NONE(0)
}

/**
 * Represents the parsed user intent from a voice command.
 */
sealed class VoiceIntent {
    abstract val confidence: Confidence
    abstract val rawInput: String

    data class TurnOn(override val rawInput: String, override val confidence: Confidence = Confidence.HIGH) : VoiceIntent()
    data class TurnOff(override val rawInput: String, override val confidence: Confidence = Confidence.HIGH) : VoiceIntent()

    data class MuteToggle(override val rawInput: String, override val confidence: Confidence = Confidence.HIGH) : VoiceIntent()
    data class VolumeUp(override val rawInput: String, val steps: Int = 1, override val confidence: Confidence = Confidence.HIGH) : VoiceIntent()
    data class VolumeDown(override val rawInput: String, val steps: Int = 1, override val confidence: Confidence = Confidence.HIGH) : VoiceIntent()

    data class OpenApp(val app: AppSymbol, override val rawInput: String, override val confidence: Confidence = Confidence.HIGH) : VoiceIntent()
    data class Navigation(val command: RemoteCommand, override val rawInput: String, override val confidence: Confidence = Confidence.HIGH) : VoiceIntent()

    data class SearchContent(val query: String, override val rawInput: String, override val confidence: Confidence = Confidence.HIGH) : VoiceIntent()

    /**
     * Used when the parser fails to understand the command.
     */
    data class Unrecognized(override val rawInput: String, override val confidence: Confidence = Confidence.NONE) : VoiceIntent()
}
