package com.example.smartclick.app.domain.cast

sealed interface CastCommand {
    object Play : CastCommand
    object Pause : CastCommand
    object Stop : CastCommand
    data class SeekTo(val positionMs: Long) : CastCommand
    data class SeekBy(val offsetMs: Long) : CastCommand
    data class SetVolume(val volume: Int) : CastCommand
    object Mute : CastCommand
    object Unmute : CastCommand
}
