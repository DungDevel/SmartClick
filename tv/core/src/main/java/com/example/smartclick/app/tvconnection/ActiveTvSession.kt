package com.example.smartclick.app.tvconnection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveTvSession @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope
) : IActiveTvSession {
    private val _currentController = MutableStateFlow<ITvController?>(null)
    override val currentController: StateFlow<ITvController?> = _currentController.asStateFlow()

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    init {
        scope.launch {
            _currentController.collectLatest { controller ->
                if (controller == null) {
                    _connectionState.value = TvConnectionState.Idle
                } else {
                    controller.connectionState.collectLatest { state ->
                        _connectionState.value = state
                    }
                }
            }
        }
    }

    override fun setActiveController(controller: ITvController?) {
        _currentController.value = controller
    }
    
    override fun setConnectionState(state: TvConnectionState) {
        _connectionState.value = state
    }
}
