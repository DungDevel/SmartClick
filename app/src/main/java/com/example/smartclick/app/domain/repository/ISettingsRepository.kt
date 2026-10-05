package com.example.smartclick.app.domain.repository

import com.example.smartclick.app.domain.model.AppSettings
import com.example.smartclick.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ISettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setHapticEnabled(enabled: Boolean)
    suspend fun setLanguageTag(tag: String?)
    suspend fun setOnboardingCompleted(done: Boolean)
    suspend fun setRemoteMode(mode: String)
}
