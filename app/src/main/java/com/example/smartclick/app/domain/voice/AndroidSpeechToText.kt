package com.example.smartclick.app.domain.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechToTextEngine, RecognitionListener {

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    override fun startListening() {
        if (speechRecognizer == null) {
            initRecognizer()
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = SpeechState.Error("Speech recognition is not available on this device.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        _state.value = SpeechState.Listening
        speechRecognizer?.startListening(intent)
    }

    override fun stopListening() {
        if (_state.value is SpeechState.Listening || _state.value is SpeechState.Processing) {
            speechRecognizer?.stopListening()
            _state.value = SpeechState.Idle
        }
    }

    override fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _state.value = SpeechState.Idle
    }

    private fun initRecognizer() {
        val mainExecutor = android.os.Handler(android.os.Looper.getMainLooper())
        // Initialization must happen on main thread usually, but let's assume this is safe
        // to call or handled by DI. We will defer initialization.
        mainExecutor.post {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(this)
            } catch (e: Exception) {
                _state.value = SpeechState.Error("Failed to initialize speech recognizer.")
            }
        }
    }

    // --- RecognitionListener implementation ---

    override fun onReadyForSpeech(params: Bundle?) {
        _state.value = SpeechState.Listening
    }

    override fun onBeginningOfSpeech() {
        _state.value = SpeechState.Processing("")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Optional: Could be used for visual feedback (sound waves)
    }

    override fun onBufferReceived(buffer: ByteArray?) {
    }

    override fun onEndOfSpeech() {
        // Recognition is done, waiting for result
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
        _state.value = SpeechState.Error(errorMessage)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""

        if (text.isNotBlank()) {
            _state.value = SpeechState.Result(text)
        } else {
            _state.value = SpeechState.Error("No speech matched")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        if (text != null) {
            _state.value = SpeechState.Processing(text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
    }
}
