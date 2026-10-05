package com.example.smartclick.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.smartclick.app.domain.model.AppSettings
import com.example.smartclick.app.domain.model.ThemeMode
import com.example.smartclick.app.domain.repository.ISettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ISettingsRepository {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val REMOTE_MODE = stringPreferencesKey("remote_mode")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeModeString = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name
            val themeMode = try {
                ThemeMode.valueOf(themeModeString)
            } catch (e: Exception) {
                ThemeMode.DARK
            }

            AppSettings(
                themeMode = themeMode,
                hapticEnabled = preferences[PreferencesKeys.HAPTIC_ENABLED] ?: true,
                languageTag = preferences[PreferencesKeys.LANGUAGE_TAG],
                onboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
                remoteMode = preferences[PreferencesKeys.REMOTE_MODE] ?: "DPAD"
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    override suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_ENABLED] = enabled
        }
    }

    override suspend fun setLanguageTag(tag: String?) {
        context.dataStore.edit { preferences ->
            if (tag == null) {
                preferences.remove(PreferencesKeys.LANGUAGE_TAG)
            } else {
                preferences[PreferencesKeys.LANGUAGE_TAG] = tag
            }
        }
    }

    override suspend fun setOnboardingCompleted(done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = done
        }
    }

    override suspend fun setRemoteMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMOTE_MODE] = mode
        }
    }
}
