package com.example.smartclick.app.di

import com.example.smartclick.app.data.local.DataStoreSettingsRepository
import com.example.smartclick.app.data.local.RoomTvDeviceRepository
import com.example.smartclick.app.data.repository.ChannelRegistryRepository
import com.example.smartclick.app.domain.repository.IChannelRegistryRepository
import com.example.smartclick.app.domain.repository.ISettingsRepository
import com.example.smartclick.app.domain.repository.ITvDeviceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindActiveTvSession(
        impl: com.example.smartclick.app.tvconnection.ActiveTvSession
    ): com.example.smartclick.app.domain.connection.IActiveTvSession

    @Binds
    abstract fun bindWifiNetworkProvider(
        impl: com.example.smartclick.app.tvconnection.wifi.WifiNetworkProvider
    ): com.example.smartclick.app.domain.connection.IWifiNetworkProvider

    @Binds
    abstract fun bindTvDeviceRepository(
        impl: RoomTvDeviceRepository
    ): ITvDeviceRepository

    @Binds
    abstract fun bindSettingsRepository(
        impl: DataStoreSettingsRepository
    ): ISettingsRepository

    @Binds
    abstract fun bindChannelRegistryRepository(
        impl: ChannelRegistryRepository
    ): IChannelRegistryRepository

    @Binds
    abstract fun bindBrandProfileRepository(
        impl: com.example.smartclick.app.data.brandprofile.AssetBrandProfileRepository
    ): com.example.smartclick.app.domain.repository.IBrandProfileRepository

    @Binds
    abstract fun bindDiscoveryOrchestrator(
        impl: com.example.smartclick.app.tvconnection.discovery.DiscoveryOrchestrator
    ): com.example.smartclick.app.domain.connection.IDiscoveryOrchestrator
}
