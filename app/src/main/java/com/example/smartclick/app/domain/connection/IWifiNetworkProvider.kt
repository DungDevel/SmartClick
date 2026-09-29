package com.example.smartclick.app.domain.connection

import kotlinx.coroutines.flow.Flow

import android.net.Network

interface IWifiNetworkProvider {
    fun getActiveWifiNetwork(): Network?
    val networkState: Flow<NetworkState>
    fun getCurrentState(): NetworkState
}

