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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RemoteUiEvent {
    object CommandNotSupported : RemoteUiEvent()
    object CommandNotConnected : RemoteUiEvent()
    object CommandFailed : RemoteUiEvent()
}

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val sendCommandUseCase: SendCommandUseCase,
    private val activeTvSession: IActiveTvSession,
    private val settingsRepository: ISettingsRepository,
    private val wifiNetworkProvider: IWifiNetworkProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<RemoteUiEvent>(extraBufferCapacity = 1)
    val uiEvents = _uiEvents.asSharedFlow()

    private var lastToastTime = 0L

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

    /** Current WiFi state — UI can show a banner when WiFi drops. */
    val networkState: StateFlow<NetworkState> = wifiNetworkProvider.networkState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = wifiNetworkProvider.getCurrentState()
        )

    fun sendCommand(command: RemoteCommand) {
        viewModelScope.launch {
            val result = sendCommandUseCase(command)
            val now = System.currentTimeMillis()
            if (now - lastToastTime > 2000L) { // Debounce toasts 2s
                when (result) {
                    SendCommandResult.NOT_SUPPORTED -> {
                        lastToastTime = now
                        _uiEvents.tryEmit(RemoteUiEvent.CommandNotSupported)
                    }
                    SendCommandResult.NOT_CONNECTED -> {
                        lastToastTime = now
                        _uiEvents.tryEmit(RemoteUiEvent.CommandNotConnected)
                    }
                    SendCommandResult.FAILED -> {
                        lastToastTime = now
                        _uiEvents.tryEmit(RemoteUiEvent.CommandFailed)
                    }
                    else -> {}
                }
            }
        }
    }
}
