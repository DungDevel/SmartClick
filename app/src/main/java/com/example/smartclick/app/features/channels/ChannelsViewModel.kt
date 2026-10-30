package com.example.smartclick.app.features.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartclick.app.domain.model.AppLaunchConfig
import com.example.smartclick.app.domain.model.ChannelCategory
import com.example.smartclick.app.domain.model.TvChannel
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.repository.IChannelRegistryRepository
import com.example.smartclick.app.domain.usecase.GetInstalledChannelsUseCase
import com.example.smartclick.app.domain.usecase.InstalledChannelsState
import com.example.smartclick.app.domain.usecase.LaunchAppOnTvUseCase
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelsUiState(
    val selectedTab: ChannelCategory = ChannelCategory.POPULAR,
    val appsState: InstalledChannelsState = InstalledChannelsState.Loading,
    val channels: List<TvChannel> = emptyList(),
    val activeTvName: String? = null
)

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val channelRepository: IChannelRegistryRepository,
    private val activeTvSession: IActiveTvSession,
    private val launchAppOnTvUseCase: LaunchAppOnTvUseCase,
    private val getInstalledChannelsUseCase: GetInstalledChannelsUseCase
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ChannelCategory.POPULAR)
    private val _appsState = MutableStateFlow<InstalledChannelsState>(InstalledChannelsState.Loading)

    init {
        viewModelScope.launch {
            activeTvSession.currentController.drop(1).collect { refreshApps() }
        }
        refreshApps()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionState: StateFlow<TvConnectionState> = activeTvSession.connectionState
    val activeDevice: StateFlow<TvDevice?> = activeTvSession.currentController
        .map { it?.device }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChannelsUiState> = combine(
        _selectedTab,
        _selectedTab.flatMapLatest { tab -> channelRepository.getTvChannels(tab) },
        activeDevice,
        _appsState
    ) { tab, channels, device, appsState ->
        ChannelsUiState(
            selectedTab = tab,
            appsState = appsState,
            channels = channels,
            activeTvName = device?.name
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChannelsUiState()
    )

    fun onTabSelected(tab: ChannelCategory) {
        _selectedTab.value = tab
    }

    fun launchApp(appId: String) {
        viewModelScope.launch {
            launchAppOnTvUseCase(appId)
        }
    }

    fun retryApps() = refreshApps()

    private fun refreshApps() {
        viewModelScope.launch {
            _appsState.value = InstalledChannelsState.Loading
            _appsState.value = getInstalledChannelsUseCase()
        }
    }

    fun tuneChannel(channelId: String) {
        // TODO(P8): Map to ITvConnectionManager tuneChannel command
    }
}


