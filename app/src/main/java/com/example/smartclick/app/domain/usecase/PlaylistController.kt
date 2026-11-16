package com.example.smartclick.app.domain.usecase

import android.util.Log
import com.example.smartclick.app.domain.cast.CastCommand
import com.example.smartclick.app.domain.cast.CastPlaybackState
import com.example.smartclick.app.domain.connection.ICastController
import com.example.smartclick.app.domain.model.CastPlaylist
import com.example.smartclick.app.domain.model.MediaItem
import com.example.smartclick.app.domain.model.PlaylistStatus
import com.example.smartclick.app.di.IoDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class PlaylistController(
    private val castMediaUseCase: CastMediaUseCase,
    private val defaultDispatcher: CoroutineDispatcher,
    val startTimeoutMs: Long = DEFAULT_START_TIMEOUT_MS,
    val completionTimeoutMs: Long = DEFAULT_COMPLETION_TIMEOUT_MS
) {
    @Inject
    constructor(
        castMediaUseCase: CastMediaUseCase,
        @IoDispatcher defaultDispatcher: CoroutineDispatcher
    ) : this(
        castMediaUseCase,
        defaultDispatcher,
        DEFAULT_START_TIMEOUT_MS,
        DEFAULT_COMPLETION_TIMEOUT_MS
    )

    private companion object {
        const val DEFAULT_START_TIMEOUT_MS = 15_000L
        const val DEFAULT_COMPLETION_TIMEOUT_MS = 2 * 60 * 60 * 1000L
    }

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

    /** Logging must never affect playback flow (android.util.Log is not mocked in JVM unit tests). */
    private fun logWarn(message: String) {
        runCatching { Log.w("PlaylistController", message) }
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
                val controller = castMediaUseCase.activeCastController.value
                if (controller == null) {
                    handleItemError("No active cast controller")
                    return@launch
                }

                val initialState = controller.castPlaybackState.value

                // Phase 1: wait for PLAYING or BUFFERING (startTimeoutMs).
                // ERROR, COMPLETED-before-playing and timeout are item errors.
                val started: CastPlaybackState? = try {
                    withTimeoutOrNull(startTimeoutMs) {
                        controller.castPlaybackState
                            .dropWhile {
                                it == initialState &&
                                    (it == CastPlaybackState.COMPLETED || it == CastPlaybackState.ERROR)
                            }
                            .first {
                                it == CastPlaybackState.PLAYING ||
                                    it == CastPlaybackState.BUFFERING ||
                                    it == CastPlaybackState.ERROR ||
                                    it == CastPlaybackState.COMPLETED
                            }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleItemError(e.message ?: "Video start error")
                    return@launch
                }

                if (started == null || started == CastPlaybackState.ERROR) {
                    handleItemError("Video failed to start playing")
                    return@launch
                }
                if (started == CastPlaybackState.COMPLETED) {
                    logWarn("Video completed before playing")
                    handleItemError("Video completed before playing")
                    return@launch
                }

                // Phase 2: wait for video completion (completionTimeoutMs).
                // IDLE/COMPLETED -> next(). ERROR -> handleItemError.
                // Timeout -> log a warning and advance via next() WITHOUT incrementing
                // the consecutive error count.
                val ended: CastPlaybackState? = try {
                    withTimeoutOrNull(completionTimeoutMs) {
                        controller.castPlaybackState.first {
                            it == CastPlaybackState.IDLE ||
                                it == CastPlaybackState.COMPLETED ||
                                it == CastPlaybackState.ERROR
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleItemError(e.message ?: "Video completion error")
                    return@launch
                }

                if (ended == null) {
                    logWarn("Video playback timed out after ${completionTimeoutMs}ms; advancing playlist")
                    if (isActive) next()
                    return@launch
                }
                if (ended == CastPlaybackState.ERROR) {
                    handleItemError("Video playback error state")
                    return@launch
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
