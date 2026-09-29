package com.example.smartclick.app.di

import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.tvconnection.androidtv.AndroidTvDriver
import com.example.smartclick.app.tvconnection.samsung.SamsungTizenDriver
import com.example.smartclick.app.tvconnection.wifi.RokuDriver
import com.example.smartclick.app.tvconnection.lg.LgWebOsDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Provider

@Module
@InstallIn(SingletonComponent::class)
object TvDriverModule {

    // FW-9: Multibinding cho các driver.
    @Provides
    @ElementsIntoSet
    fun provideProtocolDrivers(
        rokuDriverProvider: Provider<RokuDriver>,
        androidTvDriverProvider: Provider<AndroidTvDriver>,
        samsungTizenDriverProvider: Provider<SamsungTizenDriver>,
        lgWebOsDriverProvider: Provider<LgWebOsDriver>
    ): Set<IProtocolDriver> {
        return setOf(
            rokuDriverProvider.get(),
            androidTvDriverProvider.get(),
            samsungTizenDriverProvider.get(),
            lgWebOsDriverProvider.get()
        )
    }
}
