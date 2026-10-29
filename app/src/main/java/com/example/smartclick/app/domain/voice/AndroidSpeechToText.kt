package com.example.smartclick.app.domain.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.smartclick.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class AndroidSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechToTextEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()
    override val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null
    private var sessionId = 0L
    private var timeout: Runnable? = null

    override fun startListening() {
        mainHandler.post { beginSession() }
    }

    private fun beginSession() {
        destroyRecognizer()
        _state.value = SpeechState.Idle
        val id = ++sessionId
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = SpeechState.Error(context.getString(R.string.voice_error_service_unavailable))
            return
        }

        try {
            val listener = SessionListener(id)
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
            }
            val locale = SpeechLocaleSelector.forLanguageTag(
                context.resources.configuration.locales[0]?.toLanguageTag().orEmpty()
            )
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            _state.value = SpeechState.Listening
            scheduleTimeout(id)
            recognizer?.startListening(intent)
        } catch (_: SecurityException) {
            finishSession(id, SpeechState.Error(context.getString(R.string.voice_error_permission_denied)))
        } catch (_: Exception) {
            finishSession(id, SpeechState.Error(context.getString(R.string.voice_error_generic)))
        }
    }

    override fun stopListening() {
        mainHandler.post {
            val current = recognizer ?: return@post
            try {
                current.stopListening()
                scheduleTimeout(sessionId, STOP_TIMEOUT_MS)
            } catch (_: Exception) {
                finishSession(sessionId, SpeechState.Error(context.getString(R.string.voice_error_generic)))
            }
        }
    }

    override fun destroy() {
        mainHandler.post {
            sessionId++
            destroyRecognizer()
            _state.value = SpeechState.Idle
        }
    }

    private fun scheduleTimeout(id: Long, delayMs: Long = LISTENING_TIMEOUT_MS) {
        timeout?.let(mainHandler::removeCallbacks)
        timeout = Runnable {
            if (id == sessionId) {
                finishSession(id, SpeechState.Error(context.getString(R.string.voice_error_no_speech)))
            }
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun finishSession(id: Long, finalState: SpeechState) {
        if (id != sessionId) return
        destroyRecognizer()
        _state.value = finalState
    }

    private fun destroyRecognizer() {
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        recognizer = null
    }

    private inner class SessionListener(private val id: Long) : RecognitionListener {
        private fun active() = id == sessionId

        override fun onReadyForSpeech(params: Bundle?) {
            if (active()) _state.value = SpeechState.Listening
        }

        override fun onBeginningOfSpeech() {
            if (active()) _state.value = SpeechState.Processing("")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (!active()) return
            val messageRes = when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_error_permission_denied
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> R.string.voice_error_network
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_AUDIO -> R.string.voice_error_no_speech
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_error_busy
                else -> R.string.voice_error_generic
            }
            finishSession(id, SpeechState.Error(context.getString(messageRes)))
        }

        override fun onResults(results: Bundle?) {
            if (!active()) return
            val result = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            val state = if (result.isNullOrBlank()) {
                SpeechState.Error(context.getString(R.string.voice_error_no_speech))
            } else {
                SpeechState.Result(result)
            }
            finishSession(id, state)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!active()) return
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.let { _state.value = SpeechState.Processing(it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val LISTENING_TIMEOUT_MS = 15_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
