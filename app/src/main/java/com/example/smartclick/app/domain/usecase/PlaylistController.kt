package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.model.CastMediaType
import com.example.smartclick.app.domain.model.CastPlaylist
import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.model.PlaylistStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

class PlaylistController @Inject constructor(
    private val castMediaUseCase: CastMediaUseCase,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _playlistState = MutableStateFlow(CastPlaylist())
    val playlistState: StateFlow<CastPlaylist> = _playlistState.asStateFlow()

    private var playbackJob: Job? = null
    private var scope: CoroutineScope? = null

    fun bindScope(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    fun start(items: List<MediaItem>, imageDurationSeconds: Int = 5, loop: Boolean = false) {
        if (items.isEmpty()) return
        playbackJob?.cancel()
        _playlistState.value = CastPlaylist(
            items = items,
            currentIndex = 0,
            status = PlaylistStatus.PREPARING,
            imageDurationSeconds = imageDurationSeconds,
            loop = loop,
            errorCount = 0
        )
        playCurrentItem()
    }

    fun pause() {
        val current = _playlistState.value
        if (current.status == PlaylistStatus.PLAYING) {
            playbackJob?.cancel()
            _playlistState.value = current.copy(status = PlaylistStatus.PAUSED)
            scope?.launch(defaultDispatcher) {
                castMediaUseCase.activeCastController.value?.controlCast(CastCommand.Pause)
            }
        }
    }

    fun resume() {
        val current = _playlistState.value
        if (current.status == PlaylistStatus.PAUSED) {
            _playlistState.value = current.copy(status = PlaylistStatus.PLAYING)
            scope?.launch(defaultDispatcher) {
                castMediaUseCase.activeCastController.value?.controlCast(CastCommand.Play)
            }
            playCurrentItem(resume = true)
        }
    }

    fun next() {
        val current = _playlistState.value
        if (current.currentIndex < current.items.size - 1) {
            moveToIndex(current.currentIndex + 1)
        } else if (current.loop && current.items.isNotEmpty()) {
            moveToIndex(0)
        } else {
            stop()
            _playlistState.value = current.copy(status = PlaylistStatus.FINISHED)
        }
    }

    fun prev() {
        val current = _playlistState.value
        if (current.currentIndex > 0) {
            moveToIndex(current.currentIndex - 1)
        }
    }

    fun moveToIndex(index: Int) {
        val current = _playlistState.value
        if (index in current.items.indices) {
            playbackJob?.cancel()
            _playlistState.value = current.copy(currentIndex = index, status = PlaylistStatus.PREPARING)
            playCurrentItem()
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        scope?.launch(defaultDispatcher) {
            castMediaUseCase.teardown()
        }
        _playlistState.value = CastPlaylist()
    }

    fun setImageDuration(seconds: Int) {
        _playlistState.value = _playlistState.value.copy(imageDurationSeconds = seconds)
    }

    fun setLoop(loop: Boolean) {
        _playlistState.value = _playlistState.value.copy(loop = loop)
    }

    private fun playCurrentItem(resume: Boolean = false) {
        val scope = scope ?: return
        playbackJob?.cancel()
        playbackJob = scope.launch(defaultDispatcher) {
            val state = _playlistState.value
            val item = state.currentItem ?: return@launch
            if (!resume) {
                val result = castMediaUseCase(item)
                if (result.isFailure) {
                    handleItemError(result.exceptionOrNull()?.message ?: "Cast failed")
                    return@launch
                }
            }

            _playlistState.value = _playlistState.value.copy(status = PlaylistStatus.PLAYING, errorMessage = null)

            val isVideo = item.mimeType.startsWith("video/")
            if (isVideo) {
                // Wait for video completion signal from controller or timeout safety
                val controller = castMediaUseCase.activeCastController.value
                val startTime = System.currentTimeMillis()
                val timeoutMs = 2 * 60 * 60 * 1000L // 2 hours max safety timeout
                while (isActive) {
                    val playbackState = controller?.castPlaybackState?.value ?: CastPlaybackState.IDLE
                    if (playbackState == CastPlaybackState.IDLE || playbackState == CastPlaybackState.ERROR) {
                        break
                    }
                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        break
                    }
                    delay(1000L)
                }
                if (isActive) {
                    next()
                }
            } else {
                // Photo: wait for imageDurationSeconds
                delay(state.imageDurationSeconds * 1000L)
                if (isActive) {
                    next()
                }
            }
        }
    }

    private fun handleItemError(errorMsg: String) {
        val current = _playlistState.value
        val newErrors = current.errorCount + 1
        if (newErrors >= 3 || current.currentIndex >= current.items.size - 1) {
            _playlistState.value = current.copy(
                status = PlaylistStatus.ERROR,
                errorCount = newErrors,
                errorMessage = errorMsg
            )
        } else {
            // Skip to next item
            _playlistState.value = current.copy(errorCount = newErrors, errorMessage = errorMsg)
            next()
        }
    }
}
