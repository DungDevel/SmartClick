package com.example.smartclick.app.di

import com.example.smartclick.app.data.noop.NoOpAuthRepository
import com.example.smartclick.app.data.repository.InMemoryMediaRepository
import com.example.smartclick.app.data.repository.AndroidMediaContentReader
import com.example.smartclick.app.data.repository.MediaContentReader
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
    fun provideMediaRepository(repository: InMemoryMediaRepository): IMediaRepository = repository

    @Provides
    @Singleton
    fun provideMediaContentReader(reader: AndroidMediaContentReader): MediaContentReader = reader
}
