package com.example.smartclick.app.di

import com.example.smartclick.app.domain.voice.AndroidSpeechToText
import com.example.smartclick.app.domain.voice.SpeechToTextEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    abstract fun bindSpeechToTextEngine(
        impl: AndroidSpeechToText
    ): SpeechToTextEngine
}
