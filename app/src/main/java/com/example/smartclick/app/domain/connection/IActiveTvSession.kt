package com.example.smartclick.app.domain.connection

import kotlinx.coroutines.flow.StateFlow

interface IActiveTvSession {
    val currentController: StateFlow<ITvController?>
    val connectionState: StateFlow<TvConnectionState>
    fun setActiveController(controller: ITvController?)
    fun setConnectionState(state: TvConnectionState)
}
