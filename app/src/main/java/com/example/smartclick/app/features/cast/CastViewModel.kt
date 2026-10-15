package com.example.smartclick.app.features.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.repository.IMediaRepository
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.cast.CastSessionInfo
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.usecase.CastMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CastUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val selectedMediaIds: Set<String> = emptySet(),
    val isCasting: Boolean = false,
    val activeTvName: String? = null,
    val playbackState: CastPlaybackState = CastPlaybackState.IDLE,
    val sessionInfo: CastSessionInfo = CastSessionInfo()
)

@HiltViewModel
class CastViewModel @Inject constructor(
    private val mediaRepository: IMediaRepository,
    private val activeTvSession: IActiveTvSession,
    private val castMediaUseCase: CastMediaUseCase
) : ViewModel() {

    private val _selectedMediaIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isCasting = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionState: StateFlow<TvConnectionState> = activeTvSession.connectionState

    val activeDevice: StateFlow<TvDevice?> = activeTvSession.currentController
        .map { it?.device }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val playbackState: StateFlow<CastPlaybackState> = activeTvSession.currentController
        .flatMapLatest { controller ->
            (controller as? ICastController)?.castPlaybackState ?: flowOf(CastPlaybackState.IDLE)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CastPlaybackState.IDLE
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionInfo: StateFlow<CastSessionInfo> = activeTvSession.currentController
        .flatMapLatest { controller ->
            (controller as? ICastController)?.castSessionInfo ?: flowOf(CastSessionInfo())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CastSessionInfo()
        )

    val uiState: StateFlow<CastUiState> = combine(
        mediaRepository.getLocalMedia(),
        _selectedMediaIds,
        _isCasting,
        activeDevice,
        playbackState
    ) { media, selected, casting, device, playback ->
        Triple(Triple(media, selected, casting), device, playback)
    }.combine(sessionInfo) { triple, session ->
        val media = triple.first.first
        val selected = triple.first.second
        val casting = triple.first.third
        val device = triple.second
        val playback = triple.third
        CastUiState(
            mediaItems = media,
            selectedMediaIds = selected,
            isCasting = casting,
            activeTvName = device?.name,
            playbackState = playback,
            sessionInfo = session
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CastUiState()
    )

    fun toggleSelection(mediaId: String) {
        val current = _selectedMediaIds.value
        _selectedMediaIds.value = if (current.contains(mediaId)) {
            current - mediaId
        } else {
            current + mediaId
        }
    }

    fun clearSelection() {
        _selectedMediaIds.value = emptySet()
    }

    fun startCasting() {
        val selectedIds = _selectedMediaIds.value
        if (selectedIds.isNotEmpty()) {
            viewModelScope.launch {
                val mediaList = mediaRepository.getLocalMedia().first()
                val targetMedia = mediaList.firstOrNull { selectedIds.contains(it.id) }
                if (targetMedia != null) {
                    _isCasting.value = true
                    val result = castMediaUseCase(targetMedia)
                    if (result.isFailure) {
                        _isCasting.value = false
                    }
                    _selectedMediaIds.value = emptySet()
                }
            }
        }
    }

    fun stopCasting() {
        viewModelScope.launch {
            val controller = activeTvSession.currentController.value
            (controller as? ICastController)?.teardown()
            _isCasting.value = false
        }
    }

    fun play() {
        viewModelScope.launch {
            val controller = activeTvSession.currentController.value as? ICastController
            controller?.controlCast(CastCommand.Play)
        }
    }

    fun pause() {
        viewModelScope.launch {
            val controller = activeTvSession.currentController.value as? ICastController
            controller?.controlCast(CastCommand.Pause)
        }
    }

    fun stop() {
        stopCasting()
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            val controller = activeTvSession.currentController.value as? ICastController
            controller?.controlCast(CastCommand.SeekTo(positionMs))
        }
    }
}
