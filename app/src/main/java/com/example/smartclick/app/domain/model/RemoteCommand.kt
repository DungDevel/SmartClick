package com.example.smartclick.app.domain.model

sealed class RemoteCommand {
    object Power : RemoteCommand()
    object VolumeUp : RemoteCommand()
    object VolumeDown : RemoteCommand()
    object Mute : RemoteCommand()
    object DPadUp : RemoteCommand()
    object DPadDown : RemoteCommand()
    object DPadLeft : RemoteCommand()
    object DPadRight : RemoteCommand()
    object Ok : RemoteCommand()
    object Back : RemoteCommand()
    object Home : RemoteCommand()
    object Menu : RemoteCommand()
    object Input : RemoteCommand()

    // Media controls
    object PlayPause : RemoteCommand()
    object Next : RemoteCommand()
    object Previous : RemoteCommand()
    object FastForward : RemoteCommand()
    object Rewind : RemoteCommand()

    // Channel controls
    object ChannelUp : RemoteCommand()
    object ChannelDown : RemoteCommand()

    // Search
    data class SearchContent(val query: String) : RemoteCommand()
    object OpenSearchUi : RemoteCommand()

    data class Numpad(val digit: Int) : RemoteCommand()
    data class Text(val text: String) : RemoteCommand()
    object Backspace : RemoteCommand()

    // Touchpad/Pointer
    data class PointerMove(val dx: Float, val dy: Float) : RemoteCommand()
    object PointerClick : RemoteCommand()
}
