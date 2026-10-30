package com.example.smartclick.app.di

import com.example.smartclick.app.data.fake.FakeAuthRepository
import com.example.smartclick.app.data.fake.FakeSubscriptionRepository
import com.example.smartclick.app.data.fake.FakeMediaRepository
import com.example.smartclick.app.domain.repository.IAuthRepository
import com.example.smartclick.app.domain.repository.IMediaRepository
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FakeRepositoryModule {
    @Provides
    @Singleton
    fun provideAuthRepository(): IAuthRepository = FakeAuthRepository()

    @Provides
    @Singleton
    fun provideSubscriptionRepository(): ISubscriptionRepository = FakeSubscriptionRepository()

    @Provides
    @Singleton
    fun provideMediaRepository(): IMediaRepository = FakeMediaRepository()
}
