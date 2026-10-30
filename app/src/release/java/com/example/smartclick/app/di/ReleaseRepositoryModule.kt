package com.example.smartclick.app.di

import com.example.smartclick.app.data.noop.*
import com.example.smartclick.app.data.repository.PlayBillingRepository
import com.example.smartclick.app.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReleaseRepositoryModule {
    @Provides
    @Singleton
    fun provideAuthRepository(): IAuthRepository = NoOpAuthRepository()

    @Provides
    @Singleton
    fun provideSubscriptionRepository(playBilling: PlayBillingRepository): ISubscriptionRepository = playBilling

    @Provides
    @Singleton
    fun provideMediaRepository(): IMediaRepository = NoOpMediaRepository()
}
