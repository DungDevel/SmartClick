package com.example.smartclick.app.features.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.repository.IMediaRepository
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class CastUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val selectedMediaIds: Set<String> = emptySet(),
    val isCasting: Boolean = false,
    val activeTvName: String? = null
)

@HiltViewModel
class CastViewModel @Inject constructor(
    private val mediaRepository: IMediaRepository,
    private val activeTvSession: IActiveTvSession
) : ViewModel() {

    private val _selectedMediaIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isCasting = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionState: StateFlow<TvConnectionState> = activeTvSession.connectionState
        
    val activeDevice: StateFlow<TvDevice?> = activeTvSession.currentController
        .map { it?.device }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val uiState: StateFlow<CastUiState> = combine(
        mediaRepository.getLocalMedia(),
        _selectedMediaIds,
        _isCasting,
        activeDevice
    ) { media, selected, casting, device ->
        CastUiState(
            mediaItems = media,
            selectedMediaIds = selected,
            isCasting = casting,
            activeTvName = device?.name
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
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
        if (_selectedMediaIds.value.isNotEmpty()) {
            _isCasting.value = true
            _selectedMediaIds.value = emptySet()
            // TODO(P4): Gửi lệnh cast media qua ITvController
        }
    }

    fun stopCasting() {
        _isCasting.value = false
        // TODO(P4): Dừng cast
    }
}


