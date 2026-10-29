package com.example.smartclick.app.features.remote

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.repository.ISettingsRepository
import com.example.smartclick.app.domain.usecase.SendCommandUseCase
import com.example.smartclick.app.domain.usecase.SendCommandResult
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.connection.NetworkState
import com.example.smartclick.app.domain.connection.IWifiNetworkProvider
import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.policy.FeatureGate
import com.example.smartclick.app.domain.policy.PremiumFeature
import com.example.smartclick.app.domain.usecase.ProcessVoiceCommandUseCase
import com.example.smartclick.app.domain.usecase.VoiceCommandResult
import com.example.smartclick.app.domain.voice.IntentParser
import com.example.smartclick.app.domain.voice.SpeechState
import com.example.smartclick.app.domain.voice.SpeechToTextEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.smartclick.app.domain.repository.ISubscriptionRepository

sealed class RemoteUiEvent {
    object CommandNotSupported : RemoteUiEvent()
    object CommandNotConnected : RemoteUiEvent()
    object CommandFailed : RemoteUiEvent()
    object WolSent : RemoteUiEvent()
    object VoiceNotSupportedError : RemoteUiEvent()
    object VoiceUnrecognizedError : RemoteUiEvent()
    data class VoiceError(val message: String) : RemoteUiEvent()
    object VoicePremiumRequired : RemoteUiEvent()
    object RequestVoicePermission : RemoteUiEvent()
    object RequestPowerOffConfirmation : RemoteUiEvent()
}

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val sendCommandUseCase: SendCommandUseCase,
    private val activeTvSession: IActiveTvSession,
    private val settingsRepository: ISettingsRepository,
    private val wifiNetworkProvider: IWifiNetworkProvider,
    private val speechToTextEngine: SpeechToTextEngine,
    private val intentParser: IntentParser,
    private val processVoiceCommandUseCase: ProcessVoiceCommandUseCase,
    private val featureGate: FeatureGate,
    private val subscriptionRepository: ISubscriptionRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<RemoteUiEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val uiEvents = _uiEvents.asSharedFlow()

    private var lastToastTime = 0L
    private var lastToastEvent: RemoteUiEvent? = null

    private fun emitThrottledEvent(event: RemoteUiEvent) {
        val now = System.currentTimeMillis()
        if (event != lastToastEvent || now - lastToastTime > 2000L) {
            lastToastTime = now
            lastToastEvent = event
            _uiEvents.tryEmit(event)
        }
    }

    val activeMode: StateFlow<RemoteMode> = settingsRepository.settings
        .map { settings ->
            try {
                RemoteMode.valueOf(settings.remoteMode)
            } catch (e: Exception) {
                RemoteMode.DPAD
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RemoteMode.DPAD
        )

    fun setMode(mode: RemoteMode) {
        viewModelScope.launch {
            settingsRepository.setRemoteMode(mode.name)
        }
    }

    private val _isLocked = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _muteState = MutableStateFlow(-1)
    val muteState: StateFlow<Int> = _muteState.asStateFlow()

    fun toggleScreenLock() {
        if (!_isLocked.value) {
            _isLocked.value = true
        }
    }

    fun unlockScreen() {
        _isLocked.value = false
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val connectionState: StateFlow<TvConnectionState> = activeTvSession.connectionState

    val activeDevice: StateFlow<TvDevice?> = activeTvSession.currentController
        .map { it?.device }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val capabilities: StateFlow<Set<com.example.smartclick.app.domain.model.TvCapability>> = activeTvSession.currentController
        .map { it?.capabilities ?: emptySet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val isHapticEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.hapticEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val speechState: StateFlow<SpeechState> = speechToTextEngine.state

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state !is TvConnectionState.Connected) {
                    _muteState.value = -1
                }
            }
        }
        viewModelScope.launch {
            speechToTextEngine.state.collect { state ->
                when (state) {
                    is SpeechState.Result -> processVoiceResult(state.text)
                    is SpeechState.Error -> {
                        _uiEvents.tryEmit(RemoteUiEvent.VoiceError(state.message))
                    }
                    else -> {}
                }
            }
        }
    }

    fun startListening() {
        viewModelScope.launch {
            startListeningIfAllowed()
        }
    }

    fun prepareVoiceListening(hasRecordAudioPermission: Boolean) {
        viewModelScope.launch {
            if (!isVoiceAllowed()) {
                _uiEvents.tryEmit(RemoteUiEvent.VoicePremiumRequired)
            } else if (hasRecordAudioPermission) {
                speechToTextEngine.startListening()
            } else {
                _uiEvents.tryEmit(RemoteUiEvent.RequestVoicePermission)
            }
        }
    }

    private suspend fun startListeningIfAllowed() {
        if (isVoiceAllowed()) speechToTextEngine.startListening()
        else _uiEvents.tryEmit(RemoteUiEvent.VoicePremiumRequired)
    }

    private suspend fun isVoiceAllowed(): Boolean {
        val userPlan = subscriptionRepository.currentPlan.first()
        return featureGate.isFeatureAllowed(PremiumFeature.VOICE, userPlan)
    }

    fun stopListening() {
        speechToTextEngine.stopListening()
    }

    fun cancelVoiceSession() {
        speechToTextEngine.destroy()
    }

    override fun onCleared() {
        speechToTextEngine.destroy()
        super.onCleared()
    }

    private suspend fun processVoiceResult(text: String) {
        val intent = intentParser.parse(text)
        if (intent is com.example.smartclick.app.domain.model.voice.VoiceIntent.TurnOff) {
            _uiEvents.tryEmit(RemoteUiEvent.RequestPowerOffConfirmation)
            return
        }
        val result = processVoiceCommandUseCase(intent)
        when (result) {
            VoiceCommandResult.NOT_CONNECTED -> _uiEvents.tryEmit(RemoteUiEvent.CommandNotConnected)
            VoiceCommandResult.NOT_SUPPORTED -> _uiEvents.tryEmit(RemoteUiEvent.CommandNotSupported)
            VoiceCommandResult.FAILED -> _uiEvents.tryEmit(RemoteUiEvent.CommandFailed)
            VoiceCommandResult.UNRECOGNIZED -> _uiEvents.tryEmit(RemoteUiEvent.VoiceUnrecognizedError)
            VoiceCommandResult.SUCCESS -> { /* Handled successfully */ }
        }
    }

    /** Current WiFi state — UI can show a banner when WiFi drops. */
    val networkState: StateFlow<NetworkState> = wifiNetworkProvider.networkState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = wifiNetworkProvider.getCurrentState()
        )

    fun sendCommand(command: RemoteCommand) {
        viewModelScope.launch {
            if (command == RemoteCommand.Power && connectionState.value !is TvConnectionState.Connected) {
                // Trigger Wake-on-LAN if device not connected but we have a MAC
                activeDevice.value?.macAddress?.let { mac ->
                    val success = com.example.smartclick.app.domain.connection.WakeOnLan.wake(mac)
                    if (success) {
                        emitThrottledEvent(RemoteUiEvent.WolSent)
                    } else {
                        emitThrottledEvent(RemoteUiEvent.CommandFailed)
                    }
                    return@launch
                }
            }

            sendCommandForHold(command)
        }
    }

    suspend fun sendCommandForHold(command: RemoteCommand): SendCommandResult {
        val result = sendCommandUseCase(command)
        if (result == SendCommandResult.SUCCESS) {
            when (command) {
                RemoteCommand.MuteOn -> _muteState.value = 1
                RemoteCommand.MuteOff -> _muteState.value = 0
                RemoteCommand.Mute -> _muteState.value = if (_muteState.value == 1) 0 else 1
                else -> {}
            }
        }
        when (result) {
            SendCommandResult.NOT_SUPPORTED -> emitThrottledEvent(RemoteUiEvent.CommandNotSupported)
            SendCommandResult.NOT_CONNECTED -> emitThrottledEvent(RemoteUiEvent.CommandNotConnected)
            SendCommandResult.FAILED -> emitThrottledEvent(RemoteUiEvent.CommandFailed)
            else -> {}
        }
        return result
    }
}
