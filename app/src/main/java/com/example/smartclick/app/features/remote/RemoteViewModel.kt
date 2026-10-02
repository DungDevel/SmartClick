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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RemoteUiEvent {
    object CommandNotSupported : RemoteUiEvent()
    object CommandNotConnected : RemoteUiEvent()
}

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val sendCommandUseCase: SendCommandUseCase,
    private val activeTvSession: IActiveTvSession,
    settingsRepository: ISettingsRepository,
    private val wifiNetworkProvider: IWifiNetworkProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<RemoteUiEvent>(extraBufferCapacity = 1)
    val uiEvents = _uiEvents.asSharedFlow()

    private var lastToastTime = 0L

    val activeMode: StateFlow<RemoteMode> = savedStateHandle.getStateFlow(
        "remote_mode",
        RemoteMode.DPAD
    )

    fun setMode(mode: RemoteMode) {
        savedStateHandle["remote_mode"] = mode
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
                    else -> {}
                }
            }
        }
    }
}
