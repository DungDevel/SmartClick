package com.example.smartclick.app.features.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartclick.app.domain.model.MediaItem
import android.net.Uri
import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.app.domain.model.CastMediaType
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.policy.FeatureGate
import com.example.smartclick.app.domain.policy.PremiumFeature
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
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
    val sessionInfo: CastSessionInfo = CastSessionInfo(),
    val requestState: CastRequestState = CastRequestState.IDLE
)

enum class CastRequestState { IDLE, PREPARING, SENT, ERROR }

sealed class CastUiEvent {
    data object OpenPicker : CastUiEvent()
    data object PremiumRequired : CastUiEvent()
    data object NoActiveDevice : CastUiEvent()
    data object NotConnected : CastUiEvent()
    data object NoVisualCapability : CastUiEvent()
    data object CapabilityMissing : CastUiEvent()
    data object UnsupportedFormat : CastUiEvent()
    data object MediaUnavailable : CastUiEvent()
    data class CastFailed(val presentation: CastErrorPresentation) : CastUiEvent()
}

@HiltViewModel
class CastViewModel @Inject constructor(
    private val mediaRepository: IMediaRepository,
    private val activeTvSession: IActiveTvSession,
    private val castMediaUseCase: CastMediaUseCase,
    private val featureGate: FeatureGate,
    private val subscriptionRepository: ISubscriptionRepository
) : ViewModel() {

    private val _selectedMediaIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isCasting = MutableStateFlow(false)
    private val _requestState = MutableStateFlow(CastRequestState.IDLE)
    private val _uiEvents = MutableSharedFlow<CastUiEvent>(extraBufferCapacity = 8)
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            var previous: com.example.smartclick.app.domain.connection.ITvController? = null
            var initialized = false
            activeTvSession.currentController.collect { next ->
                if (initialized && previous !== next) {
                    castMediaUseCase.teardown()
                    mediaRepository.clearSelectedMedia()
                    _selectedMediaIds.value = emptySet()
                    _isCasting.value = false
                    _requestState.value = CastRequestState.IDLE
                }
                previous = next
                initialized = true
            }
        }
    }

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
    val playbackState: StateFlow<CastPlaybackState> = castMediaUseCase.activeCastController
        .flatMapLatest { controller ->
            (controller as? ICastController)?.castPlaybackState ?: flowOf(CastPlaybackState.IDLE)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CastPlaybackState.IDLE
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionInfo: StateFlow<CastSessionInfo> = castMediaUseCase.activeCastController
        .flatMapLatest { controller ->
            (controller as? ICastController)?.castSessionInfo ?: flowOf(CastSessionInfo())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CastSessionInfo()
        )

    private val mediaSelectionState = combine(
        mediaRepository.getLocalMedia(),
        _selectedMediaIds,
        _isCasting
    ) { media, selected, casting -> Triple(media, selected, casting) }

    val uiState: StateFlow<CastUiState> = combine(
        mediaSelectionState, _requestState, activeDevice, playbackState
    ) { selection, request, device, playback ->
        CastUiState(
            mediaItems = selection.first,
            selectedMediaIds = selection.second,
            isCasting = selection.third,
            activeTvName = device?.name,
            playbackState = playback,
            sessionInfo = CastSessionInfo(),
            requestState = if (playback == CastPlaybackState.PLAYING) CastRequestState.SENT else request
        )
    }.combine(sessionInfo) { state, session -> state.copy(sessionInfo = session) }.stateIn(
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

    fun requestMediaPicker() {
        viewModelScope.launch {
            val gate = checkAccess(mediaType = null)
            _uiEvents.emit(gate ?: CastUiEvent.OpenPicker)
        }
    }

    fun onMediaPicked(uri: Uri) {
        viewModelScope.launch {
            val prepared = mediaRepository.prepareMedia(uri)
            val item = prepared.getOrElse { error ->
                emitMediaError(error)
                return@launch
            }
            val mediaType = item.toCastMediaTypeOrNull()
            if (mediaType == null) {
                _uiEvents.emit(CastUiEvent.UnsupportedFormat)
                return@launch
            }
            val gate = checkAccess(mediaType)
            if (gate != null) {
                _uiEvents.emit(gate)
                return@launch
            }
            mediaRepository.selectMedia(item)
            _selectedMediaIds.value = setOf(item.id)
        }
    }

    fun startCasting() {
        val selectedIds = _selectedMediaIds.value
        if (selectedIds.isNotEmpty()) {
            viewModelScope.launch {
                val mediaList = mediaRepository.getLocalMedia().first()
                val targetMedia = mediaList.firstOrNull { selectedIds.contains(it.id) }
                if (targetMedia != null) {
                    val mediaType = targetMedia.toCastMediaTypeOrNull()
                    if (mediaType == null) {
                        _uiEvents.emit(CastUiEvent.UnsupportedFormat)
                        _selectedMediaIds.value = emptySet()
                        return@launch
                    }
                    val gate = checkAccess(mediaType)
                    if (gate != null) {
                        _uiEvents.emit(gate)
                        return@launch
                    }
                    val readable = mediaRepository.verifyReadable(targetMedia)
                    if (readable.isFailure) {
                        emitMediaError(readable.exceptionOrNull())
                        _selectedMediaIds.value = emptySet()
                        return@launch
                    }
                    _isCasting.value = true
                    _requestState.value = CastRequestState.PREPARING
                    val result = castMediaUseCase(targetMedia)
                    if (result.isFailure) {
                        _isCasting.value = false
                        _requestState.value = CastRequestState.ERROR
                        val error = result.exceptionOrNull()
                        _uiEvents.emit(CastUiEvent.CastFailed(
                            (error as? CastError ?: CastError.ProtocolCommandFailed(error?.message ?: "Cast failed")).toPresentation()
                        ))
                    } else {
                        _requestState.value = CastRequestState.SENT
                    }
                    _selectedMediaIds.value = emptySet()
                }
            }
        }
    }

    /** Gate order is intentionally active TV, connection, capability, then Premium. */
    private suspend fun checkAccess(mediaType: CastMediaType?): CastUiEvent? {
        val controller = activeTvSession.currentController.value ?: return CastUiEvent.NoActiveDevice
        val device = controller.device ?: return CastUiEvent.NoActiveDevice
        if (controller.connectionState.value !is TvConnectionState.Connected) return CastUiEvent.NotConnected
        val hasCapability = when (mediaType) {
            CastMediaType.PHOTO -> TvCapability.CAST_IMAGE in device.capabilities
            CastMediaType.VIDEO -> TvCapability.CAST_VIDEO in device.capabilities
            CastMediaType.AUDIO -> false
            null -> TvCapability.CAST_IMAGE in device.capabilities || TvCapability.CAST_VIDEO in device.capabilities
        }
        if (!hasCapability) {
            return if (mediaType == null) CastUiEvent.NoVisualCapability else CastUiEvent.CapabilityMissing
        }
        val plan = subscriptionRepository.currentPlan.first()
        if (!featureGate.isFeatureAllowed(PremiumFeature.CAST, plan)) return CastUiEvent.PremiumRequired
        return null
    }

    private fun MediaItem.toCastMediaTypeOrNull(): CastMediaType? = when {
        mimeType.startsWith("image/") -> CastMediaType.PHOTO
        mimeType.startsWith("video/") -> CastMediaType.VIDEO
        else -> null
    }

    private fun emitMediaError(error: Throwable?) {
        if (error is CastError) {
            _requestState.value = CastRequestState.ERROR
            _uiEvents.tryEmit(CastUiEvent.CastFailed(error.toPresentation()))
            return
        }
        _uiEvents.tryEmit(
            when (error) {
                is CastError.UnsupportedMediaFormat -> CastUiEvent.UnsupportedFormat
                is CastError.MediaSourceUnavailable -> CastUiEvent.MediaUnavailable
                else -> CastUiEvent.MediaUnavailable
            }
        )
    }

    fun stopCasting() {
        viewModelScope.launch {
            val controller = activeTvSession.currentController.value
            castMediaUseCase.teardown()
            mediaRepository.clearSelectedMedia()
            _selectedMediaIds.value = emptySet()
            _isCasting.value = false
            _requestState.value = CastRequestState.IDLE
        }
    }

    override fun onCleared() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            castMediaUseCase.teardown()
            mediaRepository.clearSelectedMedia()
        }
        super.onCleared()
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
