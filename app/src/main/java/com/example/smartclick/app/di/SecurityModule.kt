package com.example.smartclick.app.di

import com.example.smartclick.app.domain.connection.ITvCredentialStore
import com.example.smartclick.app.tvconnection.security.TvCredentialStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    abstract fun bindTvCredentialStore(
        impl: TvCredentialStore
    ): ITvCredentialStore
}
