package com.example.smartclick.app.features.remote

import com.example.smartclick.app.domain.model.RemoteCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class TouchpadPointerMoveDispatcher(
    private val scope: CoroutineScope,
    private val windowMs: Long = 16L,
    private val send: (RemoteCommand.PointerMove) -> Unit,
    private val haptic: () -> Unit
) {
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var flushJob: Job? = null
    private var hapticSentForGesture = false

    fun onMove(dx: Float, dy: Float) {
        if (!hapticSentForGesture) {
            hapticSentForGesture = true
            haptic()
        }

        pendingDx += dx
        pendingDy += dy
        if (flushJob == null) {
            lateinit var scheduledJob: Job
            scheduledJob = scope.launch {
                try {
                    delay(windowMs)
                    flushPending()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                } finally {
                    if (flushJob === currentCoroutineContext()[Job]) flushJob = null
                }
            }
            flushJob = scheduledJob
        }
    }

    fun onDragEnd() {
        flushJob?.cancel()
        flushJob = null
        hapticSentForGesture = false
        try {
            flushPending()
        } catch (_: Exception) {
        }
    }

    private fun flushPending() {
        if (pendingDx == 0f && pendingDy == 0f) return
        val movement = RemoteCommand.PointerMove(pendingDx, pendingDy)
        pendingDx = 0f
        pendingDy = 0f
        send(movement)
    }
}
