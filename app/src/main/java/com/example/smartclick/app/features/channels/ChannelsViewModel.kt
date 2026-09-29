package com.example.smartclick.app.features.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartclick.app.domain.model.AppLaunchConfig
import com.example.smartclick.app.domain.model.ChannelCategory
import com.example.smartclick.app.domain.model.TvChannel
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.repository.IChannelRegistryRepository
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
    val apps: List<AppLaunchConfig> = emptyList(),
    val channels: List<TvChannel> = emptyList(),
    val activeTvName: String? = null
)

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val channelRepository: IChannelRegistryRepository,
    private val activeTvSession: IActiveTvSession,
    private val launchAppOnTvUseCase: LaunchAppOnTvUseCase
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ChannelCategory.POPULAR)

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
        channelRepository.getSupportedChannels(),
        _selectedTab.flatMapLatest { tab -> channelRepository.getTvChannels(tab) },
        activeDevice
    ) { tab, apps, channels, device ->
        ChannelsUiState(
            selectedTab = tab,
            apps = apps,
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
            val result = launchAppOnTvUseCase(appId)
            result.onFailure {
                // TODO(P8): Show paywall if error is ChannelsFeatureRequiresPremium
            }
        }
    }

    fun tuneChannel(channelId: String) {
        // TODO(P8): Map to ITvConnectionManager tuneChannel command
    }
}


