package com.example.smartclick.app.features.remote

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.repository.ISettingsRepository
import com.example.smartclick.app.domain.usecase.SendCommandUseCase
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.connection.NetworkState
import com.example.smartclick.app.domain.connection.IWifiNetworkProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val sendCommandUseCase: SendCommandUseCase,
    private val activeTvSession: IActiveTvSession,
    settingsRepository: ISettingsRepository,
    private val wifiNetworkProvider: IWifiNetworkProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

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
            sendCommandUseCase(command)
        }
    }
}


