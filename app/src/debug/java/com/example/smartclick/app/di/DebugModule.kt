package com.example.smartclick.app.di

import com.example.smartclick.app.tvconnection.FakeTvController
import com.example.smartclick.app.domain.connection.ITvController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugModule {
    @Binds
    abstract fun bindFakeController(impl: FakeTvController): ITvController
}

