package com.example.smartclick.app.domain.model

class SwipeToDpadMapper(
    var thresholdPx: Float = 150f,
    var cooldownMs: Long = 150L
) {
    var fallbackDx: Float = 0f
        private set
    var fallbackDy: Float = 0f
        private set
        
    private var lastSwipeTime: Long = 0L

    fun onMove(dx: Float, dy: Float, currentTimeMs: Long): RemoteCommand? {
        fallbackDx += dx
        fallbackDy += dy
        
        if (currentTimeMs - lastSwipeTime > cooldownMs) {
            var command: RemoteCommand? = null
            
            if (kotlin.math.abs(fallbackDx) > kotlin.math.abs(fallbackDy)) {
                if (fallbackDx > thresholdPx) {
                    command = RemoteCommand.DPadRight
                } else if (fallbackDx < -thresholdPx) {
                    command = RemoteCommand.DPadLeft
                }
            } else {
                if (fallbackDy > thresholdPx) {
                    command = RemoteCommand.DPadDown
                } else if (fallbackDy < -thresholdPx) {
                    command = RemoteCommand.DPadUp
                }
            }

            if (command != null) {
                fallbackDx = 0f
                fallbackDy = 0f
                lastSwipeTime = currentTimeMs
                return command
            }
        }
        return null
    }

    fun onDragEnd() {
        fallbackDx = 0f
        fallbackDy = 0f
    }
}
