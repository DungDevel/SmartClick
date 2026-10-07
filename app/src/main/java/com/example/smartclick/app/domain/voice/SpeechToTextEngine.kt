package com.example.smartclick.app.domain.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * High-level state representing the speech recognition process.
 */
sealed class SpeechState {
    object Idle : SpeechState()
    object Listening : SpeechState()
    data class Processing(val partialText: String) : SpeechState()
    data class Result(val text: String) : SpeechState()
    data class Error(val message: String) : SpeechState()
}

/**
 * Defines a contract for a Speech-to-Text engine.
 * Implementing classes could be Google's SpeechRecognizer, or an external API.
 */
interface SpeechToTextEngine {
    val state: StateFlow<SpeechState>

    fun startListening()
    fun stopListening()
    fun destroy()
}
