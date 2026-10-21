package com.example.smartclick.app.di

import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.tvconnection.androidtv.AndroidTvDriver
import com.example.smartclick.app.tvconnection.samsung.SamsungTizenDriver
import com.example.smartclick.app.tvconnection.wifi.RokuDriver
import com.example.smartclick.app.tvconnection.lg.LgWebOsDriver
import com.example.smartclick.app.tvconnection.sony.SonyBraviaDriver
import com.example.smartclick.app.tvconnection.vizio.VizioDriver
import com.example.smartclick.app.tvconnection.firetv.FireTvDriver
import com.example.smartclick.app.tvconnection.vidaa.VidaaDriver
import com.example.smartclick.app.tvconnection.dial.DialDriver
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
        lgWebOsDriverProvider: Provider<LgWebOsDriver>,
        sonyBraviaDriverProvider: Provider<SonyBraviaDriver>,
        vizioDriverProvider: Provider<VizioDriver>,
        fireTvDriverProvider: Provider<FireTvDriver>,
        vidaaDriverProvider: Provider<VidaaDriver>,
        dialDriverProvider: Provider<DialDriver>
    ): Set<IProtocolDriver> {
        return setOf(
            rokuDriverProvider.get(),
            androidTvDriverProvider.get(),
            samsungTizenDriverProvider.get(),
            lgWebOsDriverProvider.get(),
            sonyBraviaDriverProvider.get(),
            vizioDriverProvider.get(),
            fireTvDriverProvider.get(),
            vidaaDriverProvider.get(),
            dialDriverProvider.get()
        )
    }
}
